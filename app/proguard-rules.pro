# SPDX-License-Identifier: MPL-2.0
# Compose and kotlinx.serialization ship their own consumer rules, and R8 handles the rest. Add rules
# here only with the release-build failure that motivated them.

# Vault (libsodium through Lazysodium and JNA). JNA binds `native` methods by name at run time
# (Native.register) and reads its own classes reflectively, so neither may be renamed or stripped.
# JNA also references desktop-only java.awt types that do not exist on Android.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.goterl.lazysodium.** { *; }
-dontwarn java.awt.**
-dontwarn org.slf4j.**
