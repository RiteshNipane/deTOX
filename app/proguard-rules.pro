# kotlinx.serialization: keep generated serializers for the profile config models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.detox.detector.** {
    *** Companion;
}
-keepclasseswithmembers class com.detox.detector.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.detox.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.detox.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The private SlopMeta model in ml/ is decoded with kotlinx.serialization too.
-keep class com.detox.ml.SlopMeta { *; }
-keep class com.detox.ml.SlopMeta$$serializer { *; }

# Accessibility service is bound by name from the manifest.
-keep class com.detox.service.** { *; }
