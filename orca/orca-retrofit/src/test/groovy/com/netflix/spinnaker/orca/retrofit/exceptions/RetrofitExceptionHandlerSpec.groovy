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

package com.netflix.spinnaker.orca.retrofit.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Client
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import retrofit.http.*
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static retrofit.RetrofitError.Kind.NETWORK

class RetrofitExceptionHandlerSpec extends Specification {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Subject
  def handler = new RetrofitExceptionHandler()

  @Unroll
  def "should only handle RetrofitError"() {
    expect:
    handler.handles(exception) == supported

    where:
    exception                                           | supported
    RetrofitError.networkError(null, new IOException()) | true
    new RuntimeException()                              | false
    new IllegalArgumentException()                      | false
  }

  def "should handle validation errors (400) encoded within a RetrofitError"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([error: error, errors: errors]))
    def retrofitError = RetrofitError.httpError(
      url, new Response(url, status, reason, [], body), new JacksonConverter(), Map
    )

    expect:
    with(handler.handle(stepName, retrofitError)) {
      !shouldRetry
      exceptionType == "RetrofitError"
      operation == stepName
      details.url == url
      details.status == status
      details.error == error
      details.errors == errors
      details.responseBody == "{\"error\":\"Error Message\",\"errors\":[\"Error #1\",\"Error #2\"]}"
      details.rootException == null
    }

    where:
    stepName = "Step"
    url = "http://www.google.com"
    status = 400
    reason = "Bad Request"
    error = "Error Message"
    errors = ["Error #1", "Error #2"]
  }

  def "should handle unexpected server errors (5xx) encoded within a RetrofitError"() {
    given:
    def body = new TypedByteArray(null, OBJECT_MAPPER.writeValueAsBytes([
      error: error, exception: rootException, message: message
    ]))
    def retrofitError = RetrofitError.httpError(
      url, new Response(url, status, reason, [], body), new JacksonConverter(), Map
    )

    expect:
    with(handler.handle(stepName, retrofitError)) {
      !shouldRetry
      exceptionType == "RetrofitError"
      operation == stepName
      details.url == url
      details.status == status
      details.error == error
      details.errors == [message]
      details.rootException == rootException
    }

    where:
    stepName = "Step"
    url = "http://www.google.com"
    status = 500
    reason = "Internal Server Error"
    error = reason
    rootException = "java.lang.RuntimeException"
    message = "Something bad happened"
  }

  private interface DummyRetrofitApi {
    @GET("/whatever")
    Response get()

    @HEAD("/whatever")
    Response head()

    @POST("/whatever")
    Response post(@Body String data)

    @PUT("/whatever")
    Response put(@Body String data)

    @PATCH("/whatever")
    Response patch(@Body String data)

    @DELETE("/{whatever}")
    Response delete(@Path("whatever") String data)
  }

  @Unroll
  def "should not retry a network error on a #httpMethod request"() {
    given:
    def client = Stub(Client) {
      execute(_) >> { throw new IOException("network error") }
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setClient(client)
      .build()
      .create(DummyRetrofitApi)

    when:
    def ex = expectingException {
      api."$methodName"("whatever")
    }

    then:
    with(handler.handle("whatever", ex)) {
      details.kind == NETWORK
      !shouldRetry
    }

    where:
    httpMethod << ["POST", "PUT", "PATCH", "DELETE"]
    methodName = httpMethod.toLowerCase()
  }

  @Unroll
  def "should retry a network error on a #httpMethod request"() {
    given:
    def client = Stub(Client) {
      execute(_) >> { throw new IOException("network error") }
    }

    and:
    def api = new RestAdapter.Builder()
      .setEndpoint("http://localhost:1337")
      .setClient(client)
      .build()
      .create(DummyRetrofitApi)

    when:
    def ex = expectingException {
      api."$methodName"()
    }

    then:
    with(handler.handle("whatever", ex)) {
      details.kind == NETWORK
      shouldRetry
    }

    where:
    httpMethod << ["GET", "HEAD"]
    methodName = httpMethod.toLowerCase()
  }

  private static RetrofitError expectingException(Closure action) {
    try {
      action()
      throw new IllegalStateException("Closure did not throw an exception")
    } catch (RetrofitError e) {
      return e
    }
  }

}
