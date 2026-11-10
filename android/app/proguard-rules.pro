# MZAZI TECH AUTOMATED MACHINE — release shrinking rules.

# Room generates implementations reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Retrofit interfaces are proxy-based; keep their signatures and annotations.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Gson DTOs are serialised by field name.
-keepclassmembers,allowobfuscation class com.mzazitech.automatedmachine.data.remote.** {
    <fields>;
}
-keep class com.mzazitech.automatedmachine.data.remote.** { *; }

# Coroutines internals
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep line numbers so production crashes stay diagnosable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
