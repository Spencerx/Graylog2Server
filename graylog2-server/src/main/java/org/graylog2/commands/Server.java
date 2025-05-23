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
package org.graylog2.commands;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.mongodb.MongoException;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.graylog.enterprise.EnterpriseModule;
import org.graylog.events.EventsModule;
import org.graylog.events.processor.EventDefinitionConfiguration;
import org.graylog.grn.GRNTypesModule;
import org.graylog.metrics.prometheus.PrometheusExporterConfiguration;
import org.graylog.metrics.prometheus.PrometheusMetricsModule;
import org.graylog.plugins.cef.CEFInputModule;
import org.graylog.plugins.datanode.DataNodeModule;
import org.graylog.plugins.formatting.units.UnitsModule;
import org.graylog.plugins.map.MapWidgetModule;
import org.graylog.plugins.map.config.GeoIpProcessorConfig;
import org.graylog.plugins.netflow.NetFlowPluginModule;
import org.graylog.plugins.pipelineprocessor.PipelineConfig;
import org.graylog.plugins.sidecar.SidecarModule;
import org.graylog.plugins.views.ViewsBindings;
import org.graylog.plugins.views.ViewsConfig;
import org.graylog.plugins.views.search.rest.scriptingapi.ScriptingApiModule;
import org.graylog.plugins.views.search.searchfilters.module.SearchFiltersModule;
import org.graylog.plugins.views.storage.migration.DatanodeMigrationBindings;
import org.graylog.scheduler.JobSchedulerConfiguration;
import org.graylog.scheduler.JobSchedulerModule;
import org.graylog.security.SecurityModule;
import org.graylog.security.certutil.CaModule;
import org.graylog.tracing.TracingModule;
import org.graylog2.Configuration;
import org.graylog2.alerts.AlertConditionBindings;
import org.graylog2.audit.AuditActor;
import org.graylog2.audit.AuditEventSender;
import org.graylog2.bindings.AlarmCallbackBindings;
import org.graylog2.bindings.ConfigurationModule;
import org.graylog2.bindings.DbEntitiesModule;
import org.graylog2.bindings.ElasticsearchModule;
import org.graylog2.bindings.InitializerBindings;
import org.graylog2.bindings.MessageFilterBindings;
import org.graylog2.bindings.MessageOutputBindings;
import org.graylog2.bindings.PasswordAlgorithmBindings;
import org.graylog2.bindings.PeriodicalBindings;
import org.graylog2.bindings.PersistenceServicesBindings;
import org.graylog2.bindings.ServerBindings;
import org.graylog2.bootstrap.Main;
import org.graylog2.bootstrap.ServerBootstrap;
import org.graylog2.cluster.leader.LeaderElectionService;
import org.graylog2.cluster.nodes.NodeService;
import org.graylog2.cluster.nodes.ServerNodeDto;
import org.graylog2.configuration.ContentStreamConfiguration;
import org.graylog2.configuration.ElasticsearchClientConfiguration;
import org.graylog2.configuration.ElasticsearchConfiguration;
import org.graylog2.configuration.EmailConfiguration;
import org.graylog2.configuration.HttpConfiguration;
import org.graylog2.configuration.TLSProtocolsConfiguration;
import org.graylog2.configuration.TelemetryConfiguration;
import org.graylog2.configuration.VersionCheckConfiguration;
import org.graylog2.contentpacks.ContentPacksModule;
import org.graylog2.database.entities.ScopedEntitiesModule;
import org.graylog2.datatiering.DataTieringModule;
import org.graylog2.decorators.DecoratorBindings;
import org.graylog2.featureflag.FeatureFlags;
import org.graylog2.indexer.FieldTypeManagementModule;
import org.graylog2.indexer.IndexerBindings;
import org.graylog2.indexer.retention.RetentionStrategyBindings;
import org.graylog2.indexer.rotation.RotationStrategyBindings;
import org.graylog2.inputs.transports.NettyTransportConfiguration;
import org.graylog2.lookup.adapters.dnslookup.DnsLookupAdapterConfiguration;
import org.graylog2.messageprocessors.MessageProcessorModule;
import org.graylog2.migrations.MigrationsModule;
import org.graylog2.notifications.Notification;
import org.graylog2.notifications.NotificationService;
import org.graylog2.plugin.KafkaJournalConfiguration;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.system.NodeId;
import org.graylog2.rest.resources.system.ClusterConfigValidatorModule;
import org.graylog2.shared.UI;
import org.graylog2.shared.bindings.MessageInputBindings;
import org.graylog2.shared.bindings.RestApiBindings;
import org.graylog2.shared.journal.Journal;
import org.graylog2.shared.system.activities.Activity;
import org.graylog2.shared.system.activities.ActivityWriter;
import org.graylog2.storage.VersionAwareStorageModule;
import org.graylog2.storage.versionprobe.VersionProbeModule;
import org.graylog2.streams.StreamsModule;
import org.graylog2.system.processing.ProcessingStatusConfig;
import org.graylog2.system.shutdown.GracefulShutdown;
import org.graylog2.telemetry.TelemetryModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.graylog2.audit.AuditEventTypes.NODE_SHUTDOWN_INITIATE;
import static org.graylog2.plugin.ServerStatus.Capability.CLOUD;
import static org.graylog2.plugin.ServerStatus.Capability.MASTER;
import static org.graylog2.plugin.ServerStatus.Capability.SERVER;

