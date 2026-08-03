# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.openhab.habdroid.wear.shared.**$$serializer { *; }
-keepclassmembers class org.openhab.habdroid.wear.shared.** {
    *** Companion;
}
-keepclasseswithmembers class org.openhab.habdroid.wear.shared.** {
    kotlinx.serialization.KSerializer serializer(...);
}
