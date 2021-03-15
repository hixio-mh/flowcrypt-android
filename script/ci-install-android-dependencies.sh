#!/bin/bash

sdkmanager "platform-tools" "platforms;android-29" "build-tools;29.0.2" "emulator" > /dev/null | grep -v = || true
sdkmanager "ndk;22.0.7026061" > /dev/null | grep -v = || true
sdkmanager "cmake;3.10.2.4988404" > /dev/null | grep -v = || true
sdkmanager "system-images;android-30;google_apis;x86" > /dev/null | grep -v = || true