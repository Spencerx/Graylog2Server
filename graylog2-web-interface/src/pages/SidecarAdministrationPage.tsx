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

import { Col, Row } from 'components/bootstrap';
import DocsHelper from 'util/DocsHelper';
import { DocumentTitle, PageHeader } from 'components/common';
import CollectorsAdministrationContainer from 'components/sidecars/administration/CollectorsAdministrationContainer';
import SidecarsPageNavigation from 'components/sidecars/common/SidecarsPageNavigation';
import useQuery from 'routing/useQuery';

const SidecarAdministrationPage = () => {
  const { node_id: nodeId } = useQuery();

  return (
    <DocumentTitle title="Collectors Administration">
      <SidecarsPageNavigation />
      <PageHeader
        title="Collectors Administration"
        documentationLink={{
          title: 'Sidecar documentation',
          path: DocsHelper.PAGES.COLLECTOR_SIDECAR,
        }}>
        <span>Collectors can reliably forward contents of log files or Windows EventLog from your servers.</span>
      </PageHeader>

      <Row className="content">
        <Col md={12}>
          <CollectorsAdministrationContainer nodeId={nodeId as string} />
        </Col>
      </Row>
    </DocumentTitle>
  );
};

export default SidecarAdministrationPage;
