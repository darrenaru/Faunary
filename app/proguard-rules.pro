# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }

# ML Kit discovers its components reflectively through no-arg constructors; R8 full mode strips
# them otherwise ("NoSuchMethodException ...Registrar.<init>") and on-device detection never starts.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.**.*Registrar { <init>(); }

# commons-compress (used by jbsdiff for bzip2 update patches) has optional codecs we don't ship.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
