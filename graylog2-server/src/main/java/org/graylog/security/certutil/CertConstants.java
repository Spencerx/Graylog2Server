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
package org.graylog.security.certutil;

/**
 * Place to store constants that are not a subject of user/client configuration.
 */
public interface CertConstants {
    String KEY_GENERATION_ALGORITHM = "RSA";
    String SIGNING_ALGORITHM = "SHA256withRSA";
    String PKCS12 = "PKCS12";
    String CA_KEY_ALIAS = "ca";
}
