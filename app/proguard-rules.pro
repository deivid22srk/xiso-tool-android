# Mantém os métodos JNI e a ponte de callback usada pelo núcleo Rust.
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.deivid22srk.xisotool.native.** { *; }
-keep interface com.deivid22srk.xisotool.native.** { *; }
