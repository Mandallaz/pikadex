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

# B64 — Moshi's kotlin-codegen (KSP) generates a separate `Outer_InnerJsonAdapter` class per
# @JsonClass type at compile time, looked up reflectively by constructed class name at runtime
# with no static reference anywhere in the code. Moshi ships no consumer proguard rules for this
# (documented codegen limitation), so R8's tree-shaker removed every generated adapter for the
# GraphQL DTOs nested in PokeApiGraphQLDataSource, breaking every feature that depends on GraphQL
# data (team matchups, dex base-stats filter) with a misleading "network error" in the release
# build only. Moshi's own recommended rule pair, including the nested-class variant since these
# DTOs live inside PokeApiGraphQLDataSource rather than at the top level.
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>(...);
}
-if @com.squareup.moshi.JsonClass class **$*
-keep class <1>_<2>JsonAdapter {
    <init>(...);
}

# B63 — WorkManager's internal WorkDatabase (a Room database) is instantiated reflectively by
# Room at runtime via its generated `*_Impl` class name and no-arg constructor. R8 strips that
# constructor under isMinifyEnabled, which crashed the app at launch (NoSuchMethodException:
# WorkDatabase_Impl.<init>) before MainActivity ever opened, since WorkManager initializes via
# androidx.startup at process start. Room's own consumer rules don't cover this case with the
# "optimize" R8 config this app uses, so keep it explicitly.
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(...); }

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
