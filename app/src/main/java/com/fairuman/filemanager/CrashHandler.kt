package com.fairuman.filemanager

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动闪退兜底：把未捕获异常的堆栈写到 crash.log，方便用户发回来定位。
 * 两个位置都写：应用私有目录（无需权限）和外置存储的应用专属目录
 * （用户用文件管理器就能看到，不用 root）。
 */
object CrashHandler {
    const val FILE_NAME = "crash.log"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { writeLog(appCtx, e) } catch (_: Exception) { }
            prev?.uncaughtException(t, e)
        }
    }

    fun writeLog(context: Context, e: Throwable): File? {
        val text = buildString {
            // 追加写：v1.2 用覆盖写，原始异常会被后来的 SuperNotCalledException 盖掉。
            append("\n----- crash -----\n")
            // 版本号从 PackageManager 读，别写死（以前写死 1.2.0，崩溃日志
            // 里一直是错的版本，定位时误导人）。
            val ver = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            append("Fairumanējā $ver crash\n")
            append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
            append("device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
            append(" Android ").append(android.os.Build.VERSION.RELEASE)
            append(" SDK ").append(android.os.Build.VERSION.SDK_INT).append('\n')
            append(e.toString()).append('\n')
            var c: Throwable? = e.cause
            while (c != null) {
                append("Caused by: ").append(c.toString()).append('\n')
                c = c.cause
            }
            append("stack:\n")
            for (el in e.stackTrace) append("  at ").append(el.toString()).append('\n')
        }
        // v1.5: 同时打到 logcat（分块，单条超 4000 字符会被截断），
        // 这样以后直接看 logcat 就能拿到原始异常，不用再去抠 crash.log。
        try {
            var start = 0
            while (start < text.length) {
                val end = minOf(start + 3500, text.length)
                Log.e("Fairumanaja", text.substring(start, end))
                start = end
            }
        } catch (_: Exception) { }
        var out: File? = null
        try {
            val f = File(context.filesDir, FILE_NAME)
            f.appendText(text)
            out = f
        } catch (_: Exception) { }
        try {
            val ext = context.getExternalFilesDir(null)
            if (ext != null) {
                val f = File(ext, FILE_NAME)
                f.appendText(text)
                out = f
            }
        } catch (_: Exception) { }
        return out
    }

    fun logPathText(context: Context): String {
        val ext = try { context.getExternalFilesDir(null)?.absolutePath } catch (_: Exception) { null }
        return "应用内：/data/data/com.fairuman.filemanager/files/crash.log\n" +
            (ext ?: "/sdcard/Android/data/com.fairuman.filemanager/files") + "/crash.log"
    }
}
