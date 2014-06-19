package com.netflix.spinnaker.orca.test

import com.netflix.spinnaker.orca.test.httpserver.HandlerResponseBuilder
import com.netflix.spinnaker.orca.test.httpserver.MethodFilteringHttpHandler
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import static java.net.HttpURLConnection.HTTP_BAD_METHOD

/**
 * A JUnit Rule for tests that need a running HTTP server. The server is automatically started and stopped
 * between each test. Use the {@link HttpServerRule#expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)}
 * methods to configure valid endpoints that your code-under-test can connect to.
 */
@CompileStatic
class HttpServerRule implements TestRule {

    public static final int DEFAULT_SERVER_SHUTDOWN_TIMEOUT = 3

    private int serverShutdownTimeout
    private String baseURI
    private HttpServer server

    HttpServerRule() {
        this(DEFAULT_SERVER_SHUTDOWN_TIMEOUT)
    }

    HttpServerRule(int serverShutdownTimeout) {
        this.serverShutdownTimeout = serverShutdownTimeout
    }

    @Override
    Statement apply(Statement base, Description description) {
        { ->
            try {
                startServer()
                base.evaluate()
            } finally {
                stopServer()
            }
        } as Statement
    }

    /**
     * @return the URI of the root of the web server.
     */
    final String getBaseURI() {
        baseURI
    }

    /**
     * Sets up an expectation for an HTTP request. If a request to {@code path} is made using the specified
     * {@code method} then the server will respond according to the parameters supplied to this method. If a
     * request is made to {@code path} using a different HTTP method the server will respond with
     * {@value HttpURLConnection#HTTP_BAD_METHOD}.
     *
     * @param method the HTTP method expected
     * @param path the literal path expected relative to the base URI of the server. Note this cannot use wildcards or any other clever things.
     * @return a mechanism for configuring the response to this expectation.
     */
    final ResponseConfiguration expect(String method, String path) {
        def responseBuilder = new HandlerResponseBuilder()
        server.createContext path, { HttpExchange exchange ->
            try {
                if (exchange.requestMethod == method) {
                    responseBuilder.handle(exchange)
                } else {
                    exchange.sendResponseHeaders HTTP_BAD_METHOD, 0
                }
            } finally {
                exchange.close()
            }
        }
        return new ResponseConfiguration(responseBuilder)
    }

    private void startServer() {
        def address = new InetSocketAddress(0)
        server = HttpServer.create(address, 0)
        server.executor = null
        server.start()
        baseURI = "http://localhost:$server.address.port"
    }

    private void stopServer() {
        server.stop serverShutdownTimeout
    }

}
