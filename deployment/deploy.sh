#!/usr/bin/env bash
echo "Deploy script running"
if ([ "$BRANCH" = master ] || [[ "$BRANCH" = release/* ]]) && [ "$PULL_REQUEST" = false ]; then
    openssl aes-256-cbc -K $encrypted_3253fed51ff9_key -iv $encrypted_3253fed51ff9_iv -in deployment/codesigning.asc.enc -out deployment/codesigning.asc -d
    gpg --fast-import deployment/codesigning.asc

    mvn deploy -P sign,build-extras --settings deployment/maven_settings.xml
fi
echo "Deploy script finished"