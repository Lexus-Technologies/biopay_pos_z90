# Keep all classes in your app package
-keep class com.billzone.biopay.** { *; }

# Keep Flutter MethodChannels
-keepclassmembers class io.flutter.plugin.common.MethodChannel$MethodCallHandler {
    <methods>;
}

# Keep ZXing QR/barcode functionality (used by ZCS SDK)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep Aratek fingerprint SDK classes
-keep class cn.com.aratek.fp.** { *; }
-dontwarn cn.com.aratek.fp.**

# Keep ZCS SDK classes (printer, card reader, etc.)
-keep class com.zcs.sdk.** { *; }
-dontwarn com.zcs.sdk.**

# Keep SourceAFIS biometric matcher
-keep class com.machinezoo.sourceafis.** { *; }
-dontwarn com.machinezoo.sourceafis.**

# Keep Java Base64
-keep class java.util.Base64 { *; }

# Keep NFC-related Android classes
-keep class android.nfc.** { *; }
-dontwarn android.nfc.**

# Keep AOSP Bitmap/Image classes
-keep class android.graphics.** { *; }

# Keep logging
-keep class android.util.Log { *; }

# Prevent warnings for Java desktop classes not used on Android
-dontwarn javax.smartcardio.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
 
# Keep Flutter auto-generated registrant
-keep class io.flutter.plugins.GeneratedPluginRegistrant { *; }

# Keep SLF4J logging classes to avoid logger binding warnings
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Suppress any additional dynamic class loading warnings
-dontwarn kotlin.**
-dontwarn org.jetbrains.kotlin.**

# Optional: retain enums and constants used via reflection
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.zcs.base.SmartPosJni{*;}
-keep class com.zcs.sdk.DriverManager{*;}
-keep class com.zcs.sdk.emv.**{*;}

