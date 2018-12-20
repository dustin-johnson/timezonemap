#!/usr/bin/env bash
if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    echo "TestEnv: ${TEST_ENV:1}"
    mvn deploy -P sign,build-extras --settings deployment/maven_settings.xml
fi