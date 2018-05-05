/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.echo;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JiraService {
  private final EchoService echoService;
  private final ObjectMapper mapper;

  @Autowired
  public JiraService(EchoService echoService, ObjectMapper mapper) {
    this.echoService = echoService;
    this.mapper = mapper;
  }

  public CreateJiraIssueResponse createJiraIssue(CreateIssueRequest createIssueRequest) throws CreateJiraIssueException {
    try {
      validateInputs(createIssueRequest);
      EchoService.Notification notification = new EchoService.Notification();
      EchoService.Notification.Source source = new EchoService.Notification.Source();
      Optional.ofNullable(createIssueRequest.getFields().getReporter()).ifPresent(reporter -> {
        source.setUser(reporter.getName());
        notification.setTo(Collections.singletonList(reporter.getName()));
      });

      notification.setSource(source);
      notification.setNotificationType(EchoService.Notification.Type.JIRA);
      notification.setAdditionalContext(ImmutableMap.of("issueContext", createIssueRequest));
      Response response = echoService.create(notification);
      return mapper.readValue(response.getBody().in(), CreateJiraIssueResponse.class);
    } catch (Exception e) {
      throw new CreateJiraIssueException(
        String.format("Failed to create Jira Issue for project %s %s", createIssueRequest.getFields().getProject(), e.getMessage())
      );
    }
  }

  private void validateInputs(CreateIssueRequest createIssueRequest) {
    Set<ConstraintViolation<CreateIssueRequest>> violations = Validation.buildDefaultValidatorFactory()
      .getValidator()
      .validate(createIssueRequest);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
        "Failed validation: " + violations.stream()
          .map(v -> String.format("%s: %s", v.getPropertyPath().toString(), v.getMessage()))
          .collect(Collectors.toList())
      );
    }
  }

  public static class CreateJiraIssueException extends RuntimeException {
    public CreateJiraIssueException(String message) {
      super(message);
    }
  }

  public static class CreateJiraIssueResponse {
    private Value value;

    public Value getValue() {
      return value;
    }

    public void setValue(Value value) {
      this.value = value;
    }

    public static class Value {
      private String id;
      private String key;
      private String self;

      public String getId() {
        return id;
      }

      public void setId(String id) {
        this.id = id;
      }

      public String getKey() {
        return key;
      }

      public void setKey(String key) {
        this.key = key;
      }

      public String getSelf() {
        return self;
      }

      public void setSelf(String self) {
        this.self = self;
      }
    }

  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class CreateIssueRequest {
    private Fields fields;

    public CreateIssueRequest() {
      this.fields = new JiraService.CreateIssueRequest.Fields();
    }

    public CreateIssueRequest withProjectId(String projectId) {
       fields.setProject(new Fields.Project().withId(projectId));
       return this;
    }

    public CreateIssueRequest withProjectKey(String key) {
      fields.setProject(new Fields.Project().withKey(key));
      return this;
    }

    public CreateIssueRequest withParentKey(String key) {
      fields.setParent(new Fields.Parent().withKey(key));
      return this;
    }

    public CreateIssueRequest withParentId(String id) {
      fields.setParent(new Fields.Parent().withId(id));
      return this;
    }

    public CreateIssueRequest withSummary(String summary) {
      fields.setSummary(summary);
      return this;
    }

    public CreateIssueRequest withDescription(String description) {
      fields.setDescription(description);
      return this;
    }

    public CreateIssueRequest withIssueTypeName(String issueTypeName) {
      fields.setIssueType(new JiraService.CreateIssueRequest.Fields.IssueType().withName(issueTypeName));
      return this;
    }

    public CreateIssueRequest withIssueTypeId(String id) {
      fields.setIssueType(new JiraService.CreateIssueRequest.Fields.IssueType().withId(id));
      return this;
    }

    public CreateIssueRequest withReporter(String reporter) {
      fields.setReporter(new Fields.Reporter().withName(reporter));
      return this;
    }

    public void addDetails(String key, Object object) {
      fields.details().put(key, object);
    }

    public Fields getFields() {
      return fields;
    }

    public void setFields(Fields fields) {
      this.fields = fields;
    }

    public void setReporter(String reporter) {
      new JiraService.CreateIssueRequest.Fields.Reporter().withName(reporter);
    }

    @Override
    public String toString() {
      return "CreateIssueContext{" +
        "fields=" + fields +
        '}';
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fields {
      @NotNull
      private Project project;
      @NotNull private String description;
      @JsonProperty("issuetype") private IssueType issueType;
      private String summary;
      private Parent parent;
      private Reporter reporter;

      public Project getProject() {
        return project;
      }

      public void setProject(Project project) {
        this.project = project;
      }

      public String getSummary() {
        return summary;
      }

      public void setSummary(String summary) {
        this.summary = summary;
      }

      public String getDescription() {
        return description;
      }

      public void setDescription(String description) {
        this.description = description;
      }

      public IssueType getIssueType() {
        return issueType;
      }

      public void setIssueType(IssueType issueType) {
        this.issueType = issueType;
      }

      public Parent getParent() {
        return parent;
      }

      public void setParent(Parent parent) {
        this.parent = parent;
      }

      public Reporter getReporter() {
        return reporter;
      }

      public void setReporter(Reporter reporter) {
        this.reporter = reporter;
      }

      private Map<String, Object> details = new HashMap<>();
      @JsonAnySetter
      public void set(String name, Object value) {
        details.put(name, value);
      }

      @JsonAnyGetter
      public Map<String, Object> details() {
        return details;
      }

      @Override
      public String toString() {
        return "Fields{" +
          "details=" + details +
          ", project=" + project +
          ", description='" + description + '\'' +
          ", issueType=" + issueType +
          ", summary='" + summary + '\'' +
          ", parent=" + parent +
          ", reporter='" + reporter + '\'' +
          '}';
      }

      public static class Reporter {
        private String name;

        public Reporter withName(String name) {
          setName(name);
          return this;
        }

        public String getName() {
          return name;
        }

        public void setName(String name) {
          this.name = name;
        }

        @Override
        public String toString() {
          return "Reporter{" +
            "name='" + name + '\'' +
            '}';
        }
      }

      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class Project extends IdKey {
        public Project withId(String id) {
          setId(id);
          return this;
        }

        public Project withKey(String key) {
          setKey(key);
          return this;
        }
        @Override
        public String toString() {
          return "Project{" +
            "id='" + super.id + '\'' +
            ", key='" + super.key + '\'' +
            '}';
        }
      }

      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class Parent extends IdKey {
        public Parent withId(String id) {
          setId(id);
          return this;
        }

        public Parent withKey(String key) {
          setKey(key);
          return this;
        }

        @Override
        public String toString() {
          return "Parent{" +
            "id='" + super.id + '\'' +
            ", key='" + super.key + '\'' +
            '}';
        }
      }

      static class IdKey {
        private String id;
        private String key;

        public String getId() {
          return id;
        }

        public void setId(String id) {
          this.id = id;
        }

        public String getKey() {
          return key;
        }

        public void setKey(String key) {
          this.key = key;
        }
      }

      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class IssueType {
        private String name;
        private String id;

        public IssueType withName(String name) {
          setName(name);
          return this;
        }

        public IssueType withId(String id) {
          setId(id);
          return this;
        }

        public String getName() {
          return name;
        }

        public void setName(String name) {
          this.name = name;
        }

        public String getId() {
          return id;
        }

        public void setId(String id) {
          this.id = id;
        }

        @Override
        public String toString() {
          return "IssueType{" +
            "name='" + name + '\'' +
            ", id='" + id + '\'' +
            '}';
        }
      }
    }
  }
}
