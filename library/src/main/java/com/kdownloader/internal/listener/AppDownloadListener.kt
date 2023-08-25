package com.kdownloader.internal.listener

import com.kdownloader.internal.DownloadRequest
import java.util.*

class AppDownloadListener {

    private val listeners = mutableMapOf<Int, ListenerReference>()

    val mainListener = object : Listener {
        override fun onStart(req: DownloadRequest) {
            listeners.notify(req) { it.listener.onStart(req) }
        }

        override fun onProgress(req: DownloadRequest, progress: Int) {
            listeners.notify(req) { it.listener.onProgress(req, progress) }
        }

        override fun onPause(req: DownloadRequest) {
            listeners.notify(req) { it.listener.onPause(req) }
        }

        override fun onCompleted(req: DownloadRequest, path: String) {
            listeners.notify(req) {
                it.listener.onCompleted(req, path)
                stopNotify(it)
            }
        }

        override fun onError(req: DownloadRequest, error: String) {
            listeners.notify(req) {
                it.listener.onError(req, error)
                stopNotify(it)
            }
        }

    }

    fun Map<Int, ListenerReference>.notify(
        req: DownloadRequest,
        action: (ListenerReference) -> Unit,
    ) {
        forEach { entry ->
            if (entry.value.requestId == null || req.downloadId == entry.value.requestId)
                action.invoke(entry.value)
        }
    }

    fun stopNotify(ref: ListenerReference) {
        if (ref.removeOnFinish) removeListener(ref.listenerId)
    }

    fun addListener(
        listener: ListenerReference
    ): Int {
        val id = UUID.randomUUID().hashCode()
        listeners[id] = listener.copy(listenerId = id)
        return id
    }

    fun removeListener(listenerId: Int) {
        if (listeners.containsKey(listenerId))
            listeners.remove(listenerId)
    }

    fun getAllListeners() = mutableMapOf<Int, ListenerReference>().apply {
        putAll(listeners)
    }.toMap()

    interface Listener {
        fun onStart(req: DownloadRequest)
        fun onProgress(req: DownloadRequest, progress: Int)
        fun onPause(req: DownloadRequest)
        fun onCompleted(req: DownloadRequest, path: String)
        fun onError(req: DownloadRequest, error: String)
    }
}

typealias DownloadListener = AppDownloadListener.Listener

data class ListenerReference(
    val listenerId: Int = 0,
    val requestId: Int? = null,
    val removeOnFinish: Boolean = false,
    val listener: DownloadListener,
)