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
import { useContext } from 'react';

export type RuleBuilderContextType = {
  useHighlightedOutput: [string, React.Dispatch<React.SetStateAction<string>>];
};

const RuleBuilderContext = React.createContext<RuleBuilderContextType | null>(null);

const useRuleBuilder = (): RuleBuilderContextType => {
  try {
    const context = useContext(RuleBuilderContext);

    if (!context) {
      throw new Error('useRuleBuilder must be used within a RuleBuilderProvider');
    }

    return context;
  } catch {
    return { useHighlightedOutput: ['', () => {}] };
  }
};

export { useRuleBuilder };
export default RuleBuilderContext;
