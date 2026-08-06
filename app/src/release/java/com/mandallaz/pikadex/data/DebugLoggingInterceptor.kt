package com.mandallaz.pikadex.data

import okhttp3.Interceptor

/** No-op counterpart of the debug variant of this file: logging-interceptor is
 *  debugImplementation-only, so a release build can't reference HttpLoggingInterceptor at all. */
internal fun debugLoggingInterceptor(): Interceptor? = null
