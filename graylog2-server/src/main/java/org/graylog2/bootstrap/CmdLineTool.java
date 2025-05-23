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
package org.graylog2.bootstrap;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.log4j2.InstrumentedAppender;
import com.github.joschi.jadconfig.JadConfig;
import com.github.joschi.jadconfig.ParameterException;
import com.github.joschi.jadconfig.Repository;
import com.github.joschi.jadconfig.RepositoryException;
import com.github.joschi.jadconfig.ValidationException;
import com.github.joschi.jadconfig.guava.GuavaConverterFactory;
import com.github.joschi.jadconfig.jodatime.JodaTimeConverterFactory;
import com.github.joschi.jadconfig.repositories.EnvironmentRepository;
import com.github.joschi.jadconfig.repositories.PropertiesRepository;
import com.github.joschi.jadconfig.repositories.SystemPropertiesRepository;
import com.github.luben.zstd.util.Native;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TLSCipherSuiteSelector;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.graylog2.GraylogNodeConfiguration;
import org.graylog2.bindings.NamedConfigParametersOverrideModule;
import org.graylog2.bootstrap.commands.MigrateCmd;
import org.graylog2.configuration.NativeLibPathConfiguration;
import org.graylog2.configuration.TLSProtocolsConfiguration;
import org.graylog2.featureflag.FeatureFlags;
import org.graylog2.featureflag.FeatureFlagsFactory;
import org.graylog2.plugin.DocsHelper;
import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginLoaderConfig;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.Version;
import org.graylog2.plugin.system.NodeIdPersistenceException;
import org.graylog2.shared.UI;
import org.graylog2.shared.bindings.GuiceInjectorHolder;
import org.graylog2.shared.bindings.IsDevelopmentBindings;
import org.graylog2.shared.bindings.PluginBindings;
import org.graylog2.shared.metrics.MetricRegistryFactory;
import org.graylog2.shared.plugins.ChainingClassLoader;
import org.graylog2.shared.plugins.LoggingClassLoader;
import org.graylog2.shared.plugins.PluginLoader;
import org.graylog2.shared.utilities.ExceptionUtils;
import org.graylog2.storage.SearchVersion;
import org.graylog2.storage.UnsupportedSearchException;
import org.graylog2.storage.versionprobe.ElasticsearchProbeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.nullToEmpty;

public abstract class CmdLineTool<NodeConfiguration extends GraylogNodeConfiguration> implements CliCommand {

    static {
        // Set up JDK Logging adapter, https://logging.apache.org/log4j/2.x/log4j-jul/index.html
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    private static final Logger LOG = LoggerFactory.getLogger(CmdLineTool.class);

    protected static final Version version = Version.CURRENT_CLASSPATH;
    protected static final String FILE_SEPARATOR = System.getProperty("file.separator");
    protected static final String TMPDIR = System.getProperty("java.io.tmpdir", "/tmp");

    protected final JadConfig jadConfig;
    protected final NodeConfiguration configuration;
    protected final ChainingClassLoader chainingClassLoader;

    @Option(name = "--dump-config", description = "Show the effective Graylog configuration and exit")
    protected boolean dumpConfig = false;

    @Option(name = "--dump-default-config", description = "Show the default configuration and exit")
    protected boolean dumpDefaultConfig = false;

    @Option(name = {"-d", "--debug"}, description = "Run Graylog in debug mode")
    private boolean debug = false;

    @Option(name = {"-f", "--configfile"}, description = "Configuration file for Graylog")
    protected String configFile = "/etc/graylog/server/server.conf";

    @Option(name = {"-ff", "--featureflagfile"}, description = "Configuration file for Graylog feature flags")
    protected String customFeatureFlagFile = "/etc/graylog/server/feature-flag.conf";

    protected String commandName = "command";

    protected Injector injector;
    protected Injector bootstrapConfigInjector;
    protected FeatureFlags featureFlags;
    protected PluginLoader pluginLoader;

    protected CmdLineTool(NodeConfiguration configuration) {
        this(null, configuration);
    }

    protected CmdLineTool(String commandName, NodeConfiguration configuration) {
        // Wrap the context class loader to allow logging of failed class and resource lookups.
        Thread.currentThread().setContextClassLoader(new LoggingClassLoader(Thread.currentThread().getContextClassLoader()));

        jadConfig = new JadConfig();
        addConverters(jadConfig);

        if (commandName == null) {
            if (this.getClass().isAnnotationPresent(Command.class)) {
                this.commandName = this.getClass().getAnnotation(Command.class).name();
            } else {
                this.commandName = "tool";
            }
        } else {
            this.commandName = commandName;
        }
        this.configuration = configuration;
        this.chainingClassLoader = new ChainingClassLoader(this.getClass().getClassLoader());
    }

    /**
     * Validate the given configuration for this command.
     *
     * @return {@code true} if the configuration is valid, {@code false}.
     */
    protected boolean validateConfiguration() {
        return true;
    }

    public boolean isDumpConfig() {
        return dumpConfig;
    }

    public boolean isDumpDefaultConfig() {
        return dumpDefaultConfig;
    }

    public boolean isDebug() {
        return debug;
    }

    protected abstract List<Module> getCommandBindings(FeatureFlags featureFlags);

    protected abstract List<Object> getCommandConfigurationBeans();

    public boolean isMigrationCommand() {
        return commandName.equals(MigrateCmd.MIGRATION_COMMAND);
    }

    /**
     * Things that have to run before the {@link #startCommand()} method is being called.
     * Please note that this happens *before* the configuration file has been parsed.
     */
    protected void beforeStart() {
        // This needs to run before the first SSLContext is instantiated,
        // because it sets up the default SSLAlgorithmConstraints
        applySecuritySettings(parseAndGetTLSConfiguration(configFile));

        // Set these early in the startup because netty's NativeLibraryUtil uses a static initializer
        if (configuration instanceof NativeLibPathConfiguration) {
            setNettyNativeDefaults(parseAndGetNativeLibPathConfiguration(configFile));
        }
    }

    /**
     * Things that have to run before the guice injector is created.
     * This call happens *after* the configuration file has been parsed.
     *
     * @param plugins The already loaded plugins
     */
    protected void beforeInjectorCreation(Set<Plugin> plugins) {
    }

    protected static void applySecuritySettings(TLSProtocolsConfiguration configuration) {
        // Disable insecure TLS parameters and ciphers by default.
        // Prevent attacks like LOGJAM, LUCKY13, et al.
        setSystemPropertyIfEmpty("jdk.tls.ephemeralDHKeySize", "2048");
        setSystemPropertyIfEmpty("jdk.tls.rejectClientInitiatedRenegotiation", "true");

        final Set<String> tlsProtocols = configuration.getConfiguredTlsProtocols();
        final List<String> disabledAlgorithms = Stream.of(Security.getProperty("jdk.tls.disabledAlgorithms").split(",")).map(String::trim).collect(Collectors.toList());

        // Only restrict ciphers if weak / insecure TLS protocols are explicitly enabled.
        // c.f. https://github.com/Graylog2/graylog2-server/issues/10944
        if (tlsProtocols == null || !(tlsProtocols.isEmpty() || tlsProtocols.contains("TLSv1") || tlsProtocols.contains("TLSv1.1"))) {
            disabledAlgorithms.addAll(ImmutableSet.of("CBC", "3DES"));
            Security.setProperty("jdk.tls.disabledAlgorithms", String.join(", ", disabledAlgorithms));
        } else {
            // Remove explicitly enabled legacy TLS protocols from the disabledAlgorithms filter
            Set<String> reEnabledTLSProtocols;
            if (tlsProtocols.isEmpty()) {
                reEnabledTLSProtocols = ImmutableSet.of("TLSv1", "TLSv1.1");
            } else {
                reEnabledTLSProtocols = tlsProtocols;
            }
            final List<String> updatedProperties = disabledAlgorithms.stream()
                    .filter(p -> !reEnabledTLSProtocols.contains(p))
                    .collect(Collectors.toList());

            // This is only effective when it is set before the static initializer of sun.security.ssl.Ciphersuite is executed.
            Security.setProperty("jdk.tls.disabledAlgorithms", String.join(", ", updatedProperties));

            // https://github.com/pingidentity/ldapsdk/commit/837b8f0fe2947e64fcee0209e9b8ef713028515e
            SSLUtil.setEnabledSSLCipherSuites(TLSCipherSuiteSelector.getSupportedCipherSuites());
        }

        // Explicitly register Bouncy Castle as security provider.
        // This allows us to use more key formats than with JCE
        Security.addProvider(new BouncyCastleProvider());
    }


    private void setNettyNativeDefaults(NativeLibPathConfiguration pathConfiguration) {
        // Give netty a better spot than /tmp to unpack its tcnative libraries
        if (System.getProperty("io.netty.native.workdir") == null) {
            System.setProperty("io.netty.native.workdir", pathConfiguration.getNativeLibDir().toAbsolutePath().toString());
        }
        // The jna.tmpdir should reside in the native lib dir. (See: https://github.com/Graylog2/graylog2-server/issues/21223)
        if (System.getProperty("jna.tmpdir") == null) {
            System.setProperty("jna.tmpdir", pathConfiguration.getNativeLibDir().toAbsolutePath().resolve("jna").toString());
        }
        // Don't delete the native lib after unpacking, as this confuses needrestart(1) on some distributions
        if (System.getProperty("io.netty.native.deleteLibAfterLoading") == null) {
            System.setProperty("io.netty.native.deleteLibAfterLoading", "false");
        }
    }

    private static void setSystemPropertyIfEmpty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    @Override
    public void run() {
        // Setup logger first to ensure we can log any caught Throwable to the configured log file
        final Level logLevel = setupLogger();
        try {
            doRun(logLevel);
        } catch (Throwable e) {
            LOG.error("Startup error:", e);
            throw e;
        }
    }

    public void doRun(Level logLevel) {
        if (configuration instanceof NativeLibPathConfiguration) {
            NativeLibPathConfiguration pathConfiguration = parseAndGetNativeLibPathConfiguration(configFile);

            // Move the zstd temp folder from /tmp to our native lib dir to avoid issues with noexec-mounted /tmp directories.
            // See: https://github.com/Graylog2/graylog2-server/issues/17837
            // WARNING: This needs to be set before the first use of the zstd library. Our in-memory logger is using
            //          zstd library, so we need to set it before the first usage of the Logger instance.
            //          Setting it after the first library usage wouldn't have any effect.
            if (Native.isLoaded()) {
                LOG.warn("The zstd library is already loaded. Setting the ZstdTempFolder property doesn't have any effect!");
            }
            final Path nativeLibPath = pathConfiguration.getNativeLibDir().toAbsolutePath();
            try {
                // We are very early in the startup process and the data_dir and native lib dir don't exist yet. Since the
                // zstd library doesn't create its own temp directory, we have to do it to avoid errors on startup.
                Files.createDirectories(nativeLibPath);
                System.setProperty("ZstdTempFolder", nativeLibPath.toString());
            } catch (IOException e) {
                LOG.warn("Couldn't create native lib dir <{}>. Unable to set ZstdTempFolder system property.", nativeLibPath, e);
            }
        }

        // This is holding all our metrics.
        MetricRegistry metricRegistry = MetricRegistryFactory.create();
        featureFlags = getFeatureFlags(metricRegistry);

        if (configuration.withPlugins()) {
            pluginLoader = getPluginLoader(getPluginLoaderConfig(configFile), chainingClassLoader);
        }

        installCommandConfig();
        if (configuration.withPlugins()) {
            installPluginBootstrapConfig(pluginLoader);
        }

        if (isDumpDefaultConfig()) {
            dumpDefaultConfigAndExit();
        }

        installConfigRepositories();

        beforeStart();

        processConfiguration(jadConfig);

        bootstrapConfigInjector = setupBootstrapConfigInjector();

        Set<Plugin> plugins = new HashSet<>();
        if (configuration.withPlugins()) {
            plugins = loadPlugins();
            installPluginConfig(plugins);
            processConfiguration(jadConfig);
        }

        if (isDumpConfig()) {
            dumpCurrentConfigAndExit();
        }

        if (!validateConfiguration()) {
            LOG.error("Validating configuration file failed - exiting.");
            System.exit(1);
        }


        final List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        LOG.info("Running with JVM arguments: {}", Joiner.on(' ').join(arguments));

        beforeInjectorCreation(plugins);

        injector = setupInjector(
                new IsDevelopmentBindings(),
                new NamedConfigParametersOverrideModule(jadConfig.getConfigurationBeans()),
                new PluginBindings(plugins),
                binder -> binder.bind(MetricRegistry.class).toInstance(metricRegistry)
        );

        if (injector == null) {
            LOG.error("Injector could not be created, exiting! (Please include the previous error messages in bug " +
                    "reports.)");
            System.exit(1);
        }

        addInstrumentedAppender(metricRegistry);
        // Report metrics via JMX.
        final JmxReporter reporter = JmxReporter.forRegistry(metricRegistry).build();
        reporter.start();

        startCommand();
    }

    protected PluginLoader getPluginLoader(PluginLoaderConfig pluginLoaderConfig, ChainingClassLoader classLoader) {
        return new PluginLoader(pluginLoaderConfig.getPluginDir().toFile(), classLoader);
    }

    private void installPluginBootstrapConfig(PluginLoader pluginLoader) {
        pluginLoader.loadPluginBootstrapConfigs().forEach(jadConfig::addConfigurationBean);
    }

    // Parse only the TLSConfiguration bean
    // to avoid triggering anything that might initialize the default SSLContext
    protected TLSProtocolsConfiguration parseAndGetTLSConfiguration(String configFile) {
        final JadConfig jadConfig = new JadConfig();
        jadConfig.setRepositories(getConfigRepositories(configFile));
        final TLSProtocolsConfiguration tlsConfiguration = new TLSProtocolsConfiguration();
        jadConfig.addConfigurationBean(tlsConfiguration);
        processConfiguration(jadConfig);

        return tlsConfiguration;
    }

    protected NativeLibPathConfiguration parseAndGetNativeLibPathConfiguration(String configFile) {
        final NativeLibPathConfiguration pathConfiguration = (NativeLibPathConfiguration) configuration;
        final JadConfig config = new JadConfig(getConfigRepositories(configFile), pathConfiguration);
        addConverters(config);
        processConfiguration(config);
        return pathConfiguration;
    }

    /**
     * The server configuration file contains config values that require these converters.
     * For example `root_timezone = America/Chicago`.
     * <p>
     * The converters must be added to each instance of JadConfig before calling `JadConfig.process()` or else
     * configuration value parsing might fail which could halt server startup.
     */
    private void addConverters(JadConfig config) {
        config.addConverterFactory(new GuavaConverterFactory());
        config.addConverterFactory(new JodaTimeConverterFactory());
    }

    private void installCommandConfig() {
        getCommandConfigurationBeans().forEach(jadConfig::addConfigurationBean);
    }

    private void installPluginConfig(Set<Plugin> plugins) {
        plugins.stream()
                .flatMap(plugin -> plugin.modules().stream())
                .flatMap(pm -> pm.getConfigBeans().stream())
                .forEach(jadConfig::addConfigurationBean);
    }

    protected abstract void startCommand();

    protected Level setupLogger() {
        final Level logLevel;
        if (isDebug()) {
            LOG.info("Running in Debug mode");
            logLevel = Level.DEBUG;

            // Enable logging for Netty when running in debug mode.
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
        } else if (onlyLogErrors()) {
            logLevel = Level.ERROR;
        } else {
            logLevel = Level.INFO;
        }

        initializeLogging(logLevel);

        return logLevel;
    }

    private void initializeLogging(final Level logLevel) {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(logLevel);
        config.getLoggerConfig(Main.class.getPackage().getName()).setLevel(logLevel);

        context.updateLoggers(config);
    }

    private void addInstrumentedAppender(final MetricRegistry metrics) {
        final InstrumentedAppender appender = new InstrumentedAppender(metrics, null, null, false);
        appender.start();

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
        config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).addAppender(appender, null, null);
        context.updateLoggers(config);
    }

    protected boolean onlyLogErrors() {
        return false;
    }

    private void dumpCurrentConfigAndExit() {
        System.out.println(dumpConfiguration(jadConfig.dump()));
        System.exit(0);
    }

    private void dumpDefaultConfigAndExit() {
        bootstrapConfigInjector = setupBootstrapConfigInjector();
        if (configuration.withPlugins()) {
            installPluginConfig(pluginLoader.loadPlugins(bootstrapConfigInjector));
        }
        dumpCurrentConfigAndExit();
    }

    private PluginLoaderConfig getPluginLoaderConfig(String configFile) {
        final PluginLoaderConfig pluginLoaderConfig = new PluginLoaderConfig();
        processConfiguration(new JadConfig(getConfigRepositories(configFile), pluginLoaderConfig));

        return pluginLoaderConfig;
    }

    private FeatureFlags getFeatureFlags(MetricRegistry metricRegistry) {
        return new FeatureFlagsFactory().createImmutableFeatureFlags(customFeatureFlagFile, metricRegistry, configuration);
    }

    protected Set<Plugin> loadPlugins() {
        final Set<Plugin> plugins = new HashSet<>();

        for (Plugin plugin : pluginLoader.loadPlugins(bootstrapConfigInjector)) {
            final PluginMetaData metadata = plugin.metadata();

            final GraylogNodeConfiguration config = bootstrapConfigInjector.getInstance(configuration.getClass());
            // TODO do we want this here? We are also considering removing the deprecated CollectorPlugin entirely
            if (config.isCloud()) {
                if (metadata.getUniqueId().equals("org.graylog.plugins.collector.CollectorPlugin")) {
                    continue;
                }
            }
            if (capabilities().containsAll(metadata.getRequiredCapabilities())) {
                if (version.sameOrHigher(metadata.getRequiredVersion())) {
                    LOG.info("Loaded plugin: {}", plugin);
                    plugins.add(plugin);
                } else {
                    LOG.error("Plugin \"" + metadata.getName() + "\" requires version " + metadata.getRequiredVersion() + " - not loading!");
                }
            } else {
                LOG.debug("Skipping plugin \"{}\" because some capabilities are missing ({}).",
                        metadata.getName(),
                        Sets.difference(plugin.metadata().getRequiredCapabilities(), capabilities()));
            }
        }

        return plugins;
    }

    protected Collection<Repository> getConfigRepositories(String configFile) {
        return Arrays.asList(
                new EnvironmentRepository(configuration.getEnvironmentVariablePrefix()),
                new SystemPropertiesRepository(configuration.getSystemPropertyPrefix()),
                // Legacy prefixes
                new EnvironmentRepository("GRAYLOG2_"),
                new SystemPropertiesRepository("graylog2."),
                new PropertiesRepository(configFile)
        );
    }

    private String dumpConfiguration(final Map<String, String> configMap) {
        final StringBuilder sb = new StringBuilder();
        sb.append("# Configuration of graylog2-").append(commandName).append(" ").append(version).append(System.lineSeparator());
        sb.append("# Generated on ").append(Tools.nowUTC()).append(System.lineSeparator());

        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            sb.append(entry.getKey()).append('=').append(nullToEmpty(entry.getValue())).append(System.lineSeparator());
        }

        return sb.toString();
    }

    private void installConfigRepositories() {
        installConfigRepositories(jadConfig);
    }

    protected void installConfigRepositories(JadConfig jadConfig) {
        jadConfig.setRepositories(getConfigRepositories(configFile));
    }

    protected void processConfiguration(JadConfig jadConfig) {
        try {
            jadConfig.process();
        } catch (RepositoryException e) {
            LOG.error("Couldn't load configuration: {}", e.getMessage());
            System.exit(1);
        } catch (ParameterException | ValidationException e) {
            LOG.error("Invalid configuration", e);
            System.exit(1);
        }
    }

    protected List<Module> getSharedBindingsModules() {
        return Lists.newArrayList();
    }

    protected Injector setupInjector(Module... modules) {
        try {
            final ImmutableList.Builder<Module> builder = ImmutableList.builder();
            builder.addAll(getSharedBindingsModules());
            builder.addAll(getCommandBindings(featureFlags));
            builder.addAll(Arrays.asList(modules));
            builder.add(binder -> {
                binder.bind(ChainingClassLoader.class).toInstance(chainingClassLoader);
                featureFlagsBinding(binder);
                binder.bind(String.class).annotatedWith(Names.named("BootstrapCommand")).toInstance(commandName);
            });
            return GuiceInjectorHolder.createInjector(builder.build());
        } catch (CreationException e) {
            annotateInjectorCreationException(e);
            return null;
        } catch (Exception e) {
            LOG.error("Injector creation failed!", e);
            return null;
        }
    }

    /**
     * Set up a separate injector, containing only the core and bootstrap configuration bindings.
     * It can be used to look up configuration values in modules at binding time.
     */
    protected Injector setupBootstrapConfigInjector() {
        final AbstractModule configModule =
                new NamedConfigParametersOverrideModule(jadConfig.getConfigurationBeans());

        Injector bootstrapConfigInjector = null;
        try {
            bootstrapConfigInjector = Guice.createInjector(Stage.PRODUCTION, ImmutableList.of(configModule,
                    (Module) Binder::requireExplicitBindings, this::featureFlagsBinding));
        } catch (CreationException e) {
            annotateInjectorCreationException(e);
        } catch (Exception e) {
            LOG.error("Injector creation failed!", e);
        }

        if (bootstrapConfigInjector == null) {
            LOG.error("Injector for bootstrap configuration could not be created, exiting! (Please include the " +
                    "previous error messages in bug reports.)");
            System.exit(1);
        }
        return bootstrapConfigInjector;
    }

    protected void featureFlagsBinding(Binder binder) {
        binder.bind(FeatureFlags.class).toInstance(featureFlags);
    }

    protected void annotateInjectorCreationException(CreationException e) {
        annotateInjectorExceptions(e.getErrorMessages());
        throw e;
    }

    protected void annotateInjectorExceptions(Collection<Message> messages) {
        for (Message message : messages) {
            //noinspection ThrowableResultOfMethodCallIgnored
            final Throwable rootCause = ExceptionUtils.getRootCause(message.getCause());
            if (configuration.withNodeIdFile() && rootCause instanceof NodeIdPersistenceException) {
                LOG.error(UI.wallString(
                        "Unable to read or persist your NodeId file. This means your node id file is not readable or writable by the current user. The following exception might give more information: " + message));
                System.exit(-1);
            } else if (rootCause instanceof AccessDeniedException) {
                LOG.error(UI.wallString("Unable to access file " + rootCause.getMessage()));
                System.exit(-2);
            } else if (rootCause instanceof UnsupportedSearchException) {
                final SearchVersion search = ((UnsupportedSearchException) rootCause).getSearchMajorVersion();
                LOG.error(UI.wallString("Unsupported search version: " + search, DocsHelper.PAGE_ES_VERSIONS.toString()));
                System.exit(-3);
            } else if (rootCause instanceof ElasticsearchProbeException) {
                LOG.error(UI.wallString(rootCause.getMessage(), DocsHelper.PAGE_ES_CONFIGURATION.toString()));
                System.exit(-4);
            } else {
                // other guice error, still print the raw messages
                // TODO this could potentially print duplicate messages depending on what a subclass does...
                LOG.error("Guice error (more detail on log level debug): {}", message.getMessage());
                if (rootCause != null) {
                    LOG.debug("Stacktrace:", rootCause);
                }
            }
        }
    }

    protected Set<ServerStatus.Capability> capabilities() {
        return Collections.emptySet();
    }

    @VisibleForTesting
    protected void setConfigFile(String configFile) {
        this.configFile = configFile;
    }
}
