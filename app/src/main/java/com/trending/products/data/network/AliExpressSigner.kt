package com.trending.products.data.network

import java.security.MessageDigest

/**
 * Generates the MD5 signature required by AliExpress Affiliate API.
 * Docs: https://developers.aliexpress.com/en/doc.htm?docId=45788
 */
object AliExpressSigner {

    fun sign(
        appSecret: String,
        params: Map<String, String>
    ): String {
        // 1. Sort parameters alphabetically
        val sorted = params.entries.sortedBy { it.key }

        // 2. Concatenate: SECRET + key1value1key2value2... + SECRET
        val raw = buildString {
            append(appSecret)
            sorted.forEach { (k, v) -> append(k).append(v) }
            append(appSecret)
        }

        // 3. MD5 → uppercase hex
        return md5(raw).uppercase()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
