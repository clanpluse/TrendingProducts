package com.trending.products.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    val googleTrendsOkHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val aliExpressApi: AliExpressApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api-sg.aliexpress.com/")
            .client(googleTrendsOkHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AliExpressApiService::class.java)
    }
}
