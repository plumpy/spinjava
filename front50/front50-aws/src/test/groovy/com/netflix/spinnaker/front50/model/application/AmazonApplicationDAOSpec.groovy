/*
 * Copyright 2014 Netflix, Inc.
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



package com.netflix.spinnaker.front50.model.application

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.*
import com.netflix.spinnaker.front50.exception.NotFoundException
import spock.lang.Specification

/**
 * Created by aglover on 4/22/14.
 */
class AmazonApplicationDAOSpec extends Specification {

  void 'should update an existing record'() {
    def awsClient = Mock(AmazonSimpleDB)
    def attributes = [
      "group": "tst-group",
      "tags" : "[1,ok, test]"]
    def dao = new AmazonApplicationDAO()
    dao.awsSimpleDBClient = awsClient
    when:
    dao.update("SampleApp1", attributes)
    then:
    1 * awsClient.putAttributes(_)
  }

  void 'should transform a properties map into another one'() {
    def attributes = [
      "group"      : "tst-group",
      "type"       : "test type",
      "description": "test",
      "owner"      : "Kevin McEntee",
      "email"      : "web@netflix.com",
      "updateTs"   : "1265752693581",
      "tags"       : "[1,ok, test]"]
    def dao = new AmazonApplicationDAO()
    def values = dao.buildAttributes(attributes, false)
    def attr = values.find { it.name == "description" }
    expect:
    values != null
    values.size() == 7
    values[0].class == ReplaceableAttribute.class
    attr.value == "test"
  }

  void 'should delete an item'() {
    def awsClient = Mock(AmazonSimpleDB)
    def dao = new AmazonApplicationDAO()
    dao.awsSimpleDBClient = awsClient

    when:
    dao.delete("TEST")

    then:
    1 * awsClient.deleteAttributes(new DeleteAttributesRequest().withDomainName(dao.domain).withItemName("TEST"))
  }

  void 'should save'() {
    def awsClient = Mock(AmazonSimpleDB)

    def attributes = [
      "group"      : "tst-group",
      "type"       : "test type",
      "description": "test",
      "owner"      : "Kevin McEntee",
      "email"      : "web@netflix.com",
      "updateTs"   : "1265752693581",
      "tags"       : "[1,ok, test]"]
    def dao = new AmazonApplicationDAO()
    dao.awsSimpleDBClient = awsClient
    when:
    def application = dao.create("SampleApp1", attributes)
    then:
    application.email == 'web@netflix.com'
    application.createTs != null
    1 * awsClient.putAttributes(_)
  }

  void 'should throw exception if no application is found'() {
    def awsClient = Mock(AmazonSimpleDB)
    def result = Mock(SelectResult)
    def dao = new AmazonApplicationDAO(domain: "RESOURCE_REGISTRY")
    List<Item> outItems = new ArrayList<Item>() //nothing was found
    result.getItems() >> outItems
    awsClient.select(_) >> result
    dao.awsSimpleDBClient = awsClient
    when:
    dao.findByName("SAMPLEAPP")

    then:
    final Exception exp = thrown()
    exp.message == "No Application found by name of SAMPLEAPP in domain RESOURCE_REGISTRY"
  }

  void 'should build search query from provided map'() {
    def awsClient = Mock(AmazonSimpleDB)
    def dao = new AmazonApplicationDAO(domain: "RESOURCE_REGISTRY")
    dao.awsSimpleDBClient = awsClient

    when:
    dao.search([name:name, email:email])

    then:
    1 * awsClient.select(_) >> { SelectRequest req ->
      new SelectResult(items: [new Item("app", [new Attribute("name", name), new Attribute("email", email)])])
    }
    thrown NotFoundException

    where:
    name = "a"
    email = "b"
  }

  void 'should be able to search case-insensitively'() {
    def awsClient = Mock(AmazonSimpleDB)
    def dao = new AmazonApplicationDAO(domain: "RESOURCE_REGISTRY")
    dao.awsSimpleDBClient = awsClient

    when:
    dao.search([name:name.toUpperCase(), email:email])

    then:
    1 * awsClient.select(_) >> { SelectRequest req ->
      new SelectResult(items: [new Item(name, [new Attribute("email", email)])])
    }
    notThrown(NotFoundException)

    when:
    dao.search([name:name, email:"c"])

    then:
    1 * awsClient.select(_) >> { SelectRequest req ->
      new SelectResult(items: [new Item(name, [new Attribute("email", email)])])
    }
    thrown(NotFoundException)

    where:
    name = "a"
    email = "b"
  }

  void 'should throw exception if no applications exist'() {
    def awsClient = Mock(AmazonSimpleDB)
    def result = Mock(SelectResult)
    def dao = new AmazonApplicationDAO(domain: "RESOURCE_REGISTRY")
    List<Item> outItems = new ArrayList<Item>() //nothing was found
    result.getItems() >> outItems
    awsClient.select(_) >> result
    dao.awsSimpleDBClient = awsClient
    when:
    dao.all()

    then:
    final Exception exp = thrown()
    exp.message == "No Applications found in domain RESOURCE_REGISTRY"
  }

  void 'should find one application by name'() {
    def awsClient = Mock(AmazonSimpleDB)
    def result = Mock(SelectResult)
    def dao = new AmazonApplicationDAO()
    List<Item> outItems = new ArrayList<Item>()
    Item item = new Item().withName("SAMPLEAPP").withAttributes(
      new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
      new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
      new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))
    outItems << item
    result.getItems() >> outItems
    awsClient.select(_) >> result

    dao.awsSimpleDBClient = awsClient

    def app = dao.findByName("SAMPLEAPP")

    expect:
    app != null
    app.name == "SAMPLEAPP"
    app.description == "netflix.com application"
    app.owner == "Kevin McEntee"
    app.type == "Standalone Application"
  }

  void 'should find all applications'() {
    def awsClient = Mock(AmazonSimpleDB)
    def result = Mock(SelectResult)
    def dao = new AmazonApplicationDAO()

    List<Item> outItems = new ArrayList<Item>()
    outItems << new Item().withName("SAMPLEAPP").withAttributes(
      new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
      new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
      new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))
    outItems << new Item().withName("SAMPLEAPP_2").withAttributes(
      new Attribute("email", "web@netflix.com"), new Attribute("createTs", "1265752693581"),
      new Attribute("updateTs", "1265752693581"), new Attribute("description", "netflix.com application"),
      new Attribute("owner", "Kevin McEntee"), new Attribute("type", "Standalone Application"))

    result.getItems() >> outItems
    awsClient.select(_) >> result

    dao.awsSimpleDBClient = awsClient

    def apps = dao.all()

    expect:
    apps != null
    apps.size() == 2
  }

  void 'should continue to query while the response contains a nextToken'() {
    setup:
    def awsClient = Mock(AmazonSimpleDB)
    def dao = new AmazonApplicationDAO(awsSimpleDBClient: awsClient, domain: 'bacon')

    when:
    def results = dao.all()

    then:
    2 * awsClient.select(_) >>> [new SelectResult().withNextToken('tok').withItems(new Item(name: 'i1', attributes: [new Attribute(name:  'description', value: 'v1')])),
                                 new SelectResult().withNextToken(null).withItems(new Item(name: 'i2', attributes: [new Attribute(name: 'description', value: 'v1')])),]
    0 * _

    results.size() == 2
    results.find { it.name == 'i1' }
    results.find { it.name == 'i2'}
  }
}
