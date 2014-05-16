package com.netflix.bluespar.orca.bakery.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class BakeStatus {

    String id
    BakeState state

}
