package com.varian.mappercore.client

import com.varian.mappercore.framework.helper.CloverLogger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import retrofit2.Response
import java.util.concurrent.CompletableFuture

abstract class ServiceClientBase(var retrofitProvider: RetrofitProvider) {
    protected var log: Logger = LogManager.getLogger(ServiceClientBase::class.java)

    fun <T> getRetrofit(classz: Class<T>): T {
        return retrofitProvider.provide().create(classz)
    }

    fun <T> call(call: CompletableFuture<Response<T>>): T? {
        var response = call.get()
        if (response.isSuccessful)
            return response.body()
        else {
            log.error(response.message())
            log.error(response.errorBody()?.byteStream().toString())
            throw Exception(response.errorBody()?.byteStream().toString())
        }
    }
}