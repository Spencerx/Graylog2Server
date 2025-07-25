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
package org.graylog.security;

import com.google.auto.value.AutoValue;
import com.google.common.collect.SetMultimap;
import org.graylog.grn.GRNType;
import org.graylog2.plugin.security.Permission;

import java.util.Set;

@AutoValue
public abstract class CapabilityDescriptor {
    public abstract Capability capability();

    public abstract String title();

    public abstract SetMultimap<GRNType, Permission> permissions();

    public Set<Permission> permissionsFor(GRNType grnType) {
        return permissions().get(grnType);
    }

    public static Builder builder() {
        return new AutoValue_CapabilityDescriptor.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder capability(Capability capability);

        public abstract Builder title(String title);

        public abstract Builder permissions(SetMultimap<GRNType, Permission> permissions);

        public abstract CapabilityDescriptor build();
    }
}
