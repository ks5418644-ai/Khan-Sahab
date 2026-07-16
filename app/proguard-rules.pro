# ProGuard rules for Google AdMob SDK integration

# Keep Google Play Services and AdMob classes
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep interface com.google.android.gms.common.** { *; }

# Preserve annotations, signatures and inner class metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Handle WebView and JavaScript interfaces (crucial for AdMob web-views)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep layout and resource references
-keepclassmembers class **.R$* {
    public static <fields>;
}
