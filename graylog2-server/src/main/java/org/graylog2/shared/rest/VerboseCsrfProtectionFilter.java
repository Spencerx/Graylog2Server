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
package org.graylog2.shared.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import org.glassfish.jersey.server.filter.CsrfProtectionFilter;

import java.io.IOException;

public class VerboseCsrfProtectionFilter extends CsrfProtectionFilter {

    private final ResourceInfo resourceInfo;

    @Inject
    public VerboseCsrfProtectionFilter(@Context ResourceInfo resourceInfo) {
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext rc) throws IOException {
        try {
            // Backward compatibility for Sidecars < 0.1.7
            if (!rc.getHeaders().containsKey("X-Graylog-Collector-Version")) {
                super.filter(rc);
            }
        } catch (BadRequestException badRequestException) {
            // Resource methods can be annotated to allow bypassing CSRF protection. We don't want to execute the
            // annotation check for every request. There should be very few resources that are annotated with the skip
            // annotation, so we only execute the check if the filter would reject the request.
            final var resourceMethod = resourceInfo.getResourceMethod();
            if (resourceMethod != null && resourceMethod.isAnnotationPresent(SkipCSRFProtection.class)) {
                return;
            }

            throw new BadRequestException(
                    "CSRF protection header is missing. Please add a \"" + HEADER_NAME + "\" header to your request.",
                    badRequestException
            );
        }
    }
}
