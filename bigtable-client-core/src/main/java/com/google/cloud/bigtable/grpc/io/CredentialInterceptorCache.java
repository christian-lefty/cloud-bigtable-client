/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.grpc.io;

import static com.google.cloud.bigtable.util.ThreadPoolUtil.createThreadFactory;

import com.google.auth.Credentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.bigtable.config.CredentialFactory;
import com.google.cloud.bigtable.config.CredentialOptions;
import com.google.cloud.bigtable.config.CredentialOptions.CredentialType;
import com.google.cloud.bigtable.config.RetryOptions;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches {@link RefreshingOAuth2CredentialsInterceptor} for default authorization cases.  In other
 * types of authorization, such as file based Credentials, it will create a new one.
 */
public class CredentialInterceptorCache {
  private static CredentialInterceptorCache instance = new CredentialInterceptorCache();

  public static CredentialInterceptorCache getInstance() {
    return instance;
  }

  private final ExecutorService executor =
      Executors.newCachedThreadPool(createThreadFactory("Credentials-Refresh"));

  private HeaderInterceptor defaultCredentialInterceptor;

  private CredentialInterceptorCache() {
  }

  /**
   * Given {@link CredentialOptions} that define how to look up credentials, do the following:
   *
   * <ol>
   *   <li> Look up the credentials
   *   <li> If there are credentials, create a gRPC interceptor that gets OAuth2 security tokens
   *        and add that token as a header on all calls.
   *        <br>NOTE: {@link RefreshingOAuth2CredentialsInterceptor} ensures that the token stays
   *        fresh. It does token lookups asynchronously so that the calls themselves take as little
   *        performance penalty as possible.
   *   <li> Cache the interceptor in step #2 if the {@link CredentialOptions} uses
   *        <a href="https://developers.google.com/identity/protocols/application-default-credentials">
   *        default application credentials
   *        </a>
   * </ol>
   * @param credentialOptions Defines how credentials should be achieved
   * @param retryOptions
   * @return a HeaderInterceptor
   * @throws IOException
   * @throws GeneralSecurityException
   */
  public synchronized HeaderInterceptor getCredentialsInterceptor(
      CredentialOptions credentialOptions, RetryOptions retryOptions)
      throws IOException, GeneralSecurityException {
    // Default credentials is the most likely CredentialType. It's also the only CredentialType
    // that can be safely cached.
    boolean isDefaultCredentials =
        credentialOptions.getCredentialType() == CredentialType.DefaultCredentials;

    if (isDefaultCredentials && defaultCredentialInterceptor != null) {
      return defaultCredentialInterceptor;
    }

    Credentials credentials = CredentialFactory.getCredentials(credentialOptions);

    if (credentials == null) {
      return null;
    }
    Preconditions.checkState(
        credentials instanceof OAuth2Credentials,
        String.format(
            "Credentials must be an instance of OAuth2Credentials, but got %s.",
            credentials.getClass().getName()));

    RefreshingOAuth2CredentialsInterceptor oauth2Interceptor =
        new RefreshingOAuth2CredentialsInterceptor(
            executor, (OAuth2Credentials) credentials, retryOptions);

    // The RefreshingOAuth2CredentialsInterceptor uses the credentials to get a security token that
    // will live for a short time.  That token is added on all calls by the gRPC interceptor to
    // allow users to access secure resources.
    //
    // Perform that token lookup asynchronously. This permits other work to be done in
    // parallel. The RefreshingOAuth2CredentialsInterceptor has internal locking that assures that
    // the oauth2 token is loaded before the interceptor proceeds with any calls.
    oauth2Interceptor.asyncRefresh();
    if (isDefaultCredentials) {
      defaultCredentialInterceptor = oauth2Interceptor;
    }
    return oauth2Interceptor;
  }
}
