#!/bin/bash

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    adb emu kill >/dev/null 2>&1
}

# trap cleanup EXIT

# Use specific AVD instead of auto-selecting
AVD_NAME="Small_Phone_API_31"

# Verify AVD exists
if ! emulator -list-avds | grep -q "^${AVD_NAME}$"; then
    echo "AVD '${AVD_NAME}' not found!"
    exit 1
fi

# Check for existing snapshot
SNAPSHOT_NAME="quickboot"
SNAPSHOT_DIR="$HOME/.android/avd/${AVD_NAME}.avd/snapshots/${SNAPSHOT_NAME}"
echo "Checking for snapshot at: $SNAPSHOT_DIR"

# Verify snapshots are enabled and exist
if [ ! -d "$SNAPSHOT_DIR" ] || [ ! -f "$SNAPSHOT_DIR/snapshot.pb" ]; then
    echo "Initial snapshot not found. Creating one..."
    # Start emulator without snapshot
    emulator -avd "$AVD_NAME" \
        -no-snapshot-load \
        -wipe-data \
        -no-boot-anim \
        -logcat "*:W" \
        -gpu swiftshader_indirect \
        >/tmp/emu.log 2>&1 &

    # Wait for complete boot
    adb wait-for-device
    until adb shell getprop sys.boot_completed 2>/dev/null | grep -q '^1'; do
        echo -n "."
        sleep 1
    done

    # Create snapshot (updated command)
    sleep 5 # Give additional time for system stabilization
    adb emu avd snapshot save "$SNAPSHOT_NAME" || {
        echo "Failed to create snapshot!"
        exit 1
    }
    adb emu kill
    sleep 2
fi

echo "Starting emulator with snapshot..."
emulator -avd "$AVD_NAME" \
    -no-boot-anim \
    -logcat "*:W" \
    -snapshot "$SNAPSHOT_NAME" \
    -gpu swiftshader_indirect \
    >/tmp/emu.log 2>&1 &

echo "Waiting for device to boot..."
adb wait-for-device

# Check boot completion
echo -n "Waiting for system boot completion"
until adb shell getprop sys.boot_completed 2>/dev/null | grep -q '^1'; do
    echo -n "."
    sleep 1
done
echo -e "\nDevice ready!"

echo "Installing app..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Launching app and streaming logs..."
adb shell am start -n "com.example.honk/.MainActivity"

# Stream only our app's logs with debug level and higher
adb logcat -s "com.example.honk:*" "*:E" | grep com.example.honk &
LOGCAT_PID=$!

# Update cleanup function to kill logcat
cleanup() {
    echo "Cleaning up..."
    kill $LOGCAT_PID 2>/dev/null
    adb emu kill >/dev/null 2>&1
}

# Enable cleanup on script exit
trap cleanup EXIT

# Wait for logcat process
wait $LOGCAT_PID
