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
import { useMemo, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import type { ColumnRenderers } from 'components/common/EntityDataTable';
import { QueryHelper, PaginatedEntityTable } from 'components/common';
import type { EventNotification, TestResults } from 'stores/event-notifications/EventNotificationsStore';
import useSendTelemetry from 'logic/telemetry/useSendTelemetry';
import { TELEMETRY_EVENT_TYPE } from 'logic/telemetry/Constants';
import { getPathnameWithoutId } from 'util/URLUtils';
import useLocation from 'routing/useLocation';
import type { ColumnRenderersByAttribute } from 'components/common/EntityDataTable/types';
import usePluggableEntityTableElements from 'hooks/usePluggableEntityTableElements';

import NotificationConfigTypeCell from './NotificationConfigTypeCell';
import NotificationTitle from './NotificationTitle';
import EventNotificationActions from './EventNotificationActions';
import BulkActions from './BulkActions';
import getEventNotificationTableElements from './Constants';

import { keyFn, fetchEventNotifications } from '../hooks/useEventNotifications';
import useNotificationTest from '../hooks/useNotificationTest';

const customColumnRenderers = (
  testResults: TestResults,
  pluggableColumnRenderers?: ColumnRenderersByAttribute<EventNotification>,
): ColumnRenderers<EventNotification> => ({
  attributes: {
    title: {
      renderCell: (_title: string, notification) => (
        <NotificationTitle notification={notification} testResults={testResults} />
      ),
    },
    type: {
      renderCell: (_type: string, notification) => <NotificationConfigTypeCell notification={notification} />,
    },
    ...(pluggableColumnRenderers || {}),
  },
});

const EventNotificationsContainer = () => {
  const { isLoadingTest, testResults, getNotificationTest } = useNotificationTest();
  const sendTelemetry = useSendTelemetry();
  const { pathname } = useLocation();
  const { pluggableColumnRenderers, pluggableAttributes, pluggableExpandedSections } =
    usePluggableEntityTableElements<EventNotification>(null, 'notification');

  const { defaultLayout, columnOrder, additionalAttributes } = getEventNotificationTableElements(pluggableAttributes);
  const columnRenderers = useMemo(
    () => customColumnRenderers(testResults, pluggableColumnRenderers),
    [testResults, pluggableColumnRenderers],
  );
  const queryClient = useQueryClient();

  const handleTest = useCallback(
    (notification: EventNotification) => {
      sendTelemetry(TELEMETRY_EVENT_TYPE.NOTIFICATIONS.ROW_ACTION_TEST_CLICKED, {
        app_pathname: getPathnameWithoutId(pathname),
        app_section: 'event-notification',
        app_action_value: 'notification-test',
      });

      getNotificationTest(notification);
      queryClient.invalidateQueries({ queryKey: keyFn() });
    },
    [getNotificationTest, pathname, queryClient, sendTelemetry],
  );
  const expandedSections = useMemo(
    () => ({
      ...pluggableExpandedSections,
    }),
    [pluggableExpandedSections],
  );

  const renderEvenNotificationActions = useCallback(
    (listItem: EventNotification) => (
      <EventNotificationActions notification={listItem} isTestLoading={isLoadingTest} onTest={handleTest} />
    ),
    [handleTest, isLoadingTest],
  );

  return (
    <PaginatedEntityTable<EventNotification>
      humanName="event notifications"
      columnsOrder={columnOrder}
      queryHelpComponent={<QueryHelper entityName="notification" />}
      entityActions={renderEvenNotificationActions}
      tableLayout={defaultLayout}
      fetchEntities={fetchEventNotifications}
      additionalAttributes={additionalAttributes}
      expandedSectionsRenderer={expandedSections}
      keyFn={keyFn}
      bulkSelection={{ actions: <BulkActions /> }}
      entityAttributesAreCamelCase
      columnRenderers={columnRenderers}
    />
  );
};

export default EventNotificationsContainer;
