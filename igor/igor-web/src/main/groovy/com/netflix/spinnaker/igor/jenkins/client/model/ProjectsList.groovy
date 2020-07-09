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

package com.netflix.spinnaker.igor.jenkins.client.model


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import groovy.transform.CompileStatic

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Represents a list of projects
 */
@XmlRootElement(name = 'hudson')
@CompileStatic
class ProjectsList {

    // not sure why we need this, it seems that there shouldn't be a <job> element wrapping the list
    // since we don't have the JAXB @XmlElementWrapper annotation, but for some reason Jackson expects one
    @JacksonXmlElementWrapper(useWrapping=false)
    @XmlElement(name = 'job')
    List<Project> list
}