@Command(name = "server", description = "Start the Graylog server")
public class Server extends ServerBootstrap {
    protected static final Configuration configuration = new Configuration();
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private final HttpConfiguration httpConfiguration = new HttpConfiguration();
    private final ElasticsearchConfiguration elasticsearchConfiguration = new ElasticsearchConfiguration();
    private final ElasticsearchClientConfiguration elasticsearchClientConfiguration = new ElasticsearchClientConfiguration();
    private final EmailConfiguration emailConfiguration = new EmailConfiguration();
    private final VersionCheckConfiguration versionCheckConfiguration = new VersionCheckConfiguration();
    private final KafkaJournalConfiguration kafkaJournalConfiguration = new KafkaJournalConfiguration();
    private final NettyTransportConfiguration nettyTransportConfiguration = new NettyTransportConfiguration();
    private final PipelineConfig pipelineConfiguration = new PipelineConfig();
    private final ViewsConfig viewsConfiguration = new ViewsConfig();
    private final ProcessingStatusConfig processingStatusConfig = new ProcessingStatusConfig();
    private final JobSchedulerConfiguration jobSchedulerConfiguration = new JobSchedulerConfiguration();
    private final PrometheusExporterConfiguration prometheusExporterConfiguration = new PrometheusExporterConfiguration();
    private final TLSProtocolsConfiguration tlsConfiguration = new TLSProtocolsConfiguration();
    private final GeoIpProcessorConfig geoIpProcessorConfig = new GeoIpProcessorConfig();
    private final TelemetryConfiguration telemetryConfiguration = new TelemetryConfiguration();
    private final ContentStreamConfiguration contentStreamConfiguration = new ContentStreamConfiguration();
    private final DnsLookupAdapterConfiguration dnsLookupAdapterConfiguration = new DnsLookupAdapterConfiguration();
    private final EventDefinitionConfiguration eventDefinitionConfiguration = new EventDefinitionConfiguration();

    @Option(name = {"-l", "--local"}, description = "Run Graylog in local mode. Only interesting for Graylog developers.")
    private boolean local = false;

    public Server() {
        super("server", configuration);
    }

    public Server(String commandName) {
        super(commandName, configuration);
    }

    public boolean isLocal() {
        return local;
    }

    @Override
    protected @Nonnull List<Module> getNodeCommandBindings(FeatureFlags featureFlags) {
        final ImmutableList.Builder<Module> modules = ImmutableList.builder();
        modules.add(
                new VersionAwareStorageModule(configuration),
                new VersionProbeModule(),
                new ConfigurationModule(configuration),
                new DbEntitiesModule(),
                new ServerBindings(configuration, isMigrationCommand()),
                new ElasticsearchModule(),
                new PersistenceServicesBindings(),
                new MessageFilterBindings(),
                new MessageProcessorModule(),
                new AlarmCallbackBindings(),
                new InitializerBindings(),
                new MessageInputBindings(),
                new MessageOutputBindings(configuration, chainingClassLoader),
                new RotationStrategyBindings(elasticsearchConfiguration),
                new RetentionStrategyBindings(elasticsearchConfiguration),
                new PeriodicalBindings(),
                new RestApiBindings(configuration),
                new PasswordAlgorithmBindings(),
                new DecoratorBindings(),
                new AlertConditionBindings(),
                new IndexerBindings(),
                new MigrationsModule(),
                new NetFlowPluginModule(),
                new CEFInputModule(),
                new SidecarModule(),
                new ContentPacksModule(),
                new ViewsBindings(),
                new JobSchedulerModule(),
                new EventsModule(),
                new EnterpriseModule(),
                new GRNTypesModule(),
                new SecurityModule(),
                new PrometheusMetricsModule(),
                new ClusterConfigValidatorModule(),
                new MapWidgetModule(),
                new SearchFiltersModule(),
                new ScopedEntitiesModule(),
                new ScriptingApiModule(),
                new StreamsModule(),
                new TracingModule(),
                new UnitsModule(),
                new DataTieringModule(),
                new DatanodeMigrationBindings(),
                new CaModule(),
                new TelemetryModule(),
                new DataNodeModule()
        );

        modules.add(new FieldTypeManagementModule());

        return modules.build();
    }

    @Override
    protected @Nonnull List<Object> getNodeCommandConfigurationBeans() {
        return Arrays.asList(configuration,
                httpConfiguration,
                elasticsearchConfiguration,
                elasticsearchClientConfiguration,
                emailConfiguration,
                versionCheckConfiguration,
                kafkaJournalConfiguration,
                nettyTransportConfiguration,
                pipelineConfiguration,
                viewsConfiguration,
                processingStatusConfig,
                jobSchedulerConfiguration,
                prometheusExporterConfiguration,
                tlsConfiguration,
                geoIpProcessorConfig,
                telemetryConfiguration,
                contentStreamConfiguration,
                dnsLookupAdapterConfiguration,
                eventDefinitionConfiguration);
    }

