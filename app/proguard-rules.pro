-repackageclasses
-ignorewarnings
-dontwarn
-dontnote

# Keep generic signatures and annotations for GSON / TypeToken
-keepattributes Signature,Annotation*
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements java.lang.reflect.ParameterizedType
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
