package com.kdownloader.internal.storage

import android.content.Context
import com.kdownloader.internal.stream.FileDownloadOutputStream
import java.io.FileNotFoundException

class DefaultStorageResolver(private val context: Context) : StorageResolver {

    override fun createFile(file: String, increment: Boolean): String {
        return createFileAtPath(file, increment, context)
    }

    override fun preAllocateFile(file: String, contentLength: Long): Boolean {
        if (file.isEmpty()) {
            throw FileNotFoundException("$file $FILE_NOT_FOUND")
        }
        if (contentLength < 1) {
            return true
        }
        allocateFile(file, contentLength, context)
        return true
    }

    override fun deleteFile(file: String): Boolean {
        return deleteFileAtPath(file)
    }

    override fun renameFile(oldFile: String, newFile: String): Boolean {
        if (oldFile.isEmpty() || newFile.isEmpty()) {
            return false
        }
        return renameFile(oldFile, newFile, context)
    }

    override fun fileExists(file: String): Boolean {
        if (file.isEmpty()) {
            return false
        }
        return fileExist(file, context)
    }

    override fun getDownloadOutputStream(file: String): FileDownloadOutputStream {
        return getDownloadOutputStream(file, context.contentResolver)
    }

}