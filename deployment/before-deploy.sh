#!/usr/bin/env bash
if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_839e06c05d8b_key -iv $encrypted_839e06c05d8b_iv -in deployment/codesigning.asc.enc -out deployment/codesigning.asc -d
    gpg --fast-import deployment/codesigning.asc
fi