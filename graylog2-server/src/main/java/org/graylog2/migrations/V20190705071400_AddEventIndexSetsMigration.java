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
package org.graylog2.migrations;

import com.google.common.collect.ImmutableList;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import jakarta.inject.Inject;
import org.bson.conversions.Bson;
import org.graylog.events.notifications.EventNotificationSettings;
import org.graylog.events.processor.DBEventDefinitionService;
import org.graylog.events.processor.EventDefinitionDto;
import org.graylog.events.processor.storage.PersistToStreamsStorageHandler;
import org.graylog.events.processor.systemnotification.SystemNotificationEventProcessorConfig;
import org.graylog2.configuration.ElasticsearchConfiguration;
import org.graylog2.database.NotFoundException;
import org.graylog2.database.entities.NonDeletableSystemScope;
import org.graylog2.database.utils.MongoUtils;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.IndexSetValidator;
import org.graylog2.indexer.MongoIndexSet;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indexset.IndexSetConfigFactory;
import org.graylog2.indexer.indexset.IndexSetService;
import org.graylog2.plugin.database.ValidationException;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.streams.StreamImpl;
import org.graylog2.streams.StreamService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Optional;

import static java.util.Locale.US;
import static java.util.Objects.requireNonNull;
import static org.graylog.events.processor.DBEventDefinitionService.SYSTEM_NOTIFICATION_EVENT_DEFINITION;
import static org.graylog2.indexer.EventIndexTemplateProvider.EVENT_TEMPLATE_TYPE;
import static org.graylog2.indexer.indexset.SimpleIndexSetConfig.FIELD_INDEX_PREFIX;
import static org.graylog2.indexer.indexset.SimpleIndexSetConfig.FIELD_INDEX_TEMPLATE_TYPE;

public class V20190705071400_AddEventIndexSetsMigration extends Migration {
    private static final Logger LOG = LoggerFactory.getLogger(V20190705071400_AddEventIndexSetsMigration.class);

    private final ElasticsearchConfiguration elasticsearchConfiguration;
    private final MongoIndexSet.Factory mongoIndexSetFactory;
    private final IndexSetService indexSetService;
    private final IndexSetValidator indexSetValidator;
    private final StreamService streamService;
    private final IndexSetConfigFactory indexSetConfigFactory;
    private final DBEventDefinitionService dbService;

    @Inject
    public V20190705071400_AddEventIndexSetsMigration(ElasticsearchConfiguration elasticsearchConfiguration,
                                                      IndexSetConfigFactory indexSetConfigFactory,
                                                      MongoIndexSet.Factory mongoIndexSetFactory,
                                                      IndexSetService indexSetService,
                                                      IndexSetValidator indexSetValidator,
                                                      StreamService streamService,
                                                      DBEventDefinitionService dbService) {
        this.elasticsearchConfiguration = elasticsearchConfiguration;
        this.indexSetConfigFactory = indexSetConfigFactory;
        this.mongoIndexSetFactory = mongoIndexSetFactory;
        this.indexSetService = indexSetService;
        this.indexSetValidator = indexSetValidator;
        this.streamService = streamService;
        this.dbService = dbService;
    }

    @Override
    public ZonedDateTime createdAt() {
        return ZonedDateTime.parse("2019-07-05T07:14:00Z");
    }

    @Override
    public void upgrade() {
        ensureEventsStreamAndIndexSet(
                "Events",
                "Stores events created by event definitions.",
                elasticsearchConfiguration.getDefaultEventsIndexPrefix(),
                ElasticsearchConfiguration.DEFAULT_EVENTS_INDEX_PREFIX,
                Stream.DEFAULT_EVENTS_STREAM_ID,
                "All events",
                "Stream containing all events created"
        );
        ensureEventsStreamAndIndexSet(
                "System Events",
                "Stores events created by and related to the system itself.",
                elasticsearchConfiguration.getDefaultSystemEventsIndexPrefix(),
                ElasticsearchConfiguration.DEFAULT_SYSTEM_EVENTS_INDEX_PREFIX,
                Stream.DEFAULT_SYSTEM_EVENTS_STREAM_ID,
                "All system events",
                "Stream containing all system events created"
        );
        ensureSystemNotificationEventsDefinition();
    }

    private void ensureEventsStreamAndIndexSet(String indexSetTitle,
                                               String indexSetDescription,
                                               String indexPrefix,
                                               String indexPrefixConfigKey,
                                               String streamId,
                                               String streamTitle,
                                               String streamDescription) {
        checkIndexPrefixConflicts(indexPrefix, indexPrefixConfigKey);

        final IndexSet eventsIndexSet = setupEventsIndexSet(indexSetTitle, indexSetDescription, indexPrefix);
        try {
            streamService.load(streamId);
        } catch (NotFoundException ignored) {
            createEventsStream(streamId, streamTitle, streamDescription, eventsIndexSet);
        }
    }

