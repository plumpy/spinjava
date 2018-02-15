/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.artifacts;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Translates pub/sub messages into Spinnaker Artifacts via user-supplied Jinja templates.
 */
@Slf4j
@NoArgsConstructor
public class MessageArtifactTranslator {

  private String jinjaTemplate;

  private static final TypeReference<List<Artifact>> artifactListReference = new TypeReference<List<Artifact>>() {};

  public MessageArtifactTranslator(InputStream templateStream) {
    if (templateStream == null) {
      this.jinjaTemplate = "";
    } else {
      try {
        this.jinjaTemplate = IOUtils.toString(templateStream);
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }

  public List<Artifact> parseArtifacts(String messagePayload) {
    if (StringUtils.isEmpty(messagePayload)) {
      return Collections.emptyList();
    }

    ObjectMapper mapper = new ObjectMapper();
    return readArtifactList(mapper, jinjaTransform(mapper, messagePayload));
  }

  private String jinjaTransform(ObjectMapper mapper, String messagePayload) {
    if (StringUtils.isEmpty(jinjaTemplate)) {
      return messagePayload;
    } else {
      Jinjava jinja = new Jinjava();
      Map context = readMapValue(mapper, messagePayload);
      return jinja.render(jinjaTemplate, context);
    }
  }

  private Map readMapValue(ObjectMapper mapper, String messagePayload) {
    Map context;
    try {
      context = mapper.readValue(messagePayload, Map.class);
    } catch (IOException ioe) {
      log.error(messagePayload);
      throw new InvalidRequestException(ioe);
    }
    return context;
  }

  private List<Artifact> readArtifactList(ObjectMapper mapper, String hydratedTemplate) {
    try {
      return mapper.readValue(hydratedTemplate, artifactListReference);
    } catch (IOException ioe) {
      // Failure to parse artifacts from the message indicates either
      // the message payload does not match the provided template or
      // there is no template and no artifacts are expected
      log.warn("Unable to parse artifact from {}", hydratedTemplate);
    }
    return null;
  }
}
