#!/bin/bash

PORT=${1:-5555} # Default port 5555 if not specified

echo "Deploying Honk app to device (Port: $PORT)..."

# Check for connected devices (USB or wireless)
if ! adb devices | grep -q "device$\|:$PORT device$"; then
    echo "No device connected! Please either:"
    echo "1. Connect your device via USB, or"
    echo "2. Enable wireless debugging on your device and connect using:"
    echo "   adb connect <DEVICE_IP>:$PORT"
    exit 1
fi

# Install the app
adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ $? -eq 0 ]; then
    echo "App deployed successfully!"
    # Launch the app
    adb shell am start -n "com.example.honk/.MainActivity"
    adb logcat | grep "MainActivity\|RecorderActivity"
else
    echo "Deployment failed!"
    exit 1
fi
