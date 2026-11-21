package com.fairuman.filemanager

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class FairuFileProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.fairuman.filemanager.fileprovider"
        fun uriFor(file: File): Uri = Uri.parse("content://$AUTHORITY${file.absolutePath}")
    }

    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = uri.path?.let { FileUtils.mimeFor(File(it)) }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val path = uri.path ?: throw FileNotFoundException(uri.toString())
        val file = File(path)
        if (!file.exists()) throw FileNotFoundException(path)
        val m = if (mode.contains("w")) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(file, m)
    }
}
