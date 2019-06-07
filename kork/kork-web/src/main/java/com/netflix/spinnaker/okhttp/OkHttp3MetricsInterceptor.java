/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.okhttp;

import com.netflix.spectator.api.Registry;
import java.io.IOException;
import okhttp3.Response;

public class OkHttp3MetricsInterceptor extends MetricsInterceptor implements okhttp3.Interceptor {

  public OkHttp3MetricsInterceptor(Registry registry, boolean skipHeaderCheck) {
    super(registry, skipHeaderCheck);
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    try {
      return (Response) doIntercept(chain);
    } catch (IOException ioe) {
      throw ioe;
    } catch (Exception ex) {
      throw new IOException(ex);
    }
  }
}
