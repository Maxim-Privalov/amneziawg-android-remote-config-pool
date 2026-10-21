
# Pool / AuthUser are persisted through Gson by field name, Ks* are Retrofit request bodies:
# obfuscated field names would break stored pools between builds.
-keepclassmembers class com.remotepool.** { <fields>; }
