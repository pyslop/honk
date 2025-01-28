#!/bin/bash

echo "Deploying Honk app to connected device..."

# Check for connected devices
if ! adb devices | grep -q "device$"; then
    echo "No device connected! Please connect your device via USB"
    echo "Make sure USB debugging is enabled on your device"
    exit 1
fi

# Install the app
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "App deployed successfully!"
    # Launch the app
    adb shell am start -n "com.example.honk/.MainActivity"
else
    echo "Deployment failed!"
    exit 1
fi
