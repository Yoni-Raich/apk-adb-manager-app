# ==============================================================================
# General & Debuggability Rules
# ==============================================================================
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ==============================================================================
# Kadb (ADB Wireless TLS & Shell Client)
# ==============================================================================
-keep class com.flyfishxu.kadb.** { *; }
-keepclassmembers class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# ==============================================================================
# Bouncy Castle (JCE/JCA Security Provider & TLS Key Generation)
# ==============================================================================
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class * extends java.security.Provider { *; }
-keepclassmembers class * extends java.security.Provider {
    public <init>(...);
}

# ==============================================================================
# Okio & Java NIO / Concurrency
# ==============================================================================
-dontwarn okio.**
-dontwarn java.nio.file.**
-dontwarn sun.misc.Unsafe
-keepclassmembers class * extends okio.ByteString {
    <fields>;
    <methods>;
}

# ==============================================================================
# Kotlinx Coroutines
# ==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ==============================================================================
# AndroidX Lifecycle & ViewModel
# ==============================================================================
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
}

# ==============================================================================
# AndroidX DataStore Preferences (Protobuf Lite)
# ==============================================================================
-dontwarn androidx.datastore.**
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

# ==============================================================================
# App Data Models & JSON Serialization
# ==============================================================================
-keep class com.apkmanager.app.data.** { *; }
-keepclassmembers class com.apkmanager.app.data.** {
    <fields>;
    <methods>;
}
