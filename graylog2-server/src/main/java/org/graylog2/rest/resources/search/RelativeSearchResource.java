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
package org.graylog2.rest.resources.search;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.glassfish.jersey.server.ChunkedOutput;
import org.graylog.plugins.views.search.engine.SearchExecutor;
import org.graylog.plugins.views.search.permissions.SearchUser;
import org.graylog.plugins.views.search.searchtypes.Sort;
import org.graylog2.decorators.DecoratorProcessor;
import org.graylog2.indexer.results.ChunkedResult;
import org.graylog2.indexer.results.ResultChunk;
import org.graylog2.indexer.searches.Searches;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.indexer.searches.timeranges.TimeRange;
import org.graylog2.rest.MoreMediaTypes;
import org.graylog2.rest.resources.search.responses.SearchResponse;
import org.graylog2.shared.security.RestPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.graylog2.rest.RestTools.respondWithFile;
import static org.graylog2.shared.rest.documentation.generator.Generator.CLOUD_VISIBLE;

@RequiresAuthentication
@Api(value = "Legacy/Search/Relative", description = "Message search", tags = {CLOUD_VISIBLE})
@Path("/search/universal/relative")
public class RelativeSearchResource extends SearchResource {

    private static final Logger LOG = LoggerFactory.getLogger(RelativeSearchResource.class);

    @Inject
    public RelativeSearchResource(Searches searches,
                                  ClusterConfigService clusterConfigService,
                                  DecoratorProcessor decoratorProcessor,
                                  SearchExecutor searchExecutor) {
        super(searches, clusterConfigService, decoratorProcessor, searchExecutor);
    }

    @GET
    @Timed
    @ApiOperation(value = "Message search with relative timerange.",
                  notes = "Search for messages in a relative timerange, specified as seconds from now. " +
                          "Example: 300 means search from 5 minutes ago to now.")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided.")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public SearchResponse searchRelative(
            @ApiParam(name = "query", value = "Query (Lucene syntax)", required = true)
            @QueryParam("query") @NotEmpty String query,
            @ApiParam(name = "range", value = "Relative timeframe to search in. See method description.", required = true)
            @QueryParam("range") @NotNull @PositiveOrZero Integer range,
            @ApiParam(name = "limit", value = "Maximum number of messages to return.") @QueryParam("limit") int limit,
            @ApiParam(name = "offset", value = "Offset") @QueryParam("offset") int offset,
            @ApiParam(name = "filter", value = "Filter") @QueryParam("filter") String filter,
            @ApiParam(name = "fields", value = "Comma separated list of fields to return") @QueryParam("fields") String fields,
            @ApiParam(name = "streams", value = "Comma separated list of stream IDs to search in") @QueryParam("streams")  String streams,
            @ApiParam(name = "sort", value = "Sorting (field:asc / field:desc)") @QueryParam("sort") String sort,
            @ApiParam(name = "decorate", value = "Run decorators on search result") @QueryParam("decorate") @DefaultValue("true") boolean decorate,
            @Context SearchUser searchUser) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_RELATIVE);

        final List<String> fieldList = parseOptionalFields(fields);
        final Sort sorting = buildSortOrder(sort);

        final TimeRange timeRange = buildRelativeTimeRange(range);
        final var parsedStreams = parseStreams(streams);

        return search(query, limit, offset, filter, parsedStreams, decorate, searchUser, fieldList, sorting, timeRange);
    }

    @GET
    @Timed
    @ApiOperation(value = "Message search with relative timerange.",
                  notes = "Search for messages in a relative timerange, specified as seconds from now. " +
                          "Example: 300 means search from 5 minutes ago to now.")
    @Produces(MoreMediaTypes.TEXT_CSV)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided.")
    })
    public ChunkedOutput<ResultChunk> searchRelativeChunked(
            @ApiParam(name = "query", value = "Query (Lucene syntax)", required = true)
            @QueryParam("query") @NotEmpty String query,
            @ApiParam(name = "range", value = "Relative timeframe to search in. See method description.", required = true)
            @QueryParam("range") @NotNull @PositiveOrZero Integer range,
            @ApiParam(name = "limit", value = "Maximum number of messages to return.", required = false) @QueryParam("limit") int limit,
            @ApiParam(name = "offset", value = "Offset", required = false) @QueryParam("offset") int offset,
            @ApiParam(name = "batch_size", value = "Batch size for the backend storage export request.", required = false) @QueryParam("batch_size") @DefaultValue(DEFAULT_SCROLL_BATCH_SIZE) int batchSize,
            @ApiParam(name = "filter", value = "Filter", required = false) @QueryParam("filter") String filter,
            @ApiParam(name = "streams", value = "Comma separated list of streams to search in") @QueryParam("streams")  String streams,
            @ApiParam(name = "fields", value = "Comma separated list of fields to return", required = true)
            @QueryParam("fields") @NotEmpty String fields) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_RELATIVE);

        final List<String> fieldList = parseFields(fields);
        final TimeRange timeRange = buildRelativeTimeRange(range);
        final var parsedStreams = parseStreams(streams);

        final ChunkedResult scroll = searches
                .scroll(query, timeRange, limit, offset, fieldList, filter, parsedStreams, batchSize);
        return buildChunkedOutput(scroll);
    }

    @GET
    @Path("/export")
    @Timed
    @ApiOperation(value = "Export message search with relative timerange.",
                  notes = "Search for messages in a relative timerange, specified as seconds from now. " +
                          "Example: 300 means search from 5 minutes ago to now.")
    @Produces(MoreMediaTypes.TEXT_CSV)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid timerange parameters provided.")
    })
    public Response exportSearchRelativeChunked(
            @ApiParam(name = "query", value = "Query (Lucene syntax)", required = true)
            @QueryParam("query") @NotEmpty String query,
            @ApiParam(name = "range", value = "Relative timeframe to search in. See method description.", required = true)
            @QueryParam("range") @PositiveOrZero int range,
            @ApiParam(name = "limit", value = "Maximum number of messages to return.", required = false) @QueryParam("limit") int limit,
            @ApiParam(name = "offset", value = "Offset", required = false) @QueryParam("offset") int offset,
            @ApiParam(name = "batch_size", value = "Batch size for the backend storage export request.", required = false) @QueryParam("batch_size") @DefaultValue(DEFAULT_SCROLL_BATCH_SIZE) int batchSize,
            @ApiParam(name = "filter", value = "Filter", required = false) @QueryParam("filter") String filter,
            @ApiParam(name = "streams", value = "Comma separated list of streams to search in") @QueryParam("streams")  String streams,
            @ApiParam(name = "fields", value = "Comma separated list of fields to return", required = true)
            @QueryParam("fields") @NotEmpty String fields) {
        checkSearchPermission(filter, RestPermissions.SEARCHES_RELATIVE);
        final String filename = "graylog-search-result-relative-" + range + ".csv";
        return respondWithFile(filename, searchRelativeChunked(query, range, limit, offset, batchSize, filter, streams, fields))
                .build();
    }

    private TimeRange buildRelativeTimeRange(int range) {
        try {
            return restrictTimeRange(RelativeRange.create(range));
        } catch (InvalidRangeParametersException e) {
            LOG.warn("Invalid timerange parameters provided. Returning HTTP 400.");
            throw new BadRequestException(e);
        }
    }
}
