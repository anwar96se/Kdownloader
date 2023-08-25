package com.kdownloader

import android.content.Context
import android.net.Uri
import com.kdownloader.database.AppDbHelper
import com.kdownloader.database.DbHelper
import com.kdownloader.database.NoOpsDbHelper
import com.kdownloader.internal.DownloadDispatchers
import com.kdownloader.internal.DownloadRequest
import com.kdownloader.internal.DownloadRequestQueue
import com.kdownloader.internal.listener.DownloadListener
import com.kdownloader.internal.listener.ListenerReference
import java.io.File

class KDownloader private constructor(
    context: Context,
    dbHelper: DbHelper,
    private val config: DownloaderConfig
) {

    companion object {
        fun create(
            context: Context,
            config: DownloaderConfig = DownloaderConfig(true)
        ): KDownloader {
            return if (config.databaseEnabled) {
                KDownloader(context, AppDbHelper(context), config)
            } else {
                KDownloader(context, NoOpsDbHelper(), config)
            }
        }
    }

    private val downloader = DownloadDispatchers(dbHelper, context)
    private val reqQueue = DownloadRequestQueue(downloader)

    fun newRequestBuilder(url: String, filePath: String): DownloadRequest.Builder {
        return DownloadRequest.Builder(url, filePath)
            .readTimeout(config.readTimeOut)
            .connectTimeout(config.connectTimeOut)
    }

    fun newRequestBuilder(url: String, file: File): DownloadRequest.Builder {
        return newRequestBuilder(url, file.absolutePath)
    }

    fun newRequestBuilder(url: String, uri: Uri): DownloadRequest.Builder {
        return newRequestBuilder(url, uri.toString())
    }

    fun enqueue(req: DownloadRequest, listener: DownloadListener): Int {
        req.listener = listener
        return reqQueue.enqueue(req)
    }

    inline fun enqueue(
        req: DownloadRequest,
        crossinline onStart: () -> Unit = {},
        crossinline onProgress: (total: Long, downloaded: Long, progress: Int) -> Unit = { _, _, _ -> },
        crossinline onPause: () -> Unit = {},
        crossinline onError: (error: String) -> Unit = { _ -> },
        crossinline onCompleted: (path: String) -> Unit = {}
    ) = enqueue(req, object : DownloadListener {
        override fun onStart(req: DownloadRequest) = onStart()
        override fun onProgress(req: DownloadRequest, progress: Int) =
            onProgress(req.totalBytes, req.downloadedBytes, progress)

        override fun onPause(req: DownloadRequest) = onPause()
        override fun onError(req: DownloadRequest, error: String) = onError(error)
        override fun onCompleted(req: DownloadRequest, path: String) = onCompleted(path)
    })

    fun status(id: Int): Status {
        return reqQueue.status(id)
    }

    fun cancel(id: Int) {
        reqQueue.cancel(id)
    }

    fun cancel(tag: String) {
        reqQueue.cancel(tag)
    }

    fun cancelAll() {
        reqQueue.cancelAll()
    }

    fun pause(id: Int) {
        reqQueue.pause(id)
    }

    fun resume(id: Int) {
        reqQueue.resume(id)
    }

    fun cleanUp(days: Int) {
        downloader.cleanup(days)

    }

    inline fun addListener(
        requestId: Int? = null,
        tag: String? = null,
        removeOnFinish: Boolean = false,
        crossinline onStart: () -> Unit = {},
        crossinline onProgress: (total: Long, downloaded: Long, progress: Int) -> Unit = { _, _, _ -> },
        crossinline onPause: () -> Unit = {},
        crossinline onError: (error: String) -> Unit = { _ -> },
        crossinline onCompleted: (path: String) -> Unit = {}
    ) = addListener(
        ListenerReference(
            requestId = requestId,
            tag = tag,
            removeOnFinish = removeOnFinish,
            listener = object : DownloadListener {
                override fun onStart(req: DownloadRequest) = onStart()
                override fun onProgress(req: DownloadRequest, progress: Int) =
                    onProgress(req.totalBytes, req.downloadedBytes, progress)

                override fun onPause(req: DownloadRequest) = onPause()
                override fun onCompleted(req: DownloadRequest, path: String) = onCompleted(path)
                override fun onError(req: DownloadRequest, error: String) = onError(error)
            })
    )

    fun addListener(listener: ListenerReference) =
        downloader.appListener.addListener(listener)

    fun removeListener(listenerId: Int) = downloader.appListener.removeListener(listenerId)

    fun removeListenersByTag(tag: String) = downloader.appListener.removeListenersByTag(tag)

    fun getAllListeners() = downloader.appListener.getAllListeners()

    fun getListenersByTag(tag: String) = downloader.appListener.getListenersByTag(tag)

}