-keep class com.selavu.app.** { *; }
-keep class androidx.** { *; }
-keepclassmembers class * {
    public <init>(...);
}
-dontwarn androidx.**
