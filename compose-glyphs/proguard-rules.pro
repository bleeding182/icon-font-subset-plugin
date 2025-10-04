# Add project specific ProGuard rules here.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FontPathExtractor and its native methods
-keep class com.davidmedenjak.fontsubsetting.runtime.FontPathExtractor {
    native <methods>;
    *;
}

# Keep data classes used with JNI
-keep class com.davidmedenjak.fontsubsetting.runtime.** {
    *;
}
