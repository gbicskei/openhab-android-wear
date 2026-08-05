# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class org.openhab.habdroid.wear.data.model.**$$serializer { *; }
-keepclassmembers class org.openhab.habdroid.wear.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class org.openhab.habdroid.wear.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-keep class coil.** { *; }

# ProtoLayout — R8 incorrectly marks Fingerprint fields as final, causing
# IllegalAccessError at runtime when the builder pattern mutates them.
-keep class androidx.wear.protolayout.expression.Fingerprint { *; }
-keep class androidx.wear.protolayout.** { *; }
