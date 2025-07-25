/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.plugins.views.startpage.recentActivities;

import com.google.common.eventbus.EventBus;
import com.mongodb.BasicDBObject;
import jakarta.inject.Singleton;
import org.graylog2.database.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import jakarta.inject.Inject;
import org.graylog.grn.GRN;
import org.graylog.grn.GRNRegistry;
import org.graylog.grn.GRNType;
import org.graylog.grn.GRNTypes;
import org.graylog.plugins.views.search.permissions.SearchUser;
import org.graylog.security.PermissionAndRoleResolver;
import org.graylog2.database.MongoCollections;
import org.graylog2.database.MongoConnection;
import org.graylog2.database.PaginatedList;
import org.graylog2.database.pagination.MongoPaginationHelper;
import org.graylog2.plugin.database.users.User;
import org.graylog2.rest.models.SortOrder;

import java.util.HashSet;

@Singleton
public class RecentActivityService {
    public static final String COLLECTION_NAME = "recent_activity";
    private final EventBus eventBus;
    private final GRNRegistry grnRegistry;
    private final PermissionAndRoleResolver permissionAndRoleResolver;

    private static final long MAXIMUM_RECENT_ACTIVITIES = 10000;
    private final MongoCollection<RecentActivityDTO> db;
    private final MongoPaginationHelper<RecentActivityDTO> pagination;

    @Inject
    public RecentActivityService(final MongoCollections mongoCollections,
                                 final MongoConnection mongoConnection,
                                 final EventBus eventBus,
                                 final GRNRegistry grnRegistry,
                                 final PermissionAndRoleResolver permissionAndRoleResolver) {
        this(mongoCollections, mongoConnection, eventBus, grnRegistry, permissionAndRoleResolver, MAXIMUM_RECENT_ACTIVITIES);
    }

    /*
     * Constructor to set a low maximum in tests to check the capped collection.
     */
    protected RecentActivityService(final MongoCollections mongoCollections,
                                    final MongoConnection mongoConnection,
                                    final EventBus eventBus,
                                    final GRNRegistry grnRegistry,
                                    final PermissionAndRoleResolver permissionAndRoleResolver,
                                    final long maximum) {
        final var mongodb = mongoConnection.getMongoDatabase();
        if (!mongodb.listCollectionNames().into(new HashSet<>()).contains(COLLECTION_NAME)) {
            mongodb.createCollection(COLLECTION_NAME, new CreateCollectionOptions().capped(true).sizeInBytes(maximum * 1024).maxDocuments(maximum));
        }
        this.db = mongoCollections.collection(COLLECTION_NAME, RecentActivityDTO.class);
        this.grnRegistry = grnRegistry;
        this.permissionAndRoleResolver = permissionAndRoleResolver;
        this.eventBus = eventBus;
        this.pagination = mongoCollections.paginationHelper(this.db);

        db.createIndex(new BasicDBObject(RecentActivityDTO.FIELD_ITEM_GRN, 1));
    }

    private void postRecentActivity(final RecentActivityEvent event) {
        eventBus.post(event);
    }

    public void create(String id, GRNType grn, SearchUser user) {
        create(id, grn, user.getUser());
    }

    public void create(String id, GRNType grn, User user) {
        postRecentActivity(new RecentActivityEvent(ActivityType.CREATE, grnRegistry.newGRN(grn, id), user.getFullName()));
    }

    public void update(String id, GRNType grn, SearchUser user) {
        update(id, grn, user.getUser());
    }

    public void update(String id, GRNType grn, User user) {
        postRecentActivity(new RecentActivityEvent(ActivityType.UPDATE, grnRegistry.newGRN(grn, id), user.getFullName()));
    }

    public void delete(String id, GRNType grn, String title, SearchUser user) {
        delete(id, grn, title, user.getUser());
    }

    public void delete(String id, GRNType grn, String title, User user) {
        postRecentActivity(new RecentActivityEvent(ActivityType.DELETE, grnRegistry.newGRN(grn, id), title, user.getFullName()));
    }

    public PaginatedList<RecentActivityDTO> findRecentActivitiesFor(SearchUser user, int page, int perPage) {
        // show the most recent activities first
        var sort = SortOrder.DESCENDING.toBsonSort(RecentActivityDTO.FIELD_TIMESTAMP);
        // no permission check for local admin
        if (user.getUser().isLocalAdmin()) {
            return pagination
                    .perPage(perPage)
                    .sort(sort)
                    .includeGrandTotal(true)
                    .page(page);
        }

        // filter relevant activities by permissions
        final var principal = grnRegistry.newGRN(GRNTypes.USER, user.getUser().getId());
        final var grns = permissionAndRoleResolver.resolveGrantees(principal).stream().map(GRN::toString).toList();
        var query = Filters.in(RecentActivityDTO.FIELD_GRANTEE, grns);
        return pagination
                .perPage(perPage)
                .sort(sort)
                .filter(query)
                .includeGrandTotal(true)
                .grandTotalFilter(query)
                .page(page);
    }

    public void deleteAllEntriesForEntity(GRN grn) {
        db.deleteMany(Filters.eq(RecentActivityDTO.FIELD_ITEM_GRN, grn.toString()));
    }

    public void save(RecentActivityDTO activity) {
        db.insertOne(activity);
    }
}
