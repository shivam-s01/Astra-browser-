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

# Astra: keep Room entities/DAOs and our JS bridge intact in release builds
-keep class com.astra.browser.data.local.entity.** { *; }
-keep class com.astra.browser.core.media.MediaPlaybackBridge$JsInterface { *; }
-keepclassmembers class com.astra.browser.core.media.MediaPlaybackBridge$JsInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep every @JavascriptInterface method on ANY class (obfuscation would rename
# them and the page could no longer call them in release builds).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.astra.browser.core.engine.PageScripts { *; }
