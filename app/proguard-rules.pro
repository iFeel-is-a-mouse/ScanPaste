# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep data classes used by ViewModel
-keep class com.scanpaste.app.model.** { *; }

# Keep ViewModel constructors for reflection
-keepclassmembers class com.scanpaste.app.viewmodel.** {
    public <init>(...);
}
