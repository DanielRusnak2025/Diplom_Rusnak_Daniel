package org.example.androidapp.network

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
) : Exception(message)
