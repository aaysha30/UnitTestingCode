package com.varian.mappercore.framework.helper

import org.joda.time.DateTime

data class ExecutionTime(
    val description: String,
    val startTime: DateTime,
    val endTime: DateTime,
    val timeInMillis: Long
)
