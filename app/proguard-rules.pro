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
