package com.kdownloader.utils

import com.kdownloader.Constants
import com.kdownloader.httpclient.DefaultHttpClient
import com.kdownloader.httpclient.HttpClient
import com.kdownloader.internal.DownloadRequest
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.experimental.and

private const val MAX_REDIRECTION = 10

fun getPath(dirPath: String, fileName: String): String {
    return dirPath + File.separator + fileName
}

val String.withTemp: String get() = if (isUriPath()) this else "$this.temp"

fun String.isUriPath(): Boolean =
    isNotEmpty() && (startsWith("content://") || startsWith("file://"))

fun String.isContentPath(): Boolean =
    isNotEmpty() && startsWith("content://")

private fun isRedirection(code: Int): Boolean {
    return code == HttpURLConnection.HTTP_MOVED_PERM
            || code == HttpURLConnection.HTTP_MOVED_TEMP
            || code == HttpURLConnection.HTTP_SEE_OTHER
            || code == HttpURLConnection.HTTP_MULT_CHOICE
            || code == Constants.HTTP_TEMPORARY_REDIRECT
            || code == Constants.HTTP_PERMANENT_REDIRECT
}

@Throws(IOException::class, IllegalAccessException::class)
fun getRedirectedConnectionIfAny(
    httpClient0: HttpClient,
    req: DownloadRequest
): HttpClient {
    var httpClient: HttpClient = httpClient0
    var redirectTimes = 0
    var code: Int = httpClient.getResponseCode()
    var location: String? = httpClient.getResponseHeader("Location")
    while (isRedirection(code)) {
        if (location == null) {
            throw IllegalAccessException("Location is null")
        }
        httpClient.close()
        req.url = (location)
        httpClient = DefaultHttpClient().clone()
        httpClient.connect(req)
        code = httpClient.getResponseCode()
        location = httpClient.getResponseHeader("Location")
        redirectTimes++
        if (redirectTimes >= MAX_REDIRECTION) {
            throw IllegalAccessException("Max redirection done")
        }
    }
    return httpClient
}

fun getUniqueId(url: String, filePath: String): Int {
    val string = url + File.separator + filePath
    val hash: ByteArray = try {
        MessageDigest.getInstance("MD5").digest(string.toByteArray(charset("UTF-8")))
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("NoSuchAlgorithmException", e)
    } catch (e: UnsupportedEncodingException) {
        throw RuntimeException("UnsupportedEncodingException", e)
    }
    val hex = StringBuilder(hash.size * 2)
    for (b in hash) {
        if (b and 0xFF.toByte() < 0x10) hex.append("0")
        hex.append(Integer.toHexString((b and 0xFF.toByte()).toInt()))
    }
    return hex.toString().hashCode()
}
