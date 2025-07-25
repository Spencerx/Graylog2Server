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
import styled, { css } from 'styled-components';
import type { DefaultTheme } from 'styled-components';

import { INPUT_BORDER_RADIUS } from 'theme/constants';

import AceEditor from './ace';

type Props = {
  $height: number;
  $scTheme: DefaultTheme;
  disabled: boolean;
};

const StyledAceEditor = styled(AceEditor)<Props>(
  ({ $scTheme, $height, disabled }) => css`
    &.ace-queryinput {
      ${$height ? `height: ${$height}px !important` : ''}
      min-height: 34px;
      width: 100% !important;
      background-color: ${$scTheme.colors.input.background};
      color: ${$scTheme.utils.contrastingColor($scTheme.colors.input.background, 'AAA')};
      border: 1px solid ${$scTheme.colors.input.border};
      border-radius: ${INPUT_BORDER_RADIUS};

      &.ace_multiselect .ace_selection.ace_start {
        box-shadow: 0 0 3px 0 ${$scTheme.colors.input.background};
      }

      .ace_gutter {
        background: ${$scTheme.colors.input.background};
        color: ${$scTheme.utils.contrastingColor($scTheme.colors.input.background, 'AAA')};
      }

      .ace_print-margin {
        width: 1px;
        background: ${$scTheme.colors.input.background};
      }

      .ace_cursor {
        color: ${$scTheme.colors.gray[50]};
        display: ${disabled ? 'none' : 'block'} !important;
      }

      .ace_marker-layer .ace_selection {
        background: Highlight;
        border-radius: 0;
      }

      .ace_marker-layer .ace_step {
        background: ${$scTheme.colors.variant.warning};
      }

      .ace_marker-layer .ace_bracket {
        margin: -1px 0 0 -1px;
        border: none;
      }

      .ace_marker-layer .ace_active-line {
        background: ${$scTheme.colors.input.background};
      }

      .ace_gutter-active-line {
        background-color: ${$scTheme.colors.input.background};
      }

      .ace_marker-layer .ace_selected-word {
        border: 1px solid ${$scTheme.colors.gray[80]};
        background: transparent;
      }

      .ace_invisible {
        color: ${$scTheme.colors.input.background};
      }

      .ace_keyword,
      .ace_meta,
      .ace_storage,
      .ace_storage.ace_type,
      .ace_support.ace_type {
        color: ${$scTheme.colors.variant.info};
      }

      .ace_keyword.ace_operator {
        color: ${$scTheme.colors.text.primary};
      }

      .ace_constant.ace_character,
      .ace_constant.ace_language,
      .ace_constant.ace_numeric,
      .ace_keyword.ace_other.ace_unit,
      .ace_support.ace_constant,
      .ace_variable.ace_parameter {
        color: ${$scTheme.colors.variant.dark.danger};
      }

      .ace_constant.ace_other {
        color: ${$scTheme.colors.variant.default};
      }

      .ace_invalid {
        color: ${$scTheme.utils.readableColor($scTheme.colors.brand.primary)};
        background-color: ${$scTheme.colors.brand.primary};
      }

      .ace_invalid.ace_deprecated {
        color: ${$scTheme.utils.readableColor($scTheme.colors.brand.primary)};
        background-color: ${$scTheme.colors.variant.dark.primary};
      }

      .ace_fold {
        background-color: ${$scTheme.colors.variant.info};
        border-color: ${$scTheme.utils.contrastingColor($scTheme.colors.input.background, 'AAA')};
      }

      .ace_entity.ace_name.ace_function,
      .ace_support.ace_function,
      .ace_variable,
      .ace_term {
        color: ${$scTheme.colors.variant.darker.info};
      }

      .ace_support.ace_class,
      .ace_support.ace_type {
        color: ${$scTheme.colors.variant.dark.warning};
      }

      .ace_heading,
      .ace_markup.ace_heading,
      .ace_string {
        color: ${$scTheme.colors.variant.dark.success};
      }

      .ace_entity.ace_name.ace_tag,
      .ace_entity.ace_other.ace_attribute-name,
      .ace_meta.ace_tag,
      .ace_string.ace_regexp,
      .ace_variable {
        color: ${$scTheme.colors.brand.primary};
      }

      .ace_comment {
        color: ${$scTheme.colors.gray[60]};
      }

      .ace_indent-guide {
        background: url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAE0lEQVQImWP4////f4bdu3f/BwAlfgctduB85QAAAABJRU5ErkJggg==')
          right repeat-y;
      }

      .ace_placeholder {
        left: 0;
        right: 0;
        padding: 0;
        margin-top: 6px;
        margin-left: 4px;
        margin-right: 4px;
        transform: none;
        opacity: 1;
        z-index: auto !important;
        font-family: ${$scTheme.fonts.family.monospace} !important;
        font-size: ${$scTheme.fonts.size.body};
        color: ${$scTheme.colors.input.placeholder};
        text-overflow: ellipsis;
        max-width: 100%;
        overflow: hidden;
      }

      .ace_marker {
        border-bottom: 2px dashed;
        position: absolute;
        border-radius: 0;
        margin-top: 1px;
      }

      .ace_marker.ace_validation_error {
        border-color: ${$scTheme.colors.variant.danger};
      }

      .ace_marker.ace_validation_warning {
        border-color: ${$scTheme.colors.variant.dark.warning};
      }
    }
  `,
);

export default StyledAceEditor;
