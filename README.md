# Zameen Naap (زمین ناپ): offline land measurement

Build on Mac:
    cd ~/ZameenNaap
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    chmod +x gradlew && ./gradlew assembleDebug
APK: app/build/outputs/apk/debug/app-debug.apk

Units: 1 karam = 5.5 ft, 1 sarsai = 30.25 sq ft, 1 marla = 9 sarsai = 272.25 sq ft
(or 225 for housing societies), 1 kanal = 20 marla, 1 acre = 8 kanal, 1 murabba = 25 acre,
Sindh ghunta = 1/40 acre.
