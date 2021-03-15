#!/bin/bash

echo no | avdmanager -v create avd --name ci-test-pixel-x86-api30 --package "system-images;android-30;google_apis;x86" --device 'pixel_xl' --abi 'google_apis/x86'
emulator -avd ci-test-pixel-x86-api30 -noaudio -no-boot-anim -gpu off -no-window &
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done;'
adb shell wm dismiss-keyguard
sleep 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0