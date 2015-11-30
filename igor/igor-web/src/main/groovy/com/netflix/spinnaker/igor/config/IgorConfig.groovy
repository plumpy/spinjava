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

package com.netflix.spinnaker.igor.config

import com.netflix.hystrix.exception.HystrixRuntimeException
import groovy.transform.CompileStatic
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import retrofit.RetrofitError

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

/**
 * Converts validation errors into REST Messages
 */
@Configuration
@CompileStatic
class IgorConfig extends WebMvcConfigurerAdapter {
    @Bean
    ExecutorService executorService() {
        Executors.newCachedThreadPool()
    }

    @Bean
    HystrixRuntimeExceptionHandler hystrixRuntimeExceptionHandler() {
        return new HystrixRuntimeExceptionHandler()
    }

    @ControllerAdvice
    static class HystrixRuntimeExceptionHandler {
        @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
        @ResponseBody
        @ExceptionHandler(HystrixRuntimeException)
        public Map handleHystrix(HystrixRuntimeException exception) {
            def failureCause = exception.cause
            if (failureCause instanceof RetrofitError) {
                failureCause = failureCause.cause ?: failureCause
            }

            return [
                fallbackException: exception.fallbackException.toString(),
                failureType: exception.failureType,
                failureCause: failureCause.toString(),
                error: "Hystrix Failure",
                message: exception.message,
                status: HttpStatus.TOO_MANY_REQUESTS.value(),
                timestamp: System.currentTimeMillis()
            ]
        }
    }
}