    @Override
    protected void startNodeRegistration(Injector injector) {
        // Register this node.
        final NodeService<ServerNodeDto> nodeService = injector.getInstance(new Key<>() {});
        final ServerStatus serverStatus = injector.getInstance(ServerStatus.class);
        final ActivityWriter activityWriter = injector.getInstance(ActivityWriter.class);
        final LeaderElectionService leaderElectionService = injector.getInstance(LeaderElectionService.class);
        nodeService.registerServer(
                ServerNodeDto.Builder.builder()
                        .setId(serverStatus.getNodeId().toString())
                        .setLeader(leaderElectionService.isLeader())
                        .setTransportAddress(httpConfiguration.getHttpPublishUri().toString())
                        .setHostname(Tools.getLocalCanonicalHostname())
                        .build());
        serverStatus.setLocalMode(isLocal());
        if (leaderElectionService.isLeader() && !nodeService.isOnlyLeader(serverStatus.getNodeId())) {
            LOG.warn("Detected another leader in the cluster. Retrying in {} seconds to make sure it is not "
                    + "an old stale instance.", TimeUnit.MILLISECONDS.toSeconds(configuration.getStaleLeaderTimeout()));
            try {
                Thread.sleep(configuration.getStaleLeaderTimeout());
            } catch (InterruptedException e) { /* nope */ }

            if (!nodeService.isOnlyLeader(serverStatus.getNodeId())) {
                // All devils here.
                String what = "Detected other leader node in the cluster! Starting as non-leader! "
                        + "This is a mis-configuration you should fix.";
                LOG.warn(what);
                activityWriter.write(new Activity(what, Server.class));

                final NotificationService notificationService = injector.getInstance(NotificationService.class);

                // remove legacy notification, if present
                //noinspection deprecation
                notificationService.fixed(notificationService.build().addType(Notification.Type.MULTI_MASTER));

                // Write a notification.
                Notification notification = notificationService.buildNow()
                        .addType(Notification.Type.MULTI_LEADER)
                        .addSeverity(Notification.Severity.URGENT);
                notificationService.publishIfFirst(notification);

                configuration.setIsLeader(false);
            } else {
                LOG.warn("Stale leader has gone. Starting as leader.");
            }
        }
    }

    @Override
    protected Class<? extends Runnable> shutdownHook() {
        return ShutdownHook.class;
    }

    @Override
    protected void annotateInjectorExceptions(Collection<Message> messages) {
        super.annotateInjectorExceptions(messages);
        for (Message message : messages) {
            if (message.getCause() instanceof MongoException) {
                MongoException e = (MongoException) message.getCause();
                LOG.error(UI.wallString("Unable to connect to MongoDB. Is it running and the configuration correct?\n" +
                        "Details: " + e.getMessage()));
                System.exit(-1);
            }
        }
    }

    @Override
    protected Set<ServerStatus.Capability> capabilities() {
        final EnumSet<ServerStatus.Capability> capabilities = EnumSet.of(SERVER);

        if (configuration.isLeader()) {
            //noinspection deprecation
            capabilities.add(MASTER);
        }

        if (configuration.isCloud()) {
            capabilities.add(CLOUD);
        }

        return capabilities;
    }

    private static class ShutdownHook implements Runnable {
        private final ActivityWriter activityWriter;
        private final ServiceManager serviceManager;
        private final NodeId nodeId;
        private final GracefulShutdown gracefulShutdown;
        private final AuditEventSender auditEventSender;
        private final Journal journal;
        private final Service leaderElectionService;

        @Inject
        public ShutdownHook(ActivityWriter activityWriter,
                            ServiceManager serviceManager,
                            NodeId nodeId,
                            GracefulShutdown gracefulShutdown,
                            AuditEventSender auditEventSender,
                            Journal journal,
                            LeaderElectionService leaderElectionService) {
            this.activityWriter = activityWriter;
            this.serviceManager = serviceManager;
            this.nodeId = nodeId;
            this.gracefulShutdown = gracefulShutdown;
            this.auditEventSender = auditEventSender;
            this.journal = journal;
            this.leaderElectionService = leaderElectionService;
        }

        @Override
        public void run() {
            String msg = "SIGNAL received. Shutting down.";
            LOG.info(msg);
            activityWriter.write(new Activity(msg, Main.class));

            auditEventSender.success(AuditActor.system(nodeId), NODE_SHUTDOWN_INITIATE);

            gracefulShutdown.runWithoutExit();
            serviceManager.stopAsync().awaitStopped();

            leaderElectionService.stopAsync().awaitTerminated();

            // Some services might continue performing processing
            // after the Journal service being down. Therefore it's
            // important to flush the most actual journal offset value
            // right before shutting down to avoid repetitive processing
            // and duplicates.
            journal.flush();
        }
    }
}
