package com.kdownloader.internal.storage


import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.kdownloader.internal.stream.FileDownloadOutputStream
import com.kdownloader.internal.stream.FileDownloadRandomAccessFile
import com.kdownloader.utils.isUriPath
import java.io.*

//region Messages
const val FNC = "FNC"
const val FILE_NOT_FOUND = "file_not_found"
const val FILE_ALLOCATION_ERROR = "file_allocation_error"
//endregion

//region OutputStream
fun getDownloadOutputStream(
    filePath: String, contentResolver: ContentResolver
): FileDownloadOutputStream {
    return FileDownloadRandomAccessFile.create(File(filePath))
}
//endregion

//region Delete
fun deleteFileAtPath(filePath: String): Boolean {
    return if (filePath.isUriPath()) {
        val uri = Uri.parse(filePath)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val file = File((uri.path ?: filePath))
                if (file.canWrite() && file.exists()) deleteFile(file) else false
            }
            ContentResolver.SCHEME_CONTENT -> {
                /*if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }*/
                true
            }
            else -> false
        }
    } else {
        deleteFile(File(filePath))
    }
}

fun deleteFile(file: File): Boolean {
    return if (file.exists() && file.canWrite()) file.delete() else false
}
//endregion

//region Rename
fun renameFile(oldFile: String, newFile: String, context: Context): Boolean {
    return if (oldFile.isUriPath()) {
        val uri = Uri.parse(oldFile)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val file = File(uri.path ?: oldFile)
                if (file.canWrite() && file.exists()) renameFile(file, File(newFile)) else {
                    val contentValue = ContentValues()
                    contentValue.put("uri", newFile)
                    context.contentResolver.update(uri, contentValue, null, null) > 0
                }
            }
            ContentResolver.SCHEME_CONTENT -> {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.renameDocument(context.contentResolver, uri, newFile) != null
                } else {
                    val contentValue = ContentValues()
                    contentValue.put("uri", newFile)
                    context.contentResolver.update(uri, contentValue, null, null) > 0
                }
            }
            else -> false
        }
    } else {
        renameFile(File(oldFile), File(newFile))
    }
}

fun renameFile(oldFile: File, newFile: File): Boolean {
    return oldFile.renameTo(newFile)
}
//endregion

//region Create
fun createFileAtPath(filePath: String, increment: Boolean, context: Context): String {
    return if (filePath.isUriPath()) {
        val uri = Uri.parse(filePath)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                createLocalFile(uri.path ?: filePath, increment)
            }
            ContentResolver.SCHEME_CONTENT -> {
                val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                if (pfd == null) {
                    throw IOException(FNC)
                } else {
                    pfd.close()
                    filePath
                }
            }
            else -> throw IOException(FNC)
        }
    } else {
        createLocalFile(filePath, increment)
    }
}

fun createLocalFile(filePath: String, increment: Boolean): String {
    return if (!increment) {
        createFile(File(filePath))
        filePath
    } else {
        getIncrementedFileIfOriginalExists(filePath).absolutePath
    }
}

fun getIncrementedFileIfOriginalExists(originalPath: String): File {
    var file = File(originalPath)
    var counter = 0
    if (file.exists()) {
        val parentPath = "${file.parent}/"
        val extension = file.extension
        val fileName: String = file.nameWithoutExtension
        while (file.exists()) {
            ++counter
            val newFileName = "$fileName ($counter)"
            file = File("$parentPath$newFileName.$extension")
        }
    }
    createFile(file)
    return file
}

fun createFile(file: File) {
    if (!file.exists()) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            if (parent.mkdirs()) {
                if (!file.createNewFile()) throw FileNotFoundException("$file $FILE_NOT_FOUND")
            } else {
                throw FileNotFoundException("$file $FILE_NOT_FOUND")
            }
        } else {
            if (!file.createNewFile()) throw FileNotFoundException("$file $FILE_NOT_FOUND")
        }
    }
}
//endregion

//region allocate

fun fileExist(filePath: String, context: Context): Boolean {
    return if (filePath.isUriPath()) {
        val uri = Uri.parse(filePath)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val file = File(uri.path ?: filePath)
                file.canWrite() && file.exists()
            }
            ContentResolver.SCHEME_CONTENT -> {
                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "w")
                val exists = parcelFileDescriptor != null
                parcelFileDescriptor?.close()
                exists
            }
            else -> false
        }
    } else {
        val file = File(filePath)
        file.canWrite() && file.exists()
    }
}

fun allocateFile(filePath: String, contentLength: Long, context: Context) {
    return if (filePath.isUriPath()) {
        val uri = Uri.parse(filePath)
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                allocateFile(File(uri.path ?: filePath), contentLength)
            }
            ContentResolver.SCHEME_CONTENT -> {
                val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "w")
                if (parcelFileDescriptor == null) {
                    throw IOException(FILE_ALLOCATION_ERROR)
                } else {
                    allocateParcelFileDescriptor(parcelFileDescriptor, contentLength)
                }
            }
            else -> throw IOException(FILE_ALLOCATION_ERROR)
        }
    } else {
        allocateFile(File(filePath), contentLength)
    }
}

fun allocateParcelFileDescriptor(parcelFileDescriptor: ParcelFileDescriptor, contentLength: Long) {
    if (contentLength > 0) {
        try {
            val fileOutputStream = FileOutputStream(parcelFileDescriptor.fileDescriptor)
            if (fileOutputStream.channel.size() == contentLength) {
                return
            }
            fileOutputStream.channel.position(contentLength - 1.toLong())
            fileOutputStream.write(1)
        } catch (e: Exception) {
            throw IOException(FILE_ALLOCATION_ERROR)
        }
    }
}

fun allocateFile(file: File, contentLength: Long) {
    if (!file.exists()) {
        createFile(file)
    }
    if (file.length() == contentLength) {
        return
    }
    if (contentLength > 0) {
        try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.setLength(contentLength)
            randomAccessFile.close()
        } catch (e: Exception) {
            throw IOException(FILE_ALLOCATION_ERROR)
        }
    }
}
//endregion