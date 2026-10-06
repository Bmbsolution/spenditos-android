# kotlinx.serialization: keep generated serializers for @Serializable wire models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.spenditos.android.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spenditos.android.**$$serializer { *; }
# Retrofit service interfaces are proxied at runtime.
-keep,allowobfuscation interface com.spenditos.android.**.*Api
