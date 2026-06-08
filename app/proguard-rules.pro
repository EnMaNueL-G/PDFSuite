# PDFSuite ProGuard rules

# iTextG (iText 5 for Android)
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# Kotlin
-keepattributes *Annotation*
-keepclassmembers class ** {
    @kotlin.jvm.JvmField *;
}

# Android Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
