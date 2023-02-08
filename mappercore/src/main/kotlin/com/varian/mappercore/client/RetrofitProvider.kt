package com.varian.mappercore.client

import com.varian.mappercore.client.TokenManager
import com.varian.mappercore.configuration.Configuration
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient


class RetrofitProvider(private var config: Configuration, private var tokenManager: TokenManager,private var baseUrl:String) {

    fun provide(): Retrofit {

        val okhttpClientBuilder = OkHttpClient.Builder()
            .connectTimeout(config.interfaceServiceConnectionTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(config.interfaceServiceReadTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(config.interfaceServiceWriteTimeout, TimeUnit.MILLISECONDS)
            .addInterceptor(Interceptor { chain ->
                val token = tokenManager.getToken()
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token").build()
                )
            }).build()

        return Retrofit.Builder()
            .addConverterFactory(JacksonConverterFactory.create())
            .baseUrl(baseUrl)
            .client(okhttpClientBuilder)
            .build()
    }
}
