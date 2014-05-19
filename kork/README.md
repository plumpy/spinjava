[![Build Status](https://drone.io/github.com/bluespar/kork/status.png)](https://drone.io/github.com/bluespar/kork/latest)

Kork adapts NetflixOSS platform components to Spring configuration and Spring-Boot autoconfiguration.

This project provides Spring bindings for NetflixOSS components that are typically exposed and configured via the internal Netflix Platform. The exposed Bean bindings are set up with reasonable defaults and limited assumptions about the existence of other infrastructure services. Using Spring-Boot AutoConfiguration, they will only conditionally load in an environment where the internal Netflix platform is not available.


