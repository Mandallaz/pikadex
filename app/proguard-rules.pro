# Gson deserializes PokeAPI/GraphQL responses via reflection, matching field names (or
# @SerializedName) against JSON keys. R8 renaming/removing those fields breaks parsing silently —
# it returns nulls instead of crashing — so every DTO field must survive minification verbatim.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.squareup.moshi.Json <fields>;
}

# All PokeAPI/GraphQL response DTOs (including the private nested data classes in
# PokeApiGraphQLDataSource, e.g. MoveInfo, which JsonDiskCache also deserializes via a Gson
# TypeToken) — pure data holders, nothing to gain from shrinking them. A missing field here isn't
# a crash, just a silent null. To narrow down keep rules to only serialized DTO fields (F111),
# we only keep the DTO packages, the private nested DTO classes inside PokeApiGraphQLDataSource,
# and the persisted PokemonDetailBundle class.
-keep class com.mandallaz.pikadex.**.dto.** { <fields>; }
-keep class com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource$* { <fields>; }
-keep class com.mandallaz.pikadex.data.repository.PokemonDetailBundle { <fields>; }

-dontwarn sun.misc.**

# Gson's TypeToken (used directly in PokedexRepository/JsonDiskCache to describe the
# Map<String, ...> shapes read back from disk) reads its own generic superclass signature via
# reflection at runtime to know which type it represents. Without this rule R8 strips that
# signature and TypeToken throws IllegalStateException at construction time — a real crash this
# app hit and fixed, not a hypothetical.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Retrofit — OkHttp and Retrofit each ship their own consumer proguard rules bundled in their AARs,
# so blanket -dontwarn here would only hide genuinely missing classes, not add anything they don't
# already declare for themselves.
-keepattributes Exceptions
-keep,allowobfuscation interface com.mandallaz.pikadex.data.remote.PokeApiService
