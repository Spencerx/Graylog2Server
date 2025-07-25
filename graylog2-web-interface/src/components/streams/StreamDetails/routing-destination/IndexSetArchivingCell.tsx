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
import styled from 'styled-components';
import { PluginStore } from 'graylog-web-plugin/plugin';

import { Tooltip, StatusIcon } from 'components/common';

type Props = {
  isArchivingEnabled: boolean;
  streamId: string;
};

const StyledDiv = styled.div`
  display: flex;
`;

const IndexSetArchivingCell = ({ isArchivingEnabled, streamId }: Props) => {
  const StreamIndexSetDataLakeWarning = PluginStore.exports('dataLake')?.[0]?.StreamIndexSetDataLakeWarning;

  return (
    <StyledDiv>
      <Tooltip withArrow position="right" label={`Archiving is ${isArchivingEnabled ? 'enabled' : 'disabled'}`}>
        <StatusIcon active={isArchivingEnabled} />
      </Tooltip>
      {StreamIndexSetDataLakeWarning && (
        <StreamIndexSetDataLakeWarning streamId={streamId} isArchivingEnabled={isArchivingEnabled} />
      )}
    </StyledDiv>
  );
};

export default IndexSetArchivingCell;
