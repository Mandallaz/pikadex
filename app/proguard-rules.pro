# Gson deserializes PokeAPI/GraphQL responses via reflection, matching field names (or
# @SerializedName) against JSON keys. R8 renaming/removing those fields breaks parsing silently —
# it returns nulls instead of crashing — so every DTO field must survive minification verbatim.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# All PokeAPI/GraphQL response DTOs (including the private nested data classes in
# PokeApiGraphQLDataSource, e.g. MoveInfo, which JsonDiskCache also deserializes via a Gson
# TypeToken) — pure data holders, nothing to gain from shrinking them.
-keep class com.mandallaz.pikadex.data.remote.** { <fields>; }

-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Gson's TypeToken (used directly in PokedexRepository/JsonDiskCache to describe the
# Map<String, ...> shapes read back from disk) reads its own generic superclass signature via
# reflection at runtime to know which type it represents. Without this rule R8 strips that
# signature and TypeToken throws IllegalStateException at construction time — a real crash this
# app hit and fixed, not a hypothetical.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Retrofit
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep,allowobfuscation interface com.mandallaz.pikadex.data.remote.PokeApiService
