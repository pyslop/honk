#!/bin/bash

PORT=${1:-5555} # Default port 5555 if not specified

echo "Deploying Honk app to device (Port: $PORT)..."

# Get list of connected devices
DEVICES=$(adb devices | grep -v "List" | grep "device$\|:$PORT device$")
DEVICE_COUNT=$(echo "$DEVICES" | grep -c .)

if [ $DEVICE_COUNT -eq 0 ]; then
    echo "No device connected! Please either:"
    echo "1. Connect your device via USB, or"
    echo "2. Enable wireless debugging on your device and connect using:"
    echo "   adb connect <DEVICE_IP>:$PORT"
    exit 1
elif [ $DEVICE_COUNT -gt 1 ]; then
    echo "Multiple devices found. Please select one:"
    echo "$DEVICES" | nl
    read -p "Enter the number of the device to use: " DEVICE_NUM
    SELECTED_DEVICE=$(echo "$DEVICES" | sed -n "${DEVICE_NUM}p" | awk '{print $1}')
    adb -s $SELECTED_DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
else
    adb install -r app/build/outputs/apk/debug/app-debug.apk
fi

if [ $? -eq 0 ]; then
    echo "App deployed successfully!"
    # Launch the app on the selected device
    if [ ! -z "$SELECTED_DEVICE" ]; then
        adb -s $SELECTED_DEVICE shell am start -n "com.example.honk/.MainActivity"
    else
        adb shell am start -n "com.example.honk/.MainActivity"
    fi
else
    echo "Deployment failed!"
    exit 1
fi
