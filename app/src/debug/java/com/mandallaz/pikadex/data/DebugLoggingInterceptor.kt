package com.mandallaz.pikadex.data

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Real request/response logging for a debug build. See the release variant of this file — the
 *  split between build-type source sets, not a runtime BuildConfig.DEBUG check, is what lets
 *  logging-interceptor be debugImplementation-only: a release-variant compile never needs to
 *  resolve HttpLoggingInterceptor at all. */
internal fun debugLoggingInterceptor(): Interceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BASIC
}
