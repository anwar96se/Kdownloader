package com.kdownloader.internal.storage

import com.kdownloader.internal.stream.FileDownloadOutputStream
import java.io.IOException

interface StorageResolver {

    /** This method is called by Fetch to create the File object or create an entry in the storage database
     * whatever that is. This method can throw IOExceptions if the file cannot be created.
     * This method is called on a background thread.
     * @param file the file path. Can be a uri or any path that makes sense to your application.
     * @param increment specify if the file already exist to create a new incremented file.
     * Example: text.txt, text(1).txt, text(2).txt.
     * @return returns the file path if the file was created successfully.
     * */
    fun createFile(file: String, increment: Boolean): String

    /**
     * This method asks the file system to preallocate the file if needed.
     * @param file the file to pre allocate.
     * @param contentLength the file content length
     * @throws IOException if the file cannot be pre-allocated.
     * @return true if the file was allocated. Otherwise false
     * */
    fun preAllocateFile(file: String, contentLength: Long): Boolean

    /** This method is called by Fetch to delete the File object or remove an entry in the storage database
     * whatever that is. This method can throw IOExceptions if the file cannot be deleted.
     * This method is called on a background thread.
     * @param file the file path. Can be custom to your app.
     * @return returns true if the file was delete. Otherwise false.
     * */
    fun deleteFile(file: String): Boolean

    /**
     * This method is used to rename an existing file.
     * @param oldFile the old file
     * @param newFile the new file
     * */
    fun renameFile(oldFile: String, newFile: String): Boolean

    /** This method is used to check if the file exists at the specified location on disk.
     * @param file the file path. Can be a uri or any path that makes sense to your application.
     * @return returns true if the file exists otherwise false
     * */
    fun fileExists(file: String): Boolean

    /**
     * This method is called by Downloader to request the FileDownloadOutputStream output stream that will be used to save
     * the downloaded information/bytes.
     * This method is called on a background thread.
     * Note: If your file is a uri that points to a content provider you must override this method
     * and provide the proper FileDownloadOutputStream object.
     * @param file the file path. Can be a uri or any path that makes sense to your application.
     * @return FileDownloadOutputStream object. Downloader will call the close method automatically
     *         after the Downloader finished. Downloader will provide a default
     *         FileDownloadOutputStream. Override this method to provide your own FileDownloadOutputStream.
     * */
    fun getDownloadOutputStream(file: String): FileDownloadOutputStream

}