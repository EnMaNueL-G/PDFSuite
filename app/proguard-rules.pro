# PDFReader ProGuard rules

# Keep PDFBox
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Keep Compose internals
-keep class androidx.compose.** { *; }

# Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class ** {
    @kotlin.jvm.JvmField *;
}

# General Android
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
