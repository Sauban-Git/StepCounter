# --- 1. Retrofit & Gson (Critical) ---
# Keep Generic signatures (Retrofit needs this to understand Call<Response>)
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit interfaces
-keep,allowobfuscation interface com.softyzen.queuecut.data.api.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- 2. Data Models (The Silent Killers) ---
# If R8 removes these or renames fields, Gson mapping fails silently
-keep class com.softyzen.queuecut.data.model.** { *; }
-keepclassmembers class com.softyzen.queuecut.data.model.** { <fields>; }

# --- 3. OkHttp & Retrofit Internal ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class kotlin.Metadata { *; }

# --- 4. Parcelable (if used in models) ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
