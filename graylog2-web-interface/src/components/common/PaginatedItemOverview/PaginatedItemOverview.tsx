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
import * as React from 'react';
import { useEffect, useState } from 'react';
import type * as Immutable from 'immutable';

import { NoSearchResult } from 'components/common';
import PaginatedList, { INITIAL_PAGE } from 'components/common/PaginatedList';
import SearchForm from 'components/common/SearchForm';
import Spinner from 'components/common/Spinner';
import type { ListPagination, Pagination } from 'stores/PaginationTypes';

import PaginatedItem from './PaginatedItem';

export type DescriptiveItem = {
  id: string;
  name: string;
  description: string;
};

export type PaginatedListType = {
  list: Immutable.List<DescriptiveItem>;
  pagination: ListPagination;
};

export type ResultsWrapperComponentProps = {
  isEmptyResult: boolean;
  children: React.ReactNode;
};

export type OverrideItemComponentProps = {
  item: DescriptiveItem;
  onDeleteItem: (item: DescriptiveItem) => void;
};

type Props = {
  disableSearchFilter?: boolean;
  noDataText?: string;
  onLoad: (pagination: Pagination, isSubscribed: boolean) => Promise<PaginatedListType>;
  overrideList?: PaginatedListType;
  onDeleteItem?: (descriptiveItem: DescriptiveItem) => void;
  queryHelper?: React.ReactNode;
  resultsWrapperComponent?: React.ComponentType<ResultsWrapperComponentProps>;
  overrideItemComponent?: React.ComponentType<OverrideItemComponentProps>;
};

const pageSizes = [5, 10, 30];
export const DEFAULT_PAGINATION = { page: INITIAL_PAGE, perPage: pageSizes[0], query: '' };

const PaginatedItemOverview = ({
  disableSearchFilter = false,
  onLoad,
  overrideList = undefined,
  onDeleteItem = undefined,
  queryHelper = undefined,
  noDataText = 'No items found to display.',
  resultsWrapperComponent: ResultsWrapperComponent = ({ children }) => <div>{children}</div>,
  overrideItemComponent: OverrideItemComponent = undefined,
}: Props) => {
  const [paginatedList, setPaginatedList] = useState<PaginatedListType | undefined>();
  const [pagination, setPagination] = useState(DEFAULT_PAGINATION);
  useEffect(() => overrideList && setPaginatedList(overrideList), [overrideList]);

  useEffect(() => {
    let isSubscribed = true;

    onLoad(pagination, isSubscribed).then((response) => {
      if (isSubscribed) {
        setPaginatedList(response);
      }
    });

    return () => {
      isSubscribed = false;
    };
  }, [pagination, onLoad]);

  if (!paginatedList) {
    return <Spinner />;
  }

  const emptyResult = <NoSearchResult>{noDataText}</NoSearchResult>;
  let itemList;

  if (paginatedList.list && paginatedList.list.size >= 1) {
    itemList = paginatedList.list.toArray().map((item) => {
      if (OverrideItemComponent) {
        return <OverrideItemComponent key={item.id} onDeleteItem={onDeleteItem} item={item} />;
      }

      return <PaginatedItem key={item.id} onDeleteItem={onDeleteItem} item={item} />;
    });
  }

  return (
    <PaginatedList
      onChange={(newPage, newPerPage) => setPagination({ ...pagination, page: newPage, perPage: newPerPage })}
      pageSize={pagination.perPage}
      totalItems={paginatedList?.pagination?.total ?? 0}
      pageSizes={pageSizes}
      activePage={pagination.page}
      useQueryParameter={false}>
      {!disableSearchFilter && (
        <SearchForm
          onSearch={(newQuery) => setPagination({ ...pagination, page: INITIAL_PAGE, query: newQuery })}
          label="Filter"
          queryWidth={300}
          wrapperClass="has-bm"
          placeholder="Enter query to filter"
          queryHelpComponent={queryHelper}
        />
      )}
      <ResultsWrapperComponent isEmptyResult={!itemList}>{itemList ?? emptyResult}</ResultsWrapperComponent>
    </PaginatedList>
  );
};

export default PaginatedItemOverview;
