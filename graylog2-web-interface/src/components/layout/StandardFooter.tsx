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
import React, { useEffect, useState } from 'react';

import { getFullVersion } from 'util/Version';
import connect from 'stores/connect';
import type { Store } from 'stores/StoreTypes';
import { SystemStore } from 'stores/system/SystemStore';
import useProductName from 'brand-customization/useProductName';

type SystemStoreState = {
  system: {
    version?: string;
    hostname?: string;
  };
};

type Jvm = {
  info: string;
};

type Props = {
  system?: {
    version?: string;
    hostname?: string;
  };
};

const StandardFooter = ({ system = undefined }: Props) => {
  const productName = useProductName();
  const [jvm, setJvm] = useState<Jvm | undefined>();

  useEffect(() => {
    let mounted = true;

    SystemStore.jvm().then((jvmInfo) => {
      if (mounted) {
        setJvm(jvmInfo);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  if (!(system && jvm)) {
    return (
      <>
        {productName} {getFullVersion()}
      </>
    );
  }

  return (
    <>
      {productName} {system.version} on {system.hostname} ({jvm.info})
    </>
  );
};

export default connect(
  StandardFooter,
  { system: SystemStore as Store<SystemStoreState> },
  ({ system: { system } = { system: undefined } }) => ({ system }),
);
