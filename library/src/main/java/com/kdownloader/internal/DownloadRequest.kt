package com.kdownloader.internal

import com.kdownloader.Constants
import com.kdownloader.Status
import com.kdownloader.internal.listener.DownloadListener
import com.kdownloader.utils.getUniqueId
import kotlinx.coroutines.Job

class DownloadRequest private constructor(
    val downloadId: Int,
    val tag: String?,
    internal var url: String,
    internal var listener: DownloadListener?,
    internal val headers: HashMap<String, List<String>>?,
    internal val filePath: String,
    internal var enqueueAction: Int,
    internal var status: Status = Status.UNKNOWN,
    internal var readTimeOut: Int = 0,
    internal var connectTimeOut: Int = 0,
    internal var userAgent: String = Constants.DEFAULT_USER_AGENT
) {

    var totalBytes: Long = 0
    var downloadedBytes: Long = 0
    internal lateinit var job: Job

    data class Builder(
        private val url: String,
        private val filePath: String,
    ) {

        private var tag: String? = null
        private var listener: DownloadListener? = null
        private var headers: HashMap<String, List<String>>? = null
        private var enqueueAction: Int = 1
        private var readTimeOut: Int = Constants.DEFAULT_READ_TIMEOUT_IN_MILLS
        private var connectTimeOut: Int = Constants.DEFAULT_CONNECT_TIMEOUT_IN_MILLS
        private var userAgent: String = Constants.DEFAULT_USER_AGENT

        fun tag(tag: String) = apply {
            this.tag = tag
        }

        fun headers(headers: HashMap<String, List<String>>) = apply {
            this.headers = headers
        }

        fun increment(increment: Boolean = true) = apply {
            this.enqueueAction = if (increment) 1 else 0
        }

        fun readTimeout(timeout: Int) = apply {
            this.readTimeOut = timeout
        }

        fun connectTimeout(timeout: Int) = apply {
            this.connectTimeOut = timeout
        }

        fun userAgent(userAgent: String) = apply {
            this@Builder.userAgent = userAgent
        }

        fun build(): DownloadRequest {
            return DownloadRequest(
                url = url,
                tag = tag,
                listener = listener,
                headers = headers,
                filePath = filePath,
                downloadId = getUniqueId(url, filePath),
                enqueueAction = enqueueAction,
                readTimeOut = readTimeOut,
                connectTimeOut = connectTimeOut,
                userAgent = userAgent
            )
        }
    }

    fun reset() {
        downloadedBytes = 0
        totalBytes = 0
        status = Status.UNKNOWN
    }

}