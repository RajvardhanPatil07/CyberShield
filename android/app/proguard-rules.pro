# CyberShield release shrinking rules.
# ML Kit, CameraX, AppCompat and Compose ship their own consumer rules.
# The app uses no reflection-based serialization, so no extra keep rules are required.

# Strip verbose/debug logging calls from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
