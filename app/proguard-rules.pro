# Astra Browser release ProGuard rules

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Room
-keep class androidx.room.** { *; }

# WebView JS interfaces (none currently exposed, but keep annotation-safe)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
