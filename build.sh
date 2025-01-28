#!/bin/bash

echo "Building Honk app..."

# Set Android SDK path
export ANDROID_HOME=$HOME/Android/Sdk

# Navigate to project directory
cd "$(dirname "$0")" || exit

# Run Gradle build
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "Build successful! APK location: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "Build failed!"
    exit 1
fi
