# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# MediaPipe specific rules
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# Enhanced MediaPipe and model management rules for modular architecture
-keep class com.gemma3n.app.ModelManager { *; }
-keep class com.gemma3n.app.ModelDownloadManager { *; }
-keep class com.gemma3n.app.ModelManager$ModelStatus { *; }
-keep class com.gemma3n.app.PermissionHandler { *; }
-keep class com.gemma3n.app.ImageProcessor { *; }
-keep class com.gemma3n.app.UIStateManager { *; }

# Keep all public methods in our app classes
-keepclassmembers class com.gemma3n.app.** {
    public <methods>;
}

# OkHttp3 rules for model downloads
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin Coroutines rules
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Gson rules for JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