    private void checkIndexPrefixConflicts(String indexPrefix, String configKey) {
        final Bson query = Filters.and(
                Filters.ne(FIELD_INDEX_TEMPLATE_TYPE, Optional.of(EVENT_TEMPLATE_TYPE)),
                Filters.eq(FIELD_INDEX_PREFIX, indexPrefix)
        );

        if (indexSetService.findOne(query).isPresent()) {
            final String msg = String.format(US, "Index prefix conflict: a non-events index-set with prefix <%s> already exists. Configure a different <%s> value in the server config file.",
                    indexPrefix, configKey);
            throw new IllegalStateException(msg);
        }
    }

    private Optional<IndexSetConfig> getEventsIndexSetConfig(String indexPrefix) {
        final Bson query = Filters.and(
                Filters.eq(FIELD_INDEX_TEMPLATE_TYPE, Optional.of(EVENT_TEMPLATE_TYPE)),
                Filters.eq(FIELD_INDEX_PREFIX, indexPrefix)
        );
        return indexSetService.findOne(query);
    }

    private IndexSet setupEventsIndexSet(String indexSetTitle, String indexSetDescription, String indexPrefix) {
        final Optional<IndexSetConfig> optionalIndexSetConfig = getEventsIndexSetConfig(indexPrefix);
        if (optionalIndexSetConfig.isPresent()) {
            return mongoIndexSetFactory.create(optionalIndexSetConfig.get());
        }

        final IndexSetConfig indexSetConfig = indexSetConfigFactory.createDefault()
                .title(indexSetTitle)
                .description(indexSetDescription)
                .indexTemplateType(EVENT_TEMPLATE_TYPE)
                .isWritable(true)
                .isRegular(false)
                .indexPrefix(indexPrefix)
                .indexTemplateName(indexPrefix + "-template")
                .build();

        try {
            final Optional<IndexSetValidator.Violation> violation = indexSetValidator.validate(indexSetConfig);
            if (violation.isPresent()) {
                throw new RuntimeException(violation.get().message());
            }

            final IndexSetConfig savedIndexSet = indexSetService.save(indexSetConfig);

            LOG.info("Successfully created events index-set <{}/{}>", savedIndexSet.id(), savedIndexSet.title());

            return mongoIndexSetFactory.create(savedIndexSet);
        } catch (MongoException e) {
            if (MongoUtils.isDuplicateKeyError(e)) {
                LOG.error("Couldn't create index-set <{}/{}>", indexSetTitle, indexPrefix);
                throw new RuntimeException(e.getMessage());
            }
            throw e;
        }
    }

    private void createEventsStream(String streamId, String streamTitle, String streamDescription, IndexSet indexSet) {
        final Stream stream = StreamImpl.builder()
                .id(streamId)
                .title(streamTitle)
                .description(streamDescription)
                .disabled(false)
                .createdAt(DateTime.now(DateTimeZone.UTC))
                .creatorUserId("admin")
                .matchingType(StreamImpl.MatchingType.DEFAULT)
                .removeMatchesFromDefaultStream(true)
                .indexSetId(requireNonNull(indexSet.getConfig().id(), "index set ID cannot be null"))
                .isDefault(false)
                .build();

        try {
            streamService.save(stream);
            LOG.info("Successfully created events stream <{}/{}>", stream.getId(), stream.getTitle());
        } catch (ValidationException e) {
            LOG.error("Couldn't create events stream <{}/{}>! This is a bug!", streamId, streamTitle, e);
        }
    }

    private void ensureSystemNotificationEventsDefinition() {
        try (java.util.stream.Stream<EventDefinitionDto> eventDefinitionStream = dbService.streamSystemEventDefinitions()) {
            if (eventDefinitionStream.findAny().isEmpty()) {
                EventDefinitionDto eventDto =
                        EventDefinitionDto.builder()
                                .title(SYSTEM_NOTIFICATION_EVENT_DEFINITION)
                                .description("Reserved event definition for system notification events")
                                .alert(false)
                                .priority(1)
                                .keySpec(ImmutableList.of())
                                .notificationSettings(EventNotificationSettings.builder()
                                        .gracePeriodMs(0) // Defaults to 0 in the UI
                                        .backlogSize(0) // Defaults to 0 in the UI
                                        .build())
                                .config(SystemNotificationEventProcessorConfig.builder().build())
                                .storage(ImmutableList.of(PersistToStreamsStorageHandler.Config.createWithSystemEventsStream()))
                                .scope(NonDeletableSystemScope.NAME)
                                .build();
                dbService.save(eventDto);
            }
        }
    }
}
