package com.kdownloader.internal

import android.content.Context
import com.kdownloader.Status
import com.kdownloader.database.DbHelper
import com.kdownloader.database.DownloadModel
import com.kdownloader.internal.storage.DefaultStorageResolver
import com.kdownloader.utils.getPath
import kotlinx.coroutines.*

class DownloadDispatchers(
    private val dbHelper: DbHelper,
    private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main +
            CoroutineExceptionHandler { _, _ ->

            })

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, _ ->

            })

    fun enqueue(req: DownloadRequest): Int {
        val job = scope.launch {
            execute(req)
        }
        req.job = job
        return req.downloadId
    }

    private suspend fun execute(request: DownloadRequest) {
        DownloadTask(request, dbHelper, context).run(
            onStart = {
                executeOnMainThread { request.listener?.onStart() }
            },
            onProgress = {
                executeOnMainThread { request.listener?.onProgress(it) }
            },
            onPause = {
                executeOnMainThread { request.listener?.onPause() }
            },
            onCompleted = {
                executeOnMainThread { request.listener?.onCompleted() }
            },
            onError = {
                executeOnMainThread { request.listener?.onError(it) }
            }
        )
    }

    private fun executeOnMainThread(block: () -> Unit) {
        scope.launch {
            block()
        }
    }

    fun cancel(req: DownloadRequest) {

        if (req.status == Status.PAUSED) {
            DefaultStorageResolver(context).deleteFile(req.filePath)
            req.reset()
        }

        req.status = Status.CANCELLED
        req.job.cancel()

        req.listener?.onError("Cancelled")

        dbScope.launch {
            dbHelper.remove(req.downloadId)
        }
    }

    fun cancelAll() {
        scope.cancel()
        dbScope.launch {
            dbHelper.empty()
        }
    }

    fun cleanup(days: Int) {
        dbScope.launch {
            val models: List<DownloadModel>? = dbHelper.getUnwantedModels(days)
            if (models != null) {
                for (model in models) {
                    val filePath: String = getPath(
                        model.dirPath,
                        model.fileName
                    )
                    dbHelper.remove(model.id)
                    DefaultStorageResolver(context).deleteFile(filePath)
                }
            }
        }
    }
}