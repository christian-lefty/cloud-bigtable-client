/* 
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.auth.oauth2;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.cloud.bigtable.config.CredentialOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * OAuth2 credentials representing the built-in service account for Google Cloud Shell. See
 * https://cloud.google.com/cloud-shell/docs/ for more information.
 * <p>
 * NOTE: this is a copy of
 * https://github.com/google/google-auth-library-java/blob/master/oauth2_http/java/com/google/auth/
 * oauth2/CloudShellCredentials.java with a bug fix. This class gets called from
 * {@link CredentialOptions#defaultCredentials()} if cloud bigtable is invoked from the Google Cloud
 * Shell.  This copy should be removed once the google-auth-library-java has the fix.
 * <p>
 * See https://github.com/google/google-auth-library-java/pull/55 for the details of the fix.
 */
public class CloudShellCredentials extends GoogleCredentials {

  private final static int ACCESS_TOKEN_INDEX = 2;
  private final static int READ_TIMEOUT_MS = 5000;

  /**
   * The Cloud Shell back authorization channel uses serialized
   * Javascript Protobufers, preceeded by the message lengeth and a
   * new line character. However, the request message has no content,
   * so a token request consists of an empty JsPb, and its 2 character
   * lenth prefix.
   */
  protected final static String GET_AUTH_TOKEN_REQUEST = "2\n[]";

  private final int authPort;
  private final JsonFactory jsonFactory;
  
  public CloudShellCredentials(int authPort) {
    this.authPort = authPort;
    this.jsonFactory = OAuth2Utils.JSON_FACTORY;
  }

  protected int getAuthPort() {
    return this.authPort;
  }
  
  @Override
  public AccessToken refreshAccessToken() throws IOException {
    Socket socket = new Socket("localhost", this.getAuthPort());
    socket.setSoTimeout(READ_TIMEOUT_MS);
    AccessToken token;
    try {    
      PrintWriter out =
        new PrintWriter(socket.getOutputStream(), true);
      out.println(GET_AUTH_TOKEN_REQUEST);
    
      BufferedReader input =
          new BufferedReader(new InputStreamReader(socket.getInputStream()));
      input.readLine(); // Skip over the first line
      JsonParser parser = jsonFactory.createJsonParser(input);
      List<Object> messageArray = (List<Object>) parser.parseArray(ArrayList.class, Object.class);
      String accessToken = messageArray.get(ACCESS_TOKEN_INDEX).toString();
      token =  new AccessToken(accessToken, null);
    } finally {
      socket.close();
    }
    return token;
  }
}
