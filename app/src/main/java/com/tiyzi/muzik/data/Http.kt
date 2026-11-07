package com.tiyzi.muzik.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object Http {
    const val USER_AGENT = "TiyziMuzik/2.0"

    fun getText(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("Sunucu hatası: ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
