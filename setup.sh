#!/bin/bash

echo "Checking Android development dependencies..."

# Check if Java 17 is installed
if ! command -v java &>/dev/null || ! java -version 2>&1 | grep -q "version \"17"; then
    echo "Installing OpenJDK 17..."
    sudo apt update
    sudo apt install -y openjdk-17-jdk
fi

# Install required 32-bit libraries
echo "Installing required 32-bit libraries..."
sudo dpkg --add-architecture i386
sudo apt update
sudo apt install -y \
    libc6:i386 \
    libstdc++6:i386 \
    lib32z1 \
    lib32gcc-s1 \
    lib32ncurses6 \
    lib32z1

# Export JAVA_HOME
export JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which javac)")")")
if ! grep -q "JAVA_HOME" "$HOME/.bashrc"; then
    echo "export JAVA_HOME=$JAVA_HOME" >>"$HOME/.bashrc"
fi

# Function to find Android Studio archive
find_android_studio() {
    find "$HOME/Downloads" -name "android-studio-*.tar.gz" -type f -print | sort -r | head -n 1
}

# Check if Android Studio is installed
if [ ! -d "$HOME/android-studio" ]; then
    STUDIO_ARCHIVE=$(find_android_studio)
    if [ -n "$STUDIO_ARCHIVE" ]; then
        echo "Found Android Studio archive: $STUDIO_ARCHIVE"
        read -p "Install Android Studio? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo "Installing Android Studio..."
            cd "$HOME" || exit
            tar -xzf "$STUDIO_ARCHIVE"
            # Add Android Studio to PATH in .bashrc if not already present
            if ! grep -q "android-studio/bin" "$HOME/.bashrc"; then
                # shellcheck disable=SC2016
                echo 'export PATH="$HOME/android-studio/bin:$PATH"' >>"$HOME/.bashrc"
            fi
            # Create Android SDK directory
            mkdir -p "$HOME/Android/Sdk"
            # First run of studio.sh to trigger SDK installation
            echo "Starting Android Studio first run setup..."
            "$HOME/android-studio/bin/studio.sh" &
            echo "Please complete Android Studio setup and install SDK..."
            read -p "Press enter once Android Studio setup is complete..."
        fi
    else
        echo "Please download Android Studio from https://developer.android.com/studio"
        exit 1
    fi
fi

# Update SDK path check
if [ ! -d "$HOME/Android/Sdk" ]; then
    echo "Creating Android SDK directory..."
    mkdir -p "$HOME/Android/Sdk"
fi

# Check for command line tools with more detailed messages
CMDLINE_TOOLS="$HOME/Android/Sdk/cmdline-tools/latest/bin"
if [ ! -f "$CMDLINE_TOOLS/sdkmanager" ]; then
    echo "Android SDK Command-line tools not found"
    echo "Please open Android Studio and install them from:"
    echo "Tools -> SDK Manager -> SDK Tools -> Android SDK Command-line Tools"
    echo "Then restart this script"
    exit 1
fi

# Install build tools and platform tools if needed
"$CMDLINE_TOOLS"/sdkmanager --install "build-tools;33.0.0" "platform-tools" "platforms;android-33"

# Check and install Gradle
if ! command -v gradle &>/dev/null; then
    echo "Installing Gradle..."
    sudo apt update
    sudo apt install -y gradle
fi

# Create new Android project if it doesn't exist
if [ ! -d "app" ]; then
    echo "Creating new Android project..."

    # Create basic project structure
    mkdir -p app/src/main/java/com/example/honk
    mkdir -p app/src/main/res/layout
    mkdir -p app/src/main/res/values
    mkdir -p app/src/main/res/raw
    mkdir -p gradle/wrapper

    # Initialize Gradle wrapper properly
    gradle wrapper --gradle-version 8.2
    chmod +x gradlew

    # Create settings.gradle with proper syntax
    cat >settings.gradle <<'EOL'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "honk"
include ':app'
EOL

    # Create build.gradle with updated syntax
    cat >build.gradle <<'EOL'
// Top-level build file
plugins {
    id 'com.android.application' version '8.2.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.0' apply false
}
EOL

    # Update app/build.gradle with newer syntax
    cat >app/build.gradle <<'EOL'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.honk'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.honk"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildFeatures {
        viewBinding true
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'com.google.android.material:material:1.11.0'
}
EOL

    # Create local.properties with SDK path
    echo "sdk.dir=$HOME/Android/Sdk" >local.properties
fi

# Make all scripts executable
chmod +x setup.sh build.sh debug.sh deploy.sh

# After installing Android Studio, set ANDROID_HOME
if ! grep -q "ANDROID_HOME" "$HOME/.bashrc"; then
    echo 'export ANDROID_HOME="$HOME/Android/Sdk"' >>"$HOME/.bashrc"
    echo 'export PATH="$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH"' >>"$HOME/.bashrc"
fi

# Source the updated environment
source "$HOME/.bashrc"

echo "Setup complete! Android development environment is ready."
