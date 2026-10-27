# Room entities and DAOs are kept by Room's generated code.
-keep class com.rupeeflow.app.data.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses
# Strip verbose/debug logs in release so no transaction details are ever logged.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
