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
import React, { useContext, useEffect, useState, useCallback } from 'react';
import styled from 'styled-components';

import { Button, Panel, Input } from 'components/bootstrap';
import FormWrap from 'integrations/aws/common/FormWrap';
import SkipHealthCheck from 'integrations/aws/common/SkipHealthCheck';
import useFetch from 'integrations/aws/common/hooks/useFetch';
import { ApiRoutes } from 'integrations/aws/common/Routes';
import Countdown from 'integrations/aws/common/Countdown';
import { KINESIS_LOG_TYPES } from 'integrations/aws/common/constants';
import { ApiContext } from 'integrations/aws/context/Api';
import { FormDataContext } from 'integrations/aws/context/FormData';
import Icon from 'components/common/Icon';

const Notice = styled.span`
  display: flex;
  align-items: center;

  > span {
    margin-left: 6px;
  }
`;

const CheckAgain = styled.p`
  display: flex;
  align-items: center;

  > strong {
    margin-right: 9px;
  }
`;

type StepHealthCheckProps = {
  onSubmit: (...args: any[]) => void;
  onChange: (...args: any[]) => void;
};

const StepHealthCheck = ({ onChange, onSubmit }: StepHealthCheckProps) => {
  const { logData, setLogData } = useContext(ApiContext);
  const { formData } = useContext(FormDataContext);
  const [pauseCountdown, setPauseCountdown] = useState(false);

  const [fetchStreamArnStatus, setStreamArnFetch] = useFetch(
    null,
    (response) => {
      onChange({ target: { name: 'awsCloudwatchKinesisStreamArn', value: response.result } });
    },
    'POST',
    {
      region: formData.awsCloudWatchAwsRegion.value,
      stream_name: formData.awsCloudWatchKinesisStream.value,
    },
  );

  useEffect(() => {
    setStreamArnFetch(ApiRoutes.INTEGRATIONS.AWS.KINESIS.STREAM_ARN);
  }, [setStreamArnFetch]);

  useEffect(() => {
    if (fetchStreamArnStatus.error) {
      // Proceed even if fetching the stream ARN fails — failure is non-blocking and intentionally ignored
    }
  }, [fetchStreamArnStatus]);

  const [logDataProgress, setLogDataUrl] = useFetch(
    null,
    (response) => {
      setLogData(response);
      onChange({ target: { name: 'awsCloudWatchKinesisInputType', value: response.type } });
    },
    'POST',
    {
      region: formData.awsCloudWatchAwsRegion.value,
      stream_name: formData.awsCloudWatchKinesisStream.value,
    },
  );

  const checkForLogs = useCallback(() => {
    setPauseCountdown(true);
    setLogDataUrl(ApiRoutes.INTEGRATIONS.AWS.KINESIS.HEALTH_CHECK);
  }, [setPauseCountdown, setLogDataUrl]);

  useEffect(() => {
    if (!logData) {
      checkForLogs();
    }
  }, [checkForLogs, logData]);

  useEffect(() => {
    if (!logDataProgress.loading && !logDataProgress.data) {
      setPauseCountdown(false);
      setLogDataUrl(null);
    }
  }, [logDataProgress.data, logDataProgress.loading, setLogDataUrl]);

  if (!logData) {
    return (
      <Panel
        bsStyle="warning"
        header={
          <Notice>
            <Icon name="warning" size="2x" />
            <span>We haven&apos;t received a response back from Amazon yet.</span>
          </Notice>
        }>
        <p>
          Hang out for a few moments while we keep checking your AWS stream for logs. Amazon&apos;s servers parse logs
          every 10 minutes, so grab a cup of coffee because this may take some time!
        </p>

        <CheckAgain>
          <strong>
            Checking again in: <Countdown timeInSeconds={120} callback={checkForLogs} paused={pauseCountdown} />
          </strong>

          <Button type="button" bsStyle="success" bsSize="sm" onClick={checkForLogs} disabled={logDataProgress.loading}>
            {logDataProgress.loading ? 'Checking...' : 'Check Now'}
          </Button>
        </CheckAgain>

        <p>
          <em>
            Do not refresh your browser, we are continually checking for your logs and this page will automatically
            refresh when your logs are available.
          </em>
        </p>

        <div>
          <SkipHealthCheck onSubmit={onSubmit} onChange={onChange} />
        </div>
      </Panel>
    );
  }
  const handleSubmit = () => {
    onSubmit();
    onChange({ target: { name: 'awsCloudWatchKinesisInputType', value: logData.type } });
  };
  const logTypeLabel = KINESIS_LOG_TYPES.find((type) => type.value === logData.type).label;

  return (
    <FormWrap
      onSubmit={handleSubmit}
      buttonContent="Review &amp; Finalize"
      disabled={false}
      title="Create Kinesis Stream"
      description={<p>If available, a parsed sample {logTypeLabel} message from the stream will be shown below.</p>}>
      <Input
        id="awsCloudWatchLog"
        type="textarea"
        label="Formatted Log Message"
        value={logData.message || 'No messages found in stream.'}
        rows={10}
        disabled
      />
    </FormWrap>
  );
};

export default StepHealthCheck;
