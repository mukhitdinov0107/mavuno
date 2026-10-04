# LiteRT uses JNI into these classes.
-keep class org.tensorflow.lite.** { *; }
# SQLCipher JNI
-keep class net.zetetic.database.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class org.mavuno.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class org.mavuno.**$$serializer { *; }
-keepclassmembers class org.mavuno.** { *** Companion; }
