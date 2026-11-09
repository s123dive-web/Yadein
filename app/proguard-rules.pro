# Keep SQLCipher native bridge
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
# Keep Room generated classes & entities (Room handles most via its own rules)
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.yaadein.**$$serializer { *; }
-keepclassmembers class com.yaadein.** { *** Companion; }
-keepclasseswithmembers class com.yaadein.** { kotlinx.serialization.KSerializer serializer(...); }

# ---- Added for the minified beta/release builds ---------------------------------------------
# Argon2 (PIN hashing) is JNI: native code constructs Kotlin result objects by name.
-keep class com.lambdapioneer.argon2kt.** { *; }
# WorkManager stores worker class names in its database. Hilt workers have @AssistedInject constructors that the
# library's own keep rule does not match, so keep their names stable or queued work breaks after an update.
-keep class * extends androidx.work.ListenableWorker { *; }
# Broadcast receivers / widgets are referenced from the manifest (AAPT keeps names) but keep members for reflection-free safety.
-keep class * extends android.appwidget.AppWidgetProvider { *; }
# Media3 Transformer/ExoPlayer extensions loaded reflectively by DefaultRenderersFactory / DefaultEncoderFactory.
-dontwarn androidx.media3.**
# ML Kit / Play services pulls optional classes that are absent offline.
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**
# Kotlin enum valueOf used for persisted settings and routes.
-keepclassmembers enum com.yaadein.** { *; }
# Crash log and problem reports should show readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
