# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Google API Client and HTTP Client rules
-keep class com.google.api.client.** { *; }
-keep interface com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.services.drive.**

-keep class com.google.http.client.** { *; }
-dontwarn com.google.http.client.**

# Common missing classes in Google libraries
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.apache.http.**
-dontwarn android.test.**
-dontwarn com.google.errorprone.annotations.**

# If you use Gson with these libraries
-keep class com.google.api.client.json.gson.** { *; }

# Guava often used by Google API clients
-dontwarn com.google.common.**
-keep class com.google.common.** { *; }
