/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.echo

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.websocket.WebSocketAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Application entry point.
 */
@Configuration
@EnableAutoConfiguration(exclude = WebSocketAutoConfiguration)
@ComponentScan('com.netflix.spinnaker.echo.config')
class Application extends SpringBootServletInitializer {

    static void main(String[] args) {
        if (System.getProperty('netflix.environment') == null) {
            System.setProperty('netflix.environment', 'test')
        }
        SpringApplication.run(Application, args)
    }

    @Override
    SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        if (System.getProperty('netflix.environment') == null) {
            System.setProperty('netflix.environment', 'test')
        }
        application.sources(Application)
        Object.configure(application)
    }
}
