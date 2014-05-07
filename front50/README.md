Front50 SimpleDB Service
------------------------------------

This service fronts AWS SimpleDB. It is written using [Spring Boot][0]; it only fronts one SimpleDB table: RESOURCE_REGISTRY.

Supported endpoints:

 * `GET` `/` returns all Application records.
 * `GET` `/name/{name}` returns a single Application record (if found).
 * `PUT` `/` request body must be JSON document and will update an Application record (if found).
 * `POST` `/` request body must be JSON document and will create an Application record (if a name is provided).
 * `DELETE` `/name/{name}` removed an Application record (if found).

[0]:http://projects.spring.io/spring-boot/