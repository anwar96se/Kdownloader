package com.kdownloader.internal

import android.content.Context
import com.kdownloader.Constants
import com.kdownloader.Status
import com.kdownloader.database.AppDbHelper
import com.kdownloader.database.DbHelper
import com.kdownloader.database.DownloadModel
import com.kdownloader.httpclient.DefaultHttpClient
import com.kdownloader.httpclient.HttpClient
import com.kdownloader.internal.storage.DefaultStorageResolver
import com.kdownloader.internal.storage.StorageResolver
import com.kdownloader.internal.stream.FileDownloadOutputStream
import com.kdownloader.utils.getRedirectedConnectionIfAny
import com.kdownloader.utils.isContentPath
import com.kdownloader.utils.withTemp
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

class DownloadTask(
    private val req: DownloadRequest,
    private val dbHelper: DbHelper,
    context: Context,
) {

    private val storage: StorageResolver = DefaultStorageResolver(context)
    private var responseCode = 0
    private var totalBytes: Long = 0
    private var inputStream: InputStream? = null
    private lateinit var outputStream: FileDownloadOutputStream

    private var tempPath: String = ""
    private lateinit var httpClient: HttpClient
    private var isResumeSupported = true

    private var lastSyncTime: Long = 0
    private var lastSyncBytes: Long = 0

    private var eTag: String = ""

    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, _ ->

            })

    companion object {
        private const val TIME_GAP_FOR_SYNC: Long = 2000
        private const val MIN_BYTES_FOR_SYNC: Long = 65536
        private const val BUFFER_SIZE = 1024 * 4
    }

    suspend inline fun run(
        crossinline onStart: () -> Unit = {},
        crossinline onProgress: (value: Int) -> Unit = { _ -> },
        crossinline onError: (error: String) -> Unit = { _ -> },
        crossinline onCompleted: () -> Unit = {},
        crossinline onPause: () -> Unit = {}
    ) = run(object : DownloadRequest.Listener {
        override fun onStart() = onStart()

        override fun onProgress(value: Int) = onProgress(value)

        override fun onError(error: String) = onError(error)

        override fun onCompleted() = onCompleted()

        override fun onPause() = onPause()
    })

    private suspend fun createAndInsertNewModel() {

        dbScope.launch {
            dbHelper.insert(
                DownloadModel(
                    id = req.downloadId,
                    url = req.url,
                    totalBytes = req.totalBytes,
                    eTag = eTag
                )
            )
        }
    }

    private suspend fun removeNoMoreNeededModelFromDatabase() {
        dbScope.launch {
            dbHelper.remove(req.downloadId)
        }
    }

    suspend fun run(listener: DownloadRequest.Listener) {

        withContext(Dispatchers.IO) {
            try {
                tempPath = req.filePath.withTemp
                val increment = req.enqueueAction == 1

                var model = getDownloadModelIfAlreadyPresentInDatabase()

                if (model == null && storage.fileExists(tempPath) && dbHelper is AppDbHelper) {
                    if (!increment) deleteTempFile()
                }

                if (model != null) {
                    if (storage.fileExists(tempPath)) {
                        req.totalBytes = (model.totalBytes)
                        req.downloadedBytes = (model.downloadedBytes)
                    } else {
                        removeNoMoreNeededModelFromDatabase()
                        req.downloadedBytes = 0
                        req.totalBytes = 0
                        model = null
                    }
                }

                // use the url to download the file with HTTP Client
                httpClient = DefaultHttpClient().clone()

                req.status = Status.RUNNING

                listener.onStart()

                httpClient.connect(req)

                eTag = httpClient.getResponseHeader(Constants.ETAG)

                if (checkIfFreshStartRequiredAndStart(model)) {
                    model = null
                }

                httpClient = getRedirectedConnectionIfAny(httpClient, req)
                responseCode = httpClient.getResponseCode()

                if (!isSuccessful()) {
                    listener.onError("Wrong link")
                }

                setResumeSupportedOrNot()

                totalBytes = req.totalBytes

                if (!isResumeSupported) {
                    deleteTempFile()
                    req.downloadedBytes = 0
                }

                if (totalBytes == 0L) {
                    totalBytes = httpClient.getContentLength()
                    req.totalBytes = (totalBytes)
                }

                if (isResumeSupported && model == null) {
                    createAndInsertNewModel()
                }

                inputStream = httpClient.getInputStream()
                if (inputStream == null) {
                    return@withContext
                }

                val buff = ByteArray(BUFFER_SIZE)

                tempPath = storage.createFile(tempPath, increment)
                outputStream = storage.getDownloadOutputStream(tempPath)

                if (req.status === Status.CANCELLED) {
                    deleteTempFile()
                    req.reset()
                    listener.onError("Cancelled")
                    return@withContext
                } else if (req.status === Status.PAUSED) {
                    sync(outputStream)
                    listener.onPause()
                    return@withContext
                }

                if (isResumeSupported && req.downloadedBytes != 0L) {
                    outputStream.seek(req.downloadedBytes)
                }

                do {
                    val byteCount = inputStream!!.read(buff, 0, BUFFER_SIZE)
                    if (byteCount == -1) {
                        break
                    }

                    if (req.status === Status.CANCELLED) {
                        deleteTempFile()
                        req.reset()
                        listener.onError("Cancelled")
                        return@withContext
                    } else if (req.status === Status.PAUSED) {
                        sync(outputStream)
                        listener.onPause()
                        return@withContext
                    }

                    if (!isActive || !req.job.isActive) {
                        deleteTempFile()
                        req.reset()
                        break
                    }
                    outputStream.write(buff, 0, byteCount)
                    req.downloadedBytes = req.downloadedBytes + byteCount
                    withContext(Dispatchers.IO) {
                        syncIfRequired(outputStream)
                    }

                    var progress = 0
                    if (totalBytes > 0) {
                        progress = ((req.downloadedBytes * 100) / totalBytes).toInt()
                    }
                    listener.onProgress(progress)
                } while (true)

                if (req.status === Status.CANCELLED) {
                    deleteTempFile()
                    req.reset()
                    listener.onError("Cancelled")
                    return@withContext
                } else if (req.status === Status.PAUSED) {
                    sync(outputStream)
                    listener.onPause()
                    return@withContext
                }

                if (!tempPath.isContentPath()) {
                    val path = storage.createFile(req.filePath, increment)
                    storage.renameFile(tempPath, path)
                    deleteTempFile()
                }
                // TODO: pass saved file path onCompleted
                listener.onCompleted()
                req.status = Status.COMPLETED
                return@withContext
            } catch (e: CancellationException) {
                deleteTempFile()
                req.reset()
                req.status = Status.FAILED
                listener.onError(e.toString())
                return@withContext
            } catch (e: Exception) {
                if (!isResumeSupported) {
                    deleteTempFile()
                    req.reset()
                }
                req.status = Status.FAILED
                listener.onError(e.toString())
                return@withContext
            } finally {
                closeAllSafely(outputStream)
            }
        }
    }

    private fun setResumeSupportedOrNot() {
        isResumeSupported = (responseCode == HttpURLConnection.HTTP_PARTIAL)
    }

    private fun deleteTempFile(): Boolean {
        return storage.deleteFile(tempPath)
    }

    @Throws(IOException::class, IllegalAccessException::class)
    private suspend fun checkIfFreshStartRequiredAndStart(model: DownloadModel?): Boolean {
        if (responseCode == Constants.HTTP_RANGE_NOT_SATISFIABLE || isETagChanged(model)) {
            if (model != null) {
                removeNoMoreNeededModelFromDatabase()
            }
            deleteTempFile()
            req.downloadedBytes = 0
            req.totalBytes = 0
            httpClient = DefaultHttpClient().clone()
            httpClient.connect(req)
            httpClient = getRedirectedConnectionIfAny(httpClient, req)
            responseCode = httpClient.getResponseCode()
            return true
        }
        return false
    }

    private fun isETagChanged(model: DownloadModel?): Boolean {
        return (!(eTag.isEmpty() || model == null || model.eTag.isEmpty())
                && model.eTag != eTag)
    }

    private suspend fun getDownloadModelIfAlreadyPresentInDatabase(): DownloadModel? {
        return withContext(Dispatchers.IO) {
            dbHelper.find(req.downloadId)
        }
    }

    private suspend fun closeAllSafely(outputStream: FileDownloadOutputStream) {

        try {
            httpClient.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            inputStream!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            sync(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {

            try {
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncIfRequired(outputStream: FileDownloadOutputStream) {
        val currentBytes: Long = req.downloadedBytes
        val currentTime = System.currentTimeMillis()
        val bytesDelta: Long = currentBytes - lastSyncBytes
        val timeDelta: Long = currentTime - lastSyncTime
        if (bytesDelta > MIN_BYTES_FOR_SYNC && timeDelta > TIME_GAP_FOR_SYNC) {
            sync(outputStream)
            lastSyncBytes = currentBytes
            lastSyncTime = currentTime
        }
    }

    private suspend fun sync(outputStream: FileDownloadOutputStream) {
        var success: Boolean
        try {
            outputStream.flushAndSync()
            success = true
        } catch (e: IOException) {
            success = false
            e.printStackTrace()
        }
        if (success && isResumeSupported) {
            dbHelper
                .updateProgress(
                    req.downloadId,
                    req.downloadedBytes,
                    System.currentTimeMillis()
                )
        }
    }

    private fun isSuccessful(): Boolean {
        return (responseCode >= HttpURLConnection.HTTP_OK
                && responseCode < HttpURLConnection.HTTP_MULT_CHOICE)
    }
}