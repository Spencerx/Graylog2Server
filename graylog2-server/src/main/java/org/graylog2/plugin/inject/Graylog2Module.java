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
package org.graylog2.plugin.inject;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Names;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.AuthorizingRealm;
import org.graylog.plugins.views.search.db.StaticReferencedSearch;
import org.graylog.plugins.views.search.views.ViewResolver;
import org.graylog2.audit.AuditEventSender;
import org.graylog2.audit.AuditEventType;
import org.graylog2.audit.PluginAuditEventTypes;
import org.graylog2.audit.formatter.AuditEventFormatter;
import org.graylog2.bootstrap.preflight.PreflightCheck;
import org.graylog2.contentpacks.constraints.ConstraintChecker;
import org.graylog2.contentpacks.facades.EntityWithExcerptFacade;
import org.graylog2.contentpacks.model.ModelType;
import org.graylog2.migrations.Migration;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.decorators.SearchResponseDecorator;
import org.graylog2.plugin.indexer.retention.RetentionStrategy;
import org.graylog2.plugin.indexer.rotation.RotationStrategy;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.annotations.ConfigClass;
import org.graylog2.plugin.inputs.annotations.FactoryClass;
import org.graylog2.plugin.inputs.codecs.Codec;
import org.graylog2.plugin.inputs.transports.Transport;
import org.graylog2.plugin.lookup.LookupCache;
import org.graylog2.plugin.lookup.LookupCacheConfiguration;
import org.graylog2.plugin.lookup.LookupDataAdapter;
import org.graylog2.plugin.lookup.LookupDataAdapterConfiguration;
import org.graylog2.plugin.outputs.FilteredMessageOutput;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.security.PasswordAlgorithm;
import org.graylog2.plugin.security.PluginPermissions;
import org.graylog2.plugin.validate.ClusterConfigValidator;
import org.graylog2.streams.StreamDeletionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Set;

