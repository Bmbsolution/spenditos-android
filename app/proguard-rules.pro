# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes for serialization
-keep class com.bmbsolution.spenditos.data.model.** { *; }
-keep class com.bmbsolution.spenditos.data.local.entity.** { *; }

# Keep Retrofit and OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.squareup.okhttp3.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.** { *; }

# Keep Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }

# Keep BuildConfig
-keep class com.bmbsolution.spenditos.BuildConfig { *; }
