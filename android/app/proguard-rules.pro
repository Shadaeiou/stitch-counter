# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.shadaeiou.stitchcounter.**$$serializer { *; }
-keepclassmembers class com.shadaeiou.stitchcounter.** {
    *** Companion;
}
-keepclasseswithmembers class com.shadaeiou.stitchcounter.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
