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
import React from 'react';

const EventKeyHelpPopover = () => (
  <>
    Event Keys are Fields used to arrange Events into groups. A group is created for each unique Key, resulting in as
    many Events as unique Keys are found. Example:
    <p />
    <b>No Event Keys:</b> One Event for each <em>Login failure</em> message.
    <br />
    <b>
      Event Key <code>username</code>:
    </b>{' '}
    One Event for each username with a <em>Login failure</em> message.
  </>
);

export default EventKeyHelpPopover;
