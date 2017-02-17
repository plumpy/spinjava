/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kayenta.google.config;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kayenta.security.AccountCredentials;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

@Data
public class GoogleManagedAccount {

  private String name;
  private String project;
  private String jsonPath;
  private List<AccountCredentials.Type> supportedTypes;

  private InputStream getInputStream() throws FileNotFoundException {
    if (StringUtils.hasLength(jsonPath)) {
      if (jsonPath.startsWith("classpath:")) {
        return getClass().getResourceAsStream(jsonPath.replace("classpath:", ""));
      } else {
        return new FileInputStream(new File(jsonPath));
      }
    } else {
      return null;
    }
  }

  public String getJsonKey() throws IOException {
    InputStream inputStream = getInputStream();

    return inputStream != null ? CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8)) : null;
  }
}
