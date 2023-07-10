package com.kdownloader.internal.stream

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.*
import java.nio.channels.FileChannel

class FileDownloadUri private constructor(cr: ContentResolver, uri: Uri) :
    FileDownloadOutputStream {

    private var channel: FileChannel
    private val pdf: ParcelFileDescriptor
    private val out: BufferedOutputStream
    private val fos: FileOutputStream

    init {
        pdf = cr.openFileDescriptor(uri, "rw")
            ?: throw FileNotFoundException("result of $uri is null!")
        fos = FileOutputStream(pdf.fileDescriptor)
        channel = fos.channel
        out = BufferedOutputStream(fos)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray?, off: Int, len: Int) {
        out.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flushAndSync() {
        out.flush()
        pdf.fileDescriptor.sync()
    }

    @Throws(IOException::class)
    override fun close() {
        out.close()
        fos.close()
        pdf.close()
    }

    @Throws(IOException::class)
    override fun seek(offset: Long) {
        channel.position(offset)
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        try {
            pdf.apply {
                Os.posix_fallocate(fileDescriptor, 0, newLength)
            }
        } catch (e: Throwable) {
            throw IOException(e.message)
        }
    }

    companion object {
        @Throws(IOException::class)
        fun create(cr: ContentResolver, fileUri: Uri): FileDownloadUri {
            return FileDownloadUri(cr, fileUri)
        }
    }
}