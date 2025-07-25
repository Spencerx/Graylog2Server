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
import * as Immutable from 'immutable';

import isDeepEqual from 'stores/isDeepEqual';
import { TIMESTAMP_FIELD } from 'views/Constants';
import isEqualForSearch from 'views/stores/isEqualForSearch';
import type { UnitsConfigJson } from 'views/logic/aggregationbuilder/UnitsConfig';
import UnitsConfig from 'views/logic/aggregationbuilder/UnitsConfig';

import Pivot, { DateType } from './Pivot';
import Series from './Series';
import VisualizationConfig from './visualizations/VisualizationConfig';
import SortConfig from './SortConfig';
import type { SeriesJson } from './Series';
import type { SortConfigJson } from './SortConfig';
import type { VisualizationConfigJson } from './visualizations/VisualizationConfig';
import type { PivotJson } from './Pivot';
import WidgetFormattingSettings from './WidgetFormattingSettings';
import type { WidgetFormattingSettingsJSON } from './WidgetFormattingSettings';

import WidgetConfig from '../widgets/WidgetConfig';

type InternalState = {
  columnPivots: Array<Pivot>;
  formattingSettings: WidgetFormattingSettings;
  rollup: boolean;
  rowPivots: Array<Pivot>;
  series: Array<Series>;
  sort: Array<SortConfig>;
  visualization: string;
  visualizationConfig: VisualizationConfig | undefined | null;
  eventAnnotation: boolean;
  units: UnitsConfig;
};

export type AggregationWidgetConfigJson = {
  column_pivots: Array<PivotJson>;
  formatting_settings: WidgetFormattingSettingsJSON;
  rollup: boolean;
  row_pivots: Array<PivotJson>;
  series: Array<SeriesJson>;
  sort: Array<SortConfigJson>;
  visualization: string;
  visualization_config: VisualizationConfigJson;
  event_annotation: boolean;
  units: UnitsConfigJson;
};

export default class AggregationWidgetConfig extends WidgetConfig {
  _value: InternalState;

  constructor(
    columnPivots: Array<Pivot>,
    rowPivots: Array<Pivot>,
    series: Array<Series>,
    sort: Array<SortConfig>,
    visualization: string,
    rollup: boolean,
    visualizationConfig: VisualizationConfig,
    formattingSettings: WidgetFormattingSettings,
    eventAnnotation: boolean = false,
    units: UnitsConfig = UnitsConfig.empty(),
  ) {
    super();
    this._value = {
      columnPivots,
      rowPivots,
      series,
      sort,
      visualization,
      rollup,
      visualizationConfig,
      formattingSettings,
      eventAnnotation,
      units,
    };
  }

  get rowPivots() {
    return this._value.rowPivots;
  }

  get columnPivots() {
    return this._value.columnPivots;
  }

  get series() {
    return this._value.series;
  }

  get sort() {
    return this._value.sort;
  }

  get rollup() {
    return this._value.rollup;
  }

  get visualization() {
    return this._value.visualization;
  }

  get visualizationConfig() {
    if (this._value.visualizationConfig === null) {
      return undefined;
    }

    return this._value.visualizationConfig;
  }

  get formattingSettings() {
    return this._value.formattingSettings;
  }

  get eventAnnotation() {
    return this._value.eventAnnotation;
  }

  get isTimeline() {
    return (
      this.rowPivots &&
      this.rowPivots.length === 1 &&
      this.rowPivots[0].type === DateType &&
      this.rowPivots[0].fields?.[0] === TIMESTAMP_FIELD
    );
  }

  get isEmpty(): boolean {
    const empty = (arr: Array<unknown>) => !arr.length;

    return empty(this.rowPivots) && empty(this.columnPivots) && empty(this.series);
  }

  get rollupForBackendQuery(): boolean {
    return this.columnPivots.length > 0 ? this.rollup : true;
  }

  get units() {
    return this._value.units;
  }

  static builder() {
    // eslint-disable-next-line @typescript-eslint/no-use-before-define
    return new Builder()
      .rowPivots([])
      .columnPivots([])
      .series([])
      .sort([])
      .eventAnnotation(false)
      .units(UnitsConfig.empty())
      .rollup(false);
  }

  toBuilder() {
    // eslint-disable-next-line @typescript-eslint/no-use-before-define
    return new Builder(Immutable.Map<keyof InternalState, any>(this._value));
  }

  toJSON() {
    const {
      columnPivots,
      formattingSettings,
      rollup,
      rowPivots,
      series,
      sort,
      visualization,
      visualizationConfig,
      eventAnnotation,
      units,
    } = this._value;

    return {
      column_pivots: columnPivots,
      formatting_settings: formattingSettings,
      rollup,
      row_pivots: rowPivots,
      series,
      sort,
      visualization,
      visualization_config: visualizationConfig,
      event_annotation: eventAnnotation,
      units,
    };
  }

  equals(other: any) {
    if (other instanceof AggregationWidgetConfig) {
      return [
        'columnPivots',
        'rowPivots',
        'series',
        'sort',
        'rollup',
        'eventAnnotation',
        'visualizationConfig',
        'visualization',
        'formattingSettings',
        'units',
      ].every((key) => isDeepEqual(this[key], other[key]));
    }

    return false;
  }

  equalsForSearch(other: any) {
    if (other instanceof AggregationWidgetConfig) {
      return ['rowPivots', 'columnPivots', 'series', 'sort', 'rollup', 'eventAnnotation', 'visualizationConfig'].every(
        (key) => isEqualForSearch(this[key], other[key]),
      );
    }

    return false;
  }

  static fromJSON(value: AggregationWidgetConfigJson) {
    const {
      column_pivots,
      formatting_settings,
      rollup,
      row_pivots,
      series,
      sort,
      visualization,
      visualization_config,
      event_annotation,
      units,
    } = value;

    // eslint-disable-next-line @typescript-eslint/no-use-before-define
    return new Builder()
      .columnPivots(column_pivots.map(Pivot.fromJSON))
      .rowPivots(row_pivots.map(Pivot.fromJSON))
      .series(series.map(Series.fromJSON))
      .sort(sort.map(SortConfig.fromJSON))
      .visualization(visualization)
      .rollup(rollup)
      .visualizationConfig(
        visualization_config ? VisualizationConfig.fromJSON(visualization, visualization_config) : null,
      )
      .formattingSettings(formatting_settings ? WidgetFormattingSettings.fromJSON(formatting_settings) : undefined)
      .eventAnnotation(event_annotation)
      .units(UnitsConfig.fromJSON(units))
      .build();
  }
}

type BuilderState = Immutable.Map<keyof AggregationWidgetConfig, any>;

class Builder {
  value: BuilderState;

  constructor(value: BuilderState = Immutable.Map()) {
    this.value = value;
  }

  columnPivots(pivots: Array<Pivot>) {
    return new Builder(this.value.set('columnPivots', pivots));
  }

  rowPivots(pivots: Array<Pivot>) {
    return new Builder(this.value.set('rowPivots', pivots));
  }

  series(series: Array<Series>) {
    return new Builder(this.value.set('series', series));
  }

  sort(sorts: Array<SortConfig>) {
    return new Builder(this.value.set('sort', sorts));
  }

  visualization(type: string) {
    return new Builder(this.value.set('visualization', type));
  }

  visualizationConfig(config: VisualizationConfig | undefined | null) {
    return new Builder(this.value.set('visualizationConfig', config));
  }

  rollup(rollup: boolean) {
    return new Builder(this.value.set('rollup', rollup));
  }

  formattingSettings(value: WidgetFormattingSettings | undefined | null) {
    return new Builder(this.value.set('formattingSettings', value));
  }

  eventAnnotation(value: boolean) {
    return new Builder(this.value.set('eventAnnotation', value));
  }

  units(value: UnitsConfig) {
    return new Builder(this.value.set('units', value));
  }

  build() {
    const {
      rowPivots,
      columnPivots,
      series,
      sort,
      visualization,
      rollup,
      visualizationConfig,
      formattingSettings,
      eventAnnotation,
      units,
    } = this.value.toObject();

    const availableSorts = [].concat(rowPivots, columnPivots, series);
    const filteredSorts = sort.filter((s) =>
      availableSorts.find(
        (availableSort) => s.field === availableSort.function || availableSort.fields?.includes(s.field),
      ),
    );

    return new AggregationWidgetConfig(
      columnPivots,
      rowPivots,
      series,
      filteredSorts,
      visualization,
      rollup,
      visualizationConfig,
      formattingSettings,
      eventAnnotation,
      units,
    );
  }
}

export type AggregationWidgetConfigBuilder = InstanceType<typeof Builder>;
