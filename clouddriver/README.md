Kato
===

Deployment libraries for Spinnaker.

Prerequisites
===

**JDK**

This project requires JDK7 or higher to build. There is [JVM bug](https://jira.codehaus.org/browse/GROOVY-6951) affecting some JDK versions, so ensure you are using 7u60 or below, or 7u72 or above (or 8u5 or below/8u220 and above)

Quick Use
===

`./gradlew`

Bootstrapping
===

  * Build a tar of the kato-web module: `./gradlew clean build :kato-web:distTar`
  * Copy the tar to your target servers: `scp kato-web/build/distributions/*.tar <user>@<host>:/path/to/where/kato/lives`
  * SSH to your target server and untar: `cd /path/to/where/kato/lives; tar xvf kato-web-1.3.xx.tar`
  * Export your AWS access keys as environment variables [a la](https://console.aws.amazon.com/iam/home?#security_credential): `export AWS_ACCESS_KEY_ID=xxx; export AWS_SECRET_KEY=xxx`
  * Start Kato from the start script: `cd /path/to/where/kato/lives/kato-web-1.3.xx/bin/kato-web` -- should start and bind on port 8501.

Documentation
===

Start an instance of Kato and point to `/manual/index.html`.

Copyright and License
===

Copyright (C) 2014 Netflix. Licensed under the Apache License.

See [LICENSE.txt](https://raw.githubusercontent.com/spinnaker/kato/master/LICENSE.txt) for more information.
