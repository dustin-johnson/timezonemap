#!/usr/bin/env bash
echo "Deploy script running"

# Deploy if the branch is a tagged release (v3.1, etc.) or if the branch is a snapshot version on a release branch.
if [[ "$TRAVIS_BRANCH" =~ v[0-9](\.[0-9])+ ]] || ([[ "$TRAVIS_BRANCH" = release/* ]] && compgen -G "timezonemap/target/timezonemap-*-SNAPSHOT.jar" > /dev/null); then
    openssl aes-256-cbc -K $encrypted_3253fed51ff9_key -iv $encrypted_3253fed51ff9_iv -in deployment/codesigning.asc.enc -out deployment/codesigning.asc -d
    gpg --fast-import deployment/codesigning.asc

    mvn deploy -DskipDataPackaging=true -DskipTests=true -Dmaven.javadoc.skip=true -P sign,build-extras --settings deployment/maven_settings.xml
fi