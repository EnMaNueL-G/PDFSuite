# OptiSuite PDF — reglas de R8

# PdfBox-Android (Apache-2.0): carga clases y recursos (fuentes, glyphlist) por reflexión/nombre
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# Kotlin
-keepattributes *Annotation*
-keepclassmembers class ** {
    @kotlin.jvm.JvmField *;
}

# Android Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
