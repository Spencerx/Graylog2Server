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
import Reflux from 'reflux';

import fetch from 'logic/rest/FetchProvider';
import UserNotification from 'util/UserNotification';
import { qualifyUrl } from 'util/URLUtils';
import type { RefluxActions } from 'stores/StoreTypes';
import type View from 'views/logic/views/View';
import type Parameter from 'views/logic/parameters/Parameter';
import type { ViewJson } from 'views/logic/views/View';
import { singletonActions, singletonStore } from 'logic/singleton';
import type { Pagination, Attribute } from 'stores/PaginationTypes';
import { CurrentUserStore } from 'stores/users/CurrentUserStore';
import type { EntitySharePayload } from 'actions/permissions/EntityShareActions';

export type SortOrder = 'asc' | 'desc';

export type PaginatedViews = {
  pagination: {
    total: number;
    page: number;
    perPage: number;
    count: number;
  };
  list: Array<View>;
  attributes: Array<Attribute>;
};

export type ViewSummary = {
  id: string;
  title: string;
  description: string;
  summary: string;
  parameters: Array<Parameter>;
};

export type ViewSummaries = Array<ViewSummary>;

type ViewManagementActionsType = RefluxActions<{
  create: (view: View, entityShare?: EntitySharePayload) => Promise<View>;
  delete: (view: View) => Promise<View>;
  forValue: () => Promise<ViewSummaries>;
  get: (viewId: string) => Promise<ViewJson>;
  search: (
    query: string,
    page?: number,
    perPage?: number,
    sortBy?: string,
    sortOrder?: SortOrder,
  ) => Promise<PaginatedViews>;
  update: (view: View, entityShare?: EntitySharePayload) => Promise<View>;
}>;

const ViewManagementActions: ViewManagementActionsType = singletonActions('views.ViewManagement', () =>
  Reflux.createActions({
    create: { asyncResult: true },
    delete: { asyncResult: true },
    forValue: { asyncResult: true },
    get: { asyncResult: true },
    search: { asyncResult: true },
    update: { asyncResult: true },
  }),
);

const viewsUrl = qualifyUrl('/views');
const viewsIdUrl = (id) => qualifyUrl(`/views/${id}`);
const forValueUrl = () => qualifyUrl('/views/forValue');

type ViewManagementStoreState = {
  pagination: Pagination;
  list: Array<ViewJson>;
};

const ViewManagementStore = singletonStore('views.ViewManagement', () =>
  Reflux.createStore<ViewManagementStoreState>({
    listenables: [ViewManagementActions],

    views: undefined,
    pagination: {
      total: 0,
      count: 0,
      page: 1,
      perPage: 10,
    },

    getInitialState() {
      return {
        pagination: this.pagination,
        list: this.views,
      };
    },

    get(viewId: string): Promise<ViewJson> {
      const promise = fetch('GET', `${viewsUrl}/${viewId}`);

      ViewManagementActions.get.promise(promise);

      return promise;
    },

    create(view: View, entityShare?: EntitySharePayload): Promise<View> {
      const promise = fetch('POST', viewsUrl, JSON.stringify({ entity: view.toJSON(), share_request: entityShare }));

      ViewManagementActions.create.promise(promise);

      return promise;
    },

    createCompleted(): Promise<void> {
      return CurrentUserStore.reload();
    },

    update(view: View, entityShare?: EntitySharePayload): Promise<View> {
      const promise = fetch(
        'PUT',
        viewsIdUrl(view.id),
        JSON.stringify({ entity: view.toJSON(), share_request: entityShare }),
      );

      ViewManagementActions.update.promise(promise);

      return promise;
    },

    search(query, page = 1, perPage = 10, sortBy = 'title', order = 'asc') {
      const promise = fetch(
        'GET',
        `${viewsUrl}?query=${query}&page=${page}&per_page=${perPage}&sort=${sortBy}&order=${order}`,
      )
        .then((response) => {
          this.views = response.views;

          this.pagination = {
            total: response.total,
            count: response.count,
            page: response.page,
            perPage: response.per_page,
          };

          this.trigger({
            list: this.views,
            pagination: this.pagination,
          });

          return response;
        })
        .catch((error) => {
          UserNotification.error(`Fetching views failed with status: ${error}`, 'Could not retrieve views');
        });

      ViewManagementActions.search.promise(promise);
    },

    delete(view) {
      const promise = fetch('DELETE', viewsIdUrl(view.id)).catch((error) => {
        UserNotification.error(`Deleting view ${view.title} failed with status: ${error}`, 'Could not delete view');
      });

      ViewManagementActions.delete.promise(promise);
    },

    forValue() {
      const promise = fetch('POST', forValueUrl()).catch((error) =>
        UserNotification.error(
          `Finding matching views for value failed with status: ${error}`,
          'Could not find matching views',
        ),
      );

      ViewManagementActions.forValue.promise(promise);
    },
  }),
);

export { ViewManagementStore, ViewManagementActions };
