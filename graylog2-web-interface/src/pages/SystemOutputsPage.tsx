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

import { DocumentTitle, PageHeader } from 'components/common';
import OutputsComponent from 'components/outputs/OutputsComponent';
import useCurrentUser from 'hooks/useCurrentUser';
import useProductName from 'brand-customization/useProductName';
import MarketplaceLink from 'components/support/MarketplaceLink';

const SystemOutputsPage = () => {
  const currentUser = useCurrentUser();
  const productName = useProductName();

  return (
    <DocumentTitle title="Outputs">
      <span>
        <PageHeader title="Outputs in Cluster">
          <span>
            {productName} nodes can forward messages via outputs. Launch or terminate as many outputs as you want here{' '}
            <strong>and then assign them to streams to forward all messages of a stream in real-time.</strong>
            <br />
            <MarketplaceLink prefix="You can find output plugins in" />
          </span>
        </PageHeader>

        <OutputsComponent permissions={currentUser.permissions} />
      </span>
    </DocumentTitle>
  );
};

export default SystemOutputsPage;
