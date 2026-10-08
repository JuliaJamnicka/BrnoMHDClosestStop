# Wear Engine SDK uses AIDL binders and reflection
-keep class com.huawei.wearengine.** { *; }
-keep class com.huawei.hmf.** { *; }
-dontwarn com.huawei.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; *; }
