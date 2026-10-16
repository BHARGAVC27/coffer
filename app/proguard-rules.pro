# Strip all logging from release builds so nothing sensitive can reach logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Bouncy Castle references JNDI/LDAP classes that don't exist on Android; we only use the
# lightweight Argon2 API (plus the reader that upgrades 1.0-format vaults).
-dontwarn javax.naming.**
