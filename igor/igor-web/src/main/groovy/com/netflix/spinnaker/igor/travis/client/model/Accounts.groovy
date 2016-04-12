/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Default
@CompileStatic
@JsonInclude(JsonInclude.Include.NON_NULL)
@Root(strict = false)
class Accounts {
    @ElementList(required = false, name = "accounts", inline = true)
    List<Account> accounts
}
