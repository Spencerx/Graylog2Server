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

import { LinkContainer } from 'components/common/router';
import { Col, Row, Button } from 'components/bootstrap';
import { DataTable, PaginatedList, SearchForm } from 'components/common';
import Routes from 'routing/Routes';
import type { Collector } from 'components/sidecars/types';

import CollectorRow from './CollectorRow';
import style from './CollectorList.css';

type CollectorListProps = {
  collectors: Array<Collector>;
  pagination: {
    page: number;
    pageSize: number;
    total: number;
  };
  query: string;
  total: number;
  onClone: (collector: string, name: string, callback: () => void) => void;
  onDelete: (collector: Collector) => void;
  onPageChange: (page: number, pageSize: number) => void;
  onQueryChange: () => void;
  validateCollector: (collector: Collector) => Promise<{ errors: { name: string[] } }>;
};

const headerCellFormatter = (header: React.ReactNode) => {
  const className = header === 'Actions' ? style.actionsColumn : '';

  return <th className={className}>{header}</th>;
};

class CollectorList extends React.Component<CollectorListProps> {
  collectorFormatter = (collector: Collector) => {
    const { onClone, onDelete, validateCollector } = this.props;

    return (
      <CollectorRow collector={collector} onClone={onClone} onDelete={onDelete} validateCollector={validateCollector} />
    );
  };

  render() {
    const { collectors, pagination, query, total, onPageChange, onQueryChange } = this.props;

    const headers = ['Name', 'Operating System', 'Actions'];

    return (
      <div>
        <Row>
          <Col md={12}>
            <div className="pull-right">
              <LinkContainer to={Routes.SYSTEM.SIDECARS.NEW_COLLECTOR}>
                <Button bsStyle="success" bsSize="small">
                  Create Log Collector
                </Button>
              </LinkContainer>
            </div>
            <h2>
              Log Collectors <small>{total} total</small>
            </h2>
          </Col>
          <Col md={12}>
            <p>Manage Log Collectors that you can configure and supervise through sidecars and the web interface.</p>
          </Col>
        </Row>

        <Row className={`row-sm ${style.collectorRow}`}>
          <Col md={12}>
            <SearchForm
              query={query}
              onSearch={onQueryChange}
              onReset={onQueryChange}
              placeholder="Find collectors"
              wrapperClass={style.inline}
              topMargin={0}
              useLoadingState
            />

            <PaginatedList
              activePage={pagination.page}
              pageSize={pagination.pageSize}
              pageSizes={[10, 25]}
              totalItems={pagination.total}
              onChange={onPageChange}
              useQueryParameter={false}>
              <div className={style.collectorTable}>
                <DataTable
                  id="collector-list"
                  className="table-hover"
                  headers={headers}
                  headerCellFormatter={headerCellFormatter}
                  rows={collectors}
                  dataRowFormatter={this.collectorFormatter}
                  noDataText="There are no log collectors to display, why don't you create one?"
                  filterLabel=""
                  filterKeys={[]}
                  useResponsiveTable={false}
                />
              </div>
            </PaginatedList>
          </Col>
        </Row>
      </div>
    );
  }
}

export default CollectorList;