public abstract class Graylog2Module extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(Graylog2Module.class);

    public static final String SYSTEM_REST_RESOURCES = "systemRestResources";
    public static final String DB_ENTITIES = "dbEntities";

    protected void installTransport(
            MapBinder<String, Transport.Factory<? extends Transport>> mapBinder,
            String name,
            Class<? extends Transport> transportClass) {

        final Class<? extends Transport.Config> configClass =
                (Class<? extends Transport.Config>)
                        findInnerClassAnnotatedWith(ConfigClass.class, transportClass, Transport.Config.class);

        final Class<? extends Transport.Factory<? extends Transport>> factoryClass =
                (Class<? extends Transport.Factory<? extends Transport>>)
                        findInnerClassAnnotatedWith(FactoryClass.class, transportClass, Transport.Factory.class);

        if (configClass == null) {
            LOG.error("Unable to find an inner class annotated with @ConfigClass in transport {}. This transport will not be available!",
                    transportClass);
            return;
        }
        if (factoryClass == null) {
            LOG.error("Unable to find an inner class annotated with @FactoryClass in transport {}. This transport will not be available!",
                    transportClass);
            return;
        }
        installTransport(mapBinder, name, transportClass, configClass, factoryClass);
    }

    protected void installTransport(
            MapBinder<String, Transport.Factory<? extends Transport>> mapBinder,
            String name,
            Class<? extends Transport> transportClass,
            Class<? extends Transport.Config> configClass,
            Class<? extends Transport.Factory<? extends Transport>> factoryClass) {
        final Key<? extends Transport.Factory<? extends Transport>> factoryKey = Key.get(factoryClass);
        install(new FactoryModuleBuilder()
                .implement(Transport.class, transportClass)
                .implement(Transport.Config.class, configClass)
                .build(factoryClass));

        mapBinder.addBinding(name).to(factoryKey);
    }

    protected void installCodec(MapBinder<String, Codec.Factory<? extends Codec>> mapBinder, Class<? extends Codec> codecClass) {
        if (codecClass.isAnnotationPresent(org.graylog2.plugin.inputs.annotations.Codec.class)) {
            final org.graylog2.plugin.inputs.annotations.Codec a = codecClass.getAnnotation(org.graylog2.plugin.inputs.annotations.Codec.class);
            installCodec(mapBinder, a.name(), codecClass);
        } else {
            LOG.error("Codec {} not annotated with {}. Cannot determine its id. This is a bug, please use that annotation, this codec will not be available",
                    codecClass, org.graylog2.plugin.inputs.annotations.Codec.class);
        }
    }

    protected void installCodec(
            MapBinder<String, Codec.Factory<? extends Codec>> mapBinder,
            String name,
            Class<? extends Codec> codecClass) {

        final Class<? extends Codec.Config> configClass =
                (Class<? extends Codec.Config>)
                        findInnerClassAnnotatedWith(ConfigClass.class, codecClass, Codec.Config.class);

        final Class<? extends Codec.Factory<? extends Codec>> factoryClass =
                (Class<? extends Codec.Factory<? extends Codec>>)
                        findInnerClassAnnotatedWith(FactoryClass.class, codecClass, Codec.Factory.class);

        if (configClass == null) {
            LOG.error("Unable to find an inner class annotated with @ConfigClass in codec {}. This codec will not be available!",
                    codecClass);
            return;
        }
        if (factoryClass == null) {
            LOG.error("Unable to find an inner class annotated with @FactoryClass in codec {}. This codec will not be available!",
                    codecClass);
            return;
        }
        installCodec(mapBinder, name, codecClass, configClass, factoryClass);
    }

    protected void installCodec(
            MapBinder<String, Codec.Factory<? extends Codec>> mapBinder,
            String name,
            Class<? extends Codec> codecClass,
            Class<? extends Codec.Config> configClass,
            Class<? extends Codec.Factory<? extends Codec>> factoryClass) {

        final Key<? extends Codec.Factory<? extends Codec>> factoryKey = Key.get(factoryClass);

        install(new FactoryModuleBuilder()
                .implement(Codec.class, codecClass)
                .implement(Codec.Config.class, configClass)
                .build(factoryClass));

        mapBinder.addBinding(name).to(factoryKey);
    }

    @Nullable
    protected Class<?> findInnerClassAnnotatedWith(Class<? extends Annotation> annotationClass,
                                                   Class<?> containingClass,
                                                   Class<?> targetClass) {
        final Class<?>[] declaredClasses = containingClass.getDeclaredClasses();
        Class<?> annotatedClass = null;
        for (final Class<?> declaredClass : declaredClasses) {
            if (!declaredClass.isAnnotationPresent(annotationClass)) {
                continue;
            }
            if (targetClass.isAssignableFrom(declaredClass)) {
                if (annotatedClass != null) {
                    LOG.error("Multiple annotations for {} found in {}. This is invalid.", annotatedClass.getSimpleName(), containingClass);
                    return null;
                }
                annotatedClass = declaredClass;
            } else {
                LOG.error("{} annotated as {} is not extending the expected {}. Did you forget to implement the correct interface?",
                        declaredClass, annotationClass.getSimpleName(), targetClass);
                return null;
            }
        }
        return annotatedClass;
    }

    protected MapBinder<String, Codec.Factory<? extends Codec>> codecMapBinder() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<Codec.Factory<? extends Codec>>() {
                });
    }

    protected MapBinder<String, Transport.Factory<? extends Transport>> transportMapBinder() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<Transport.Factory<? extends Transport>>() {
                });
    }

    protected MapBinder<String, MessageInput.Factory<? extends MessageInput>> inputsMapBinder() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<MessageInput.Factory<? extends MessageInput>>() {
                });
    }

    protected MapBinder<String, RotationStrategy> rotationStrategiesMapBinder() {
        return MapBinder.newMapBinder(binder(), String.class, RotationStrategy.class);
    }

    protected MapBinder<String, RetentionStrategy> retentionStrategyMapBinder() {
        return MapBinder.newMapBinder(binder(), String.class, RetentionStrategy.class);
    }

    protected void installRotationStrategy(MapBinder<String, RotationStrategy> mapBinder, Class<? extends RotationStrategy> target) {
        mapBinder.addBinding(target.getCanonicalName()).to(target);
    }

    protected void installRetentionStrategy(MapBinder<String, RetentionStrategy> mapBinder, Class<? extends RetentionStrategy> target) {
        mapBinder.addBinding(target.getCanonicalName()).to(target);
    }

    protected <T extends MessageInput> void installInput(MapBinder<String, MessageInput.Factory<? extends MessageInput>> inputMapBinder,
                                                         Class<T> target,
                                                         Class<? extends MessageInput.Factory<T>> targetFactory) {
        install(new FactoryModuleBuilder().implement(MessageInput.class, target).build(targetFactory));
        inputMapBinder.addBinding(target.getCanonicalName()).to(Key.get(targetFactory));
    }

    protected <T extends MessageInput> void installInput(MapBinder<String, MessageInput.Factory<? extends MessageInput>> inputMapBinder,
                                                         Class<T> target) {
        Class<? extends MessageInput.Factory<T>> factoryClass =
                (Class<? extends MessageInput.Factory<T>>) findInnerClassAnnotatedWith(FactoryClass.class, target, MessageInput.Factory.class);

        if (factoryClass == null) {
            LOG.error("Unable to find an inner class annotated with @FactoryClass in input {}. This input will not be available!", target);
            return;
        }

        installInput(inputMapBinder, target, factoryClass);
    }

    // This should only be used by plugins that have been built before Graylog 3.0.1.
    // See comments in MessageOutput.Factory and MessageOutput.Factory2 for details
    protected MapBinder<String, MessageOutput.Factory<? extends MessageOutput>> outputsMapBinder() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<MessageOutput.Factory<? extends MessageOutput>>() {
                });
    }

    // This should only be used by plugins that have been built before Graylog 3.0.1.
    // See comments in MessageOutput.Factory and MessageOutput.Factory2 for details
    protected <T extends MessageOutput> void installOutput(MapBinder<String, MessageOutput.Factory<? extends MessageOutput>> outputMapBinder,
                                                           Class<T> target,
                                                           Class<? extends MessageOutput.Factory<T>> targetFactory) {
        install(new FactoryModuleBuilder().implement(MessageOutput.class, target).build(targetFactory));
        outputMapBinder.addBinding(target.getCanonicalName()).to(Key.get(targetFactory));
    }

    // This should be used by plugins that have been built for 3.0.1 or later.
    // See comments in MessageOutput.Factory and MessageOutput.Factory2 for details
    protected MapBinder<String, MessageOutput.Factory2<? extends MessageOutput>> outputsMapBinder2() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<MessageOutput.Factory2<? extends MessageOutput>>() {
                });
    }

    // This should be used by plugins that have been built for 3.0.1 or later.
    // See comments in MessageOutput.Factory and MessageOutput.Factory2 for details
    protected <T extends MessageOutput> void installOutput2(MapBinder<String, MessageOutput.Factory2<? extends MessageOutput>> outputMapBinder,
                                                            Class<T> target,
                                                            Class<? extends MessageOutput.Factory2<T>> targetFactory) {
        install(new FactoryModuleBuilder().implement(MessageOutput.class, target).build(targetFactory));
        outputMapBinder.addBinding(target.getCanonicalName()).to(Key.get(targetFactory));
    }

    protected <T extends MessageOutput> void installOutput(MapBinder<String, MessageOutput.Factory<? extends MessageOutput>> outputMapBinder,
                                                           Class<T> target) {
        Class<? extends MessageOutput.Factory<T>> factoryClass =
                (Class<? extends MessageOutput.Factory<T>>) findInnerClassAnnotatedWith(FactoryClass.class, target, MessageOutput.Factory.class);

        if (factoryClass == null) {
            LOG.error("Unable to find an inner class annotated with @FactoryClass in output {}. This output will not be available!", target);
            return;
        }

        installOutput(outputMapBinder, target, factoryClass);
    }

    protected MapBinder<String, FilteredMessageOutput> filteredOutputsMapBinder() {
        return MapBinder.newMapBinder(binder(), String.class, FilteredMessageOutput.class);
    }

    protected Multibinder<PluginPermissions> permissionsBinder() {
        return Multibinder.newSetBinder(binder(), PluginPermissions.class);
    }

    protected void installPermissions(Multibinder<PluginPermissions> classMultibinder,
                                      Class<? extends PluginPermissions> permissionsClass) {
        classMultibinder.addBinding().to(permissionsClass);
    }

    protected Multibinder<PluginAuditEventTypes> auditEventTypesBinder() {
        return Multibinder.newSetBinder(binder(), PluginAuditEventTypes.class);
    }

    protected void installAuditEventTypes(Multibinder<PluginAuditEventTypes> classMultibinder,
                                          Class<? extends PluginAuditEventTypes> auditEventTypesClass) {
        classMultibinder.addBinding().to(auditEventTypesClass);
    }

    protected MapBinder<AuditEventType, AuditEventFormatter> auditEventFormatterMapBinder() {
        return MapBinder.newMapBinder(binder(), AuditEventType.class, AuditEventFormatter.class);
    }

    protected void installAuditEventFormatter(MapBinder<AuditEventType, AuditEventFormatter> auditEventFormatterMapBinder,
                                              AuditEventType auditEventType,
                                              Class<? extends AuditEventFormatter> auditEventFormatter) {
        auditEventFormatterMapBinder.addBinding(auditEventType).to(auditEventFormatter);
    }

    protected OptionalBinder<AuditEventSender> auditEventSenderBinder() {
        return OptionalBinder.newOptionalBinder(binder(), AuditEventSender.class);
    }


    @Nonnull
    protected Multibinder<Class<? extends DynamicFeature>> jerseyDynamicFeatureBinder() {
        return Multibinder.newSetBinder(binder(), new DynamicFeatureType());
    }

    @Nonnull
    protected Multibinder<Class<? extends ContainerResponseFilter>> jerseyContainerResponseFilterBinder() {
        return Multibinder.newSetBinder(binder(), new ContainerResponseFilterType());
    }

    @Nonnull
    protected Multibinder<Class<? extends ExceptionMapper>> jerseyExceptionMapperBinder() {
        return Multibinder.newSetBinder(binder(), new ExceptionMapperType());
    }

    @Nonnull
    protected Multibinder<Class> jerseyAdditionalComponentsBinder() {
        return Multibinder.newSetBinder(binder(), Class.class, Names.named("additionalJerseyComponents"));
    }

    protected Multibinder<Service> serviceBinder() {
        return Multibinder.newSetBinder(binder(), Service.class);
    }

    protected MapBinder<String, PasswordAlgorithm> passwordAlgorithmBinder() {
        return MapBinder.newMapBinder(binder(), String.class, PasswordAlgorithm.class);
    }

    protected MapBinder<String, AuthenticatingRealm> authenticationRealmBinder() {
        return MapBinder.newMapBinder(binder(), String.class, AuthenticatingRealm.class);
    }

    protected MapBinder<String, AuthorizingRealm> authorizationOnlyRealmBinder() {
        return MapBinder.newMapBinder(binder(), String.class, AuthorizingRealm.class);
    }

    protected MapBinder<String, PreflightCheck> preflightChecksBinder() {
        return MapBinder.newMapBinder(binder(), String.class, PreflightCheck.class);
    }

    protected void addPreflightCheck(Class<? extends PreflightCheck> preflightCheck) {
        preflightChecksBinder().addBinding(preflightCheck.getCanonicalName()).to(preflightCheck);
    }

    protected MapBinder<String, SearchResponseDecorator.Factory> searchResponseDecoratorBinder() {
        return MapBinder.newMapBinder(binder(), String.class, SearchResponseDecorator.Factory.class);
    }

    protected void installSearchResponseDecorator(MapBinder<String, SearchResponseDecorator.Factory> searchResponseDecoratorBinder,
                                                  Class<? extends SearchResponseDecorator> searchResponseDecoratorClass,
                                                  Class<? extends SearchResponseDecorator.Factory> searchResponseDecoratorFactoryClass) {
        install(new FactoryModuleBuilder().implement(SearchResponseDecorator.class, searchResponseDecoratorClass).build(searchResponseDecoratorFactoryClass));
        searchResponseDecoratorBinder.addBinding(searchResponseDecoratorClass.getCanonicalName()).to(searchResponseDecoratorFactoryClass);
    }

    protected MapBinder<String, AlertCondition.Factory> alertConditionBinder() {
        return MapBinder.newMapBinder(binder(), String.class, AlertCondition.Factory.class);
    }

    protected void installAlertCondition(MapBinder<String, AlertCondition.Factory> alertConditionBinder,
                                         Class<? extends AlertCondition> alertConditionClass,
                                         Class<? extends AlertCondition.Factory> alertConditionFactoryClass) {
        install(new FactoryModuleBuilder().implement(AlertCondition.class, alertConditionClass).build(alertConditionFactoryClass));
        alertConditionBinder.addBinding(alertConditionClass.getCanonicalName()).to(alertConditionFactoryClass);
    }

    protected void installAlertConditionWithCustomName(MapBinder<String, AlertCondition.Factory> alertConditionBinder,
                                                       String identifier,
                                                       Class<? extends AlertCondition> alertConditionClass,
                                                       Class<? extends AlertCondition.Factory> alertConditionFactoryClass) {
        install(new FactoryModuleBuilder().implement(AlertCondition.class, alertConditionClass).build(alertConditionFactoryClass));
        alertConditionBinder.addBinding(identifier).to(alertConditionFactoryClass);
    }

    protected MapBinder<String, LookupCache.Factory> lookupCacheBinder() {
        return MapBinder.newMapBinder(binder(), String.class, LookupCache.Factory.class);
    }

    protected void installLookupCache(String name,
                                      Class<? extends LookupCache> cacheClass,
                                      Class<? extends LookupCache.Factory> factoryClass,
                                      Class<? extends LookupCacheConfiguration> configClass) {
        install(new FactoryModuleBuilder().implement(LookupCache.class, cacheClass).build(factoryClass));
        lookupCacheBinder().addBinding(name).to(factoryClass);
        registerJacksonSubtype(configClass, name);
    }


    protected MapBinder<String, LookupDataAdapter.Factory> lookupDataAdapterBinder() {
        return MapBinder.newMapBinder(binder(), String.class, LookupDataAdapter.Factory.class);
    }

    protected MapBinder<String, LookupDataAdapter.Factory2> lookupDataAdapterBinder2() {
        return MapBinder.newMapBinder(binder(), String.class, LookupDataAdapter.Factory2.class);
    }

    protected void installLookupDataAdapter(String name,
                                            Class<? extends LookupDataAdapter> adapterClass,
                                            Class<? extends LookupDataAdapter.Factory> factoryClass,
                                            Class<? extends LookupDataAdapterConfiguration> configClass) {
        install(new FactoryModuleBuilder().implement(LookupDataAdapter.class, adapterClass).build(factoryClass));
        lookupDataAdapterBinder().addBinding(name).to(factoryClass);
        registerJacksonSubtype(configClass, name);
    }

    protected void installLookupDataAdapter2(String name,
                                             Class<? extends LookupDataAdapter> adapterClass,
                                             Class<? extends LookupDataAdapter.Factory2> factoryClass,
                                             Class<? extends LookupDataAdapterConfiguration> configClass) {
        install(new FactoryModuleBuilder().implement(LookupDataAdapter.class, adapterClass).build(factoryClass));
        lookupDataAdapterBinder2().addBinding(name).to(factoryClass);
        registerJacksonSubtype(configClass, name);
    }

    /**
     * Prefer using {@link #registerJacksonSubtype(Class)} or {@link #registerJacksonSubtype(Class, String)}.
     */
    protected Multibinder<NamedType> jacksonSubTypesBinder() {
        return Multibinder.newSetBinder(binder(), NamedType.class, JacksonSubTypes.class);
    }

    /**
     * Use this if the class itself is annotated by {@link com.fasterxml.jackson.annotation.JsonTypeName} instead of explicitly given.
     *
     * @param klass
     */
    protected void registerJacksonSubtype(Class<?> klass) {
        registerJacksonSubtype(klass, null);
    }

    /**
     * Use this if the class does not have a {@link com.fasterxml.jackson.annotation.JsonTypeName} annotation.
     *
     * @param klass
     * @param name
     */
    protected void registerJacksonSubtype(Class<?> klass, String name) {
        jacksonSubTypesBinder().addBinding().toInstance(new NamedType(klass, name));
    }

    protected Multibinder<Migration> migrationsBinder() {
        return Multibinder.newSetBinder(binder(), Migration.class);
    }

    protected MapBinder<ModelType, EntityWithExcerptFacade<?, ?>> entityFacadeBinder() {
        return MapBinder.newMapBinder(binder(), new TypeLiteral<ModelType>() {}, new TypeLiteral<EntityWithExcerptFacade<?, ?>>() {});
    }

    protected Multibinder<ConstraintChecker> constraintCheckerBinder() {
        return Multibinder.newSetBinder(binder(), ConstraintChecker.class);
    }

    private static class DynamicFeatureType extends TypeLiteral<Class<? extends DynamicFeature>> {}

    private static class ContainerResponseFilterType extends TypeLiteral<Class<? extends ContainerResponseFilter>> {}

    private static class ExceptionMapperType extends TypeLiteral<Class<? extends ExceptionMapper>> {}

    /**
     * Adds given API resource as a system resource. This should not be used from plugins!
     * Plugins should use {@link org.graylog2.plugin.PluginModule#addRestResource(Class)} instead to ensure the
     * addition of the path prefix.
     *
     * @param restResourceClass the resource to add
     */
    protected void addSystemRestResource(Class<?> restResourceClass) {
        systemRestResourceBinder().addBinding().toInstance(restResourceClass);
    }

    private Multibinder<Class<?>> systemRestResourceBinder() {
        return Multibinder.newSetBinder(
                binder(),
                new TypeLiteral<Class<?>>() {},
                Names.named(SYSTEM_REST_RESOURCES)
        );
    }

    protected MapBinder<Class<?>, ClusterConfigValidator> clusterConfigMapBinder() {
        TypeLiteral<Class<?>> keyType = new TypeLiteral<Class<?>>() {};
        TypeLiteral<ClusterConfigValidator> valueType = new TypeLiteral<ClusterConfigValidator>() {};
        return MapBinder.newMapBinder(binder(), keyType, valueType);
    }

    protected MapBinder<String, ViewResolver> viewResolverBinder() {
        return MapBinder.newMapBinder(binder(),
                TypeLiteral.get(String.class),
                new TypeLiteral<ViewResolver>() {});
    }

    protected void installViewResolver(String name,
                                       Class<? extends ViewResolver> resolverClass) {
        viewResolverBinder().addBinding(name).to(resolverClass).asEagerSingleton();
    }

    protected void addStaticReferencedSearch(StaticReferencedSearch referencedSearch) {
        staticReferencedSearchBinder().addBinding().toInstance(referencedSearch);
    }

    protected Multibinder<StaticReferencedSearch> staticReferencedSearchBinder() {
        return Multibinder.newSetBinder(binder(), StaticReferencedSearch.class);
    }

    protected Multibinder<StreamDeletionGuard> streamDeletionGuardBinder() {
        return Multibinder.newSetBinder(binder(), StreamDeletionGuard.class);
    }

    protected Multibinder<Class<?>> dbEntitiesBinder() {
        return Multibinder.newSetBinder(binder(), new TypeLiteral<>() {}, Names.named(DB_ENTITIES));
    }

    protected Set<Object> getConfigurationBeans() {
        return Set.of();
    }

}
