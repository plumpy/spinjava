/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.controllers.v2

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.S3ProjectDAO
import com.netflix.spinnaker.front50.model.project.CassandraProjectDAO
import com.netflix.spinnaker.front50.model.project.Project
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.utils.CassandraTestHelper
import com.netflix.spinnaker.front50.utils.S3TestHelper
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import rx.schedulers.Schedulers
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Executors

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

abstract class ProjectsControllerTck extends Specification {
  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  MockMvc mockMvc

  @Shared
  ProjectsController controller

  @Subject
  ProjectDAO dao

  void setup() {
    this.dao = createProjectDAO()
    this.controller = new ProjectsController(
        projectDAO: dao,
        messageSource: new StaticMessageSource()
    )
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  }

  abstract ProjectDAO createProjectDAO()

  @Unroll
  void "should search by projectName or applicationName"() {
    given:
    if (project != null) {
      dao.create(null, project)
    }

    when:
    def response = mockMvc.perform(get("/v2/projects/search?q=" + criteria))
    def result = response.andReturn()

    then:
    response.andExpect status().isOk()
    extractList(result.getResponse().getContentAsString()) == (project ? [toMap(dao.findByName(project.name))] : [])

    where:
    criteria       | project
    "Project1"     | new Project(name: "Project1")
    "Application1" | new Project(name: "Project1", config: new Project.ProjectConfig(
        applications: ["Application1", "Application2"]
    ))
    "n/a"          | null
  }

  void "should fetch all projects"() {
    given:
    [new Project(name: "Project1"), new Project(name: "Project2")].each {
      dao.create(null, it)
    }

    expect:
    extractList(mockMvc.perform(get("/v2/projects")).andReturn().response.contentAsString) == dao.all().collect {
      objectMapper.convertValue(it, Map)
    }
  }

  @Unroll
  void "should fetch projects by name or id"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    expect:
    extractMap(mockMvc.perform(get("/v2/projects/" + project.name)).andReturn().response.contentAsString) == toMap(project)
    extractMap(mockMvc.perform(get("/v2/projects/" + project.id)).andReturn().response.contentAsString) == toMap(project)
    mockMvc.perform(get("/v2/projects/DOES_NOT_EXIST")).andExpect(status().is4xxClientError())
  }

  void "should create projects"() {
    given:
    def project = new Project(name: "Project1")

    when:
    def response1 = mockMvc.perform(
        post("/v2/projects").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    response1.andExpect(status().isOk())
    dao.findByName(project.name) != null

    when:
    def response2 = mockMvc.perform(
        post("/v2/projects").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    // should fail when attempting to create a project with a duplicate name
    response2.andExpect(status().is4xxClientError())
  }

  void "should update existing project"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    // apply an update
    project.email = "default@netflix.com"

    when:
    def response1 = mockMvc.perform(
        put("/v2/projects/" + project.id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    response1.andExpect(status().isOk())
    dao.findByName(project.name).email == project.email

    when:
    dao.create(null, new Project(name: "Project2"))
    project.name = "Project2"

    def response2 = mockMvc.perform(
        put("/v2/projects/" + project.id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(project))
    )

    then:
    // should fail when attempting to rename a project and the name is not unique
    response2.andExpect(status().is4xxClientError())
  }

  void "should delete an existing project"() {
    given:
    def project = dao.create(null, new Project(name: "Project1"))

    when:
    def response = mockMvc.perform(
        delete("/v2/projects/" + project.id)
    )

    then:
    response.andExpect(status().isAccepted())

    when:
    dao.findByName(project.name)

    then:
    thrown(NotFoundException)
  }

  List<Map> extractList(String content) {
    (objectMapper.readValue(content, Map).content as List<Map>).collect {
      it.remove("links")
      it
    }
  }

  Map extractMap(String content) {
    def result = objectMapper.readValue(content, Map) as Map
    result.remove("links")
    result
  }

  private Map toMap(Project project) {
    return objectMapper.convertValue(project, Map)
  }
}

class CassandraProjectsControllerTck extends ProjectsControllerTck {
  @Shared
  CassandraTestHelper cassandraHelper = new CassandraTestHelper()

  @Shared
  CassandraProjectDAO projectDAO

  @Override
  ProjectDAO createProjectDAO() {
    projectDAO = new CassandraProjectDAO(keyspace: cassandraHelper.keyspace, objectMapper: objectMapper)
    projectDAO.init()

    projectDAO.runQuery('''TRUNCATE project''')

    return projectDAO
  }
}

@IgnoreIf({ S3TestHelper.s3ProxyUnavailable() })
class S3ProjectsControllerTck extends ProjectsControllerTck {
  @Shared
  def scheduler = Schedulers.from(Executors.newFixedThreadPool(1))

  @Shared
  S3ProjectDAO s3ProjectDAO

  @Override
  ProjectDAO createProjectDAO() {
    def amazonS3 = new AmazonS3Client(new ClientConfiguration())
    amazonS3.setEndpoint("http://127.0.0.1:9999")
    S3TestHelper.setupBucket(amazonS3, "front50")

    s3ProjectDAO = new S3ProjectDAO(new ObjectMapper(), amazonS3, scheduler, 0, "front50", "test")
    return s3ProjectDAO
  }
}
