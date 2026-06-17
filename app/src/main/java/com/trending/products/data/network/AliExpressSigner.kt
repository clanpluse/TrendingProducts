package com.trending.products.data.network

import java.security.MessageDigest

object AliExpressSigner {
    fun sign(appSecret: String, params: Map<String, String>): String {
        val sorted = params.entries.sortedBy { it.key }
        val raw = buildString {
            append(appSecret)
            sorted.forEach { (k, v) -> append(k).append(v) }
            append(appSecret)
        }
        return md5(raw).uppercase()
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
