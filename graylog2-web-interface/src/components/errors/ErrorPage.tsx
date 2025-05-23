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
import styled, { createGlobalStyle, css } from 'styled-components';

import NotFoundBackgroundImage from 'assets/error-page-bg.svg';
import PageContentLayout from 'components/layout/PageContentLayout';
import DocumentTitle from 'components/common/DocumentTitle';
import ErrorJumbotron from 'components/errors/ErrorJumbotron';

const generateStyles = () => css<{ backgroundImage: string }>`
  body {
    background: url(${({ backgroundImage }) => backgroundImage}) no-repeat center center fixed;
    background-size: cover;
  }
`;

const ErrorMessage = styled.div(
  ({ theme }) => css`
    margin-left: auto;
    margin-right: auto;
    text-align: left;

    dt {
      font-size: ${theme.fonts.size.body};
      font-weight: normal;
      overflow: auto;
    }

    p {
      font-size: inherit;
    }
  `,
);

type Props = {
  backgroundImage?: string;
  children?: React.ReactNode;
  displayPageLayout?: boolean;
  description: React.ReactNode;
  title: string;
};

const ErrorPageStyles = createGlobalStyle`
    ${generateStyles()}
`;

const ErrorPage = ({
  children = undefined,
  title,
  description,
  backgroundImage = NotFoundBackgroundImage,
  displayPageLayout = true,
}: Props) => {
  const PageLayoutComponent = displayPageLayout ? PageContentLayout : React.Fragment;

  return (
    <DocumentTitle title={title}>
      <PageLayoutComponent>
        <ErrorPageStyles backgroundImage={backgroundImage} />
        <ErrorJumbotron title={title}>
          {description}
          {children && <ErrorMessage>{children}</ErrorMessage>}
        </ErrorJumbotron>
      </PageLayoutComponent>
    </DocumentTitle>
  );
};

export default ErrorPage;
