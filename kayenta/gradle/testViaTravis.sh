#!/bin/bash
# This script will build and test the project.

export GRADLE_OPTS="-Xmx1g -Xms1g"

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  ./gradlew -Prelease.useLastTag=true test
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" test --stacktrace
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  *-rc\.*)
    ./gradlew -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" candidate --stacktrace
    ;;
  *)
    ./gradlew -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" final --stacktrace
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  ./gradlew -Prelease.useLastTag=true test
fi

