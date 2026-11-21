package com.fairuman.filemanager

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 通过 Shizuku（shell 权限）读取根目录，并做文件操作（复制 / 剪切 /
 * 粘贴 / 删除 / 重命名 / 新建文件夹 / 属性），全部走 shell 命令。
 * 注意：Shizuku 的 API 分在 api/aidl/shared/provider 四个 AAR 里，
 * 打包时只取了它们的 classes.jar（见 build-direct/aars/manifest.json）。
 *
 * v1.8.0：根目录不止浏览了——删除 rm -rf、重命名 mv、复制 cp -r、
 * 新建 mkdir、属性 du -sb 都走 Shizuku shell；读目录失败时不静默。
 * v1.5.0 修过的头号 bug：旧解析器只认 GNU 版 `ls -la`（英文月份，
 * 一行至少 9 列），但 Android 的 toybox `ls -la` 用 ISO 日期
 * （`2021-08-21 06:01`，一行只有 8 列），结果每一行都被丢掉，
 * 根目录在手机上显示“这里是空的”。现在两种格式都认，坏行只跳过
 * 该行；读到了条目却一行都没解析出来时返回错误，绝不静默显示空。
 */
object ShizukuRoot {

    private const val TAG = "Fairumanaja"

    data class Entry(
        val name: String,
        val path: String,
        var isDir: Boolean,
        val size: Long,
        val modified: Long,
        val linkTarget: String? = null
    )

    /** 一条 shell 命令的执行结果（同步调用，调用方自己放后台线程）。 */
    data class ExecResult(val exit: Int, val out: String, val err: String, val timedOut: Boolean = false)

    /**
     * v1.8.0：用 Shizuku 跑一条命令（argv 形式，不走 shell，所以路径里有空格、
     * 引号也不用转义；需要重定向/管道时调用方自己包一层 `sh -c`）。
     * 同步等结果，带超时（默认 30 秒，超时就杀掉进程返回 timedOut=true）。
     * minSdk 是 24，Process.waitFor(timeout, unit) 要 API 26 才能用，
     * 这里用 exitValue 轮询，哪里都能跑。
     */
    fun exec(vararg cmd: String, timeoutMs: Long = 30000L): ExecResult {
        val proc = try {
            Shizuku.newProcess(cmd, null, null)
        } catch (e: Throwable) {
            return ExecResult(-1, "", e.message ?: e.toString())
        } ?: return ExecResult(-1, "", "Shizuku")
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val tOut = Thread { try { proc.inputStream.copyTo(outBytes) } catch (_: Throwable) { } }
        val tErr = Thread { try { proc.errorStream.copyTo(errBytes) } catch (_: Throwable) { } }
        tOut.start()
        tErr.start()
        val deadline = System.currentTimeMillis() + timeoutMs
        var exit = -1
        var timedOut = false
        while (true) {
            try {
                exit = proc.exitValue()
                break
            } catch (_: IllegalThreadStateException) {
                // 还没跑完（标准 Process 的行为）。
            } catch (_: IllegalArgumentException) {
                // Shizuku 的远程 exitValue 在进程没退时抛的是
                // IllegalArgumentException("process hasn't exited")，
                // 不是 IllegalThreadStateException——不接住它，
                // 每一次根目录操作都会直接闪退（v1.8.0 实测抓到的：
                // 重命名 mv 其实已经成功了，App 却被这个异常杀掉）。
            } catch (e: Throwable) {
                // Binder 死了之类：不再轮询，直接按失败返回（带上原因），
                // 绝不让异常逃到调用方去闪退。
                errBytes.write(("exec failed: ${e.message ?: e.toString()}").toByteArray(Charsets.UTF_8))
                break
            }
            if (System.currentTimeMillis() > deadline) {
                timedOut = true
                try { proc.destroy() } catch (_: Throwable) { }
                break
            }
            try { Thread.sleep(50) } catch (_: InterruptedException) { break }
        }
        try { tOut.join(1000) } catch (_: Throwable) { }
        try { tErr.join(1000) } catch (_: Throwable) { }
        return ExecResult(exit, outBytes.toString(Charsets.UTF_8.name()), errBytes.toString(Charsets.UTF_8.name()), timedOut)
    }

    /** 包一层 `sh -c` 跑脚本（需要重定向/管道/通配符时用）。 */
    fun execScript(script: String, timeoutMs: Long = 30000L): ExecResult =
        exec("sh", "-c", script, timeoutMs = timeoutMs)

    /** 单引号转义：拼 `sh -c` 脚本里的路径时用（路径里有 ' 就拆成 '\''）。 */
    fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    const val REQ_SHIZUKU = 2001

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun isAuthorized(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    /** 请求授权；结果通过 onResult 回调（true=已授权）。 */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQ_SHIZUKU) return
                try { Shizuku.removeRequestPermissionResultListener(this) } catch (_: Throwable) { }
                main.post { onResult(grantResult == PackageManager.PERMISSION_GRANTED) }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(REQ_SHIZUKU)
        } catch (_: Throwable) {
            try { Shizuku.removeRequestPermissionResultListener(listener) } catch (_: Throwable) { }
            main.post { onResult(false) }
        }
    }

    /**
     * 在后台线程跑 `ls -la <dir>`，解析后在主线程回调。
     * 成功：onDone(entries, null)；失败：onDone(null, errorDetail)，
     * errorDetail 是给用户看的真实原因（启动失败 / 退出码 / 解析失败）。
     */
    fun listDir(dir: String, onDone: (List<Entry>?, String?) -> Unit) {
        Thread {
            val result = try { runLs(dir) } catch (e: Throwable) { null to (e.message ?: e.toString()) }
            Handler(Looper.getMainLooper()).post { onDone(result.first, result.second) }
        }.start()
    }

    private fun runLs(dir: String): Pair<List<Entry>?, String?> {
        // toybox ls 对“软链接到目录”的参数（比如 /sdcard）只印链接本身
        // 那一行，不列目标里的内容；路径后加一个斜杠才列内容（`ls -la
        // /sdcard/` 就正常）。只改传给 ls 的参数，显示路径和条目路径
        // 仍用原来的 dir，不动。dir 是 "/" 时 lsArg 是 "//"，ls 照样认。
        val lsArg = if (dir.endsWith("/")) dir else "$dir/"
        val proc = try { Shizuku.newProcess(arrayOf("ls", "-la", lsArg), null, null) } catch (e: Throwable) {
            return null to "Shizuku: ${e.message ?: e.toString()}"
        } ?: return null to "Shizuku"
        // 把 stderr 也排空，否则子进程可能因管道满而卡住；留一份给报错用。
        val errBytes = ByteArrayOutputStream()
        val drain = Thread { try { proc.errorStream.copyTo(errBytes) } catch (_: Throwable) { } }
        drain.start()
        val lines = try {
            BufferedReader(InputStreamReader(proc.inputStream)).readLines()
        } catch (_: Throwable) { return null to "ls" }
        try { proc.waitFor() } catch (_: Throwable) { }
        try { drain.join(1000) } catch (_: Throwable) { }
        val exit = try { proc.exitValue() } catch (_: Throwable) { return null to "ls" }
        val (entries, candidates) = parseLsOutput(lines, dir)
        // v1.8.0：toybox 对 stat 不到的条目只往 stderr 写
        // "ls: <路径>: Permission denied"，stdout 里根本不出现这一行——
        // 光靠解析 stdout 永远找不回这些名字（这台模拟器上 / 根目录
        // 就有 9 个这样的：init.rc、init、metadata、adb_keys……）。
        // 从 stderr 里把名字捞回来，按未知类型/大小列出来（名字对就行）。
        recoverDeniedNames(errBytes.toString(Charsets.UTF_8.name()), dir, entries)
        if (entries.isNotEmpty()) {
            // toybox ls 碰到个别没权限的条目就会退 1，但 stdout 里的列表是
            // 完整的：只要解析出了条目就照单全收，不能因退出码丢掉整表。
            resolveSymlinks(dir, entries)
            entries.sortWith(compareBy({ !it.isDir }, { it.name.lowercase(Locale.US) }))
            return entries to null
        }
        if (candidates > 0) {
            // 明明读到了条目却一行都没解析出来：格式变了，绝不能静默显示空。
            return null to "parse"
        }
        if (exit != 0) {
            val err = errBytes.toString(Charsets.UTF_8.name()).trim().lineSequence().firstOrNull()?.take(120)
            return null to "ls exit $exit${if (!err.isNullOrEmpty()) ": $err" else ""}"
        }
        return entries to null
    }

    /**
     * 解析 `ls -la` 的全部输出行。返回（解析出的条目，疑似条目行的数量）。
     * 两种日期格式都认：
     * - toybox（Android 自带）：`drwxr-xr-x 21 root root 4096 2021-08-21 06:01 名字`
     * - GNU：`drwxr-xr-x 41 root root 4096 Nov 17 07:18 名字`
     * v1.8.0：解析不了的行也不再静默丢掉——先尽力按 toybox 布局取名字
     * 列出来（类型/大小/日期未知，副标题显示“未知”），实在取不出名字
     * 的才跳过该行，但每行都打 Log（tag Fairumanaja），手机上丢了
     * 哪些条目看 logcat 就知道。
     * 还有一种情况：Android 16 上 SELinux 更严，shell 权限 stat 不到的
     * 条目，toybox 会把权限位印成 `??????????`、大小/日期印成 `?`，
     * 以前解析器认不出这种行，整行被丢掉——这就是 Max 说的“部分读取
     * 根目录失败”最可能的根因。现在 `?` 开头的行也收。
     * 纯函数，供单测直接调用（见 tools/LsParseTest.kt）。
     */
    fun parseLsOutput(lines: List<String>, dir: String): Pair<MutableList<Entry>, Int> {
        val out = mutableListOf<Entry>()
        var candidates = 0
        for (line in lines) {
            val t = line.trim().split(Regex("\\s+"))
            val looksLikeEntry = t.isNotEmpty() && t[0].length == 10 && (t[0][0] in "dl-bcps-" || t[0][0] == '?')
            // "." 和 ".." 不是条目：`ls -la` 每级目录都有这两行，空目录
            // 就只有这两行——把它们算成候选，空目录会误报“解析失败”
            // （v1.8.0 实测抓到的：多选删空 fairu180 后，空列表显示
            // “读取根目录失败”，其实读成功了，只是目录空了）。
            // toybox 的名字在第 8 列，GNU 的在第 9 列，两处都认。
            val isDot = t.getOrNull(7) == "." || t.getOrNull(7) == ".." ||
                t.getOrNull(8) == "." || t.getOrNull(8) == ".."
            if (looksLikeEntry && !isDot) candidates++
            val parsed = parseLsLine(line, dir)
            if (parsed != null) out.add(parsed)
            else if (looksLikeEntry && !isDot) {
                // 看着像条目却一行都没解析出来：打日志，别静默丢掉。
                Log.w(TAG, "ls parse dropped line: $line")
            }
        }
        return out to candidates
    }

    private val MONTHS = mapOf(
        "Jan" to 0, "Feb" to 1, "Mar" to 2, "Apr" to 3, "May" to 4, "Jun" to 5,
        "Jul" to 6, "Aug" to 7, "Sep" to 8, "Oct" to 9, "Nov" to 10, "Dec" to 11
    )
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val ISO_TIME = Regex("^\\d{2}:\\d{2}(:\\d{2})?$")

    /** 解析一行 `ls -la` 输出；解析不了返回 null。
     *  v1.8.0：权限位是 `?`（stat 不到的条目）也收；日期格式不认识时
     *  兜底按 toybox 布局（名字从第 8 列起）取名字列出来，大小/日期
     *  记 0（界面显示“未知”），绝不因为一行格式怪就丢掉整个条目。 */
    private fun parseLsLine(line: String, dir: String): Entry? {
        val t = line.trim().split(Regex("\\s+"))
        if (t.size < 8) return null
        val perms = t[0]
        // '?' 开头是 stat 不到的条目（Android 16 上常见），照样收，类型未知。
        if (perms.length != 10 || (perms[0] !in "dl-bcps-" && perms[0] != '?')) return null
        val isDir = perms[0] == 'd'
        val size = t[4].toLongOrNull() ?: 0L
        // 两种日期格式：toybox 是 ISO 日期 + 时间（名字从第 8 列起），
        // GNU 是英文月份 + 日 + 时间/年（名字从第 9 列起）。
        val nameIndex: Int
        val modified: Long
        if (ISO_DATE.matches(t[5]) && t.size >= 8 && ISO_TIME.matches(t[6])) {
            nameIndex = 7
            modified = parseIsoDateTime(t[5], t[6])
        } else if (MONTHS.containsKey(t[5]) && t.size >= 9) {
            val month = MONTHS.getValue(t[5])
            val day = t[6].toIntOrNull() ?: return null
            nameIndex = 8
            modified = parseLsDate(month, day, t[7])
        } else {
            // 兜底：日期格式不认识（比如全是 `?`），但行首确实是 ls 条目——
            // 名字按 toybox 布局从第 8 列起取，大小/日期记未知（0），列出来再说。
            if (t.size <= 7) return null
            val name = bestEffortName(t, 7, perms[0] == 'l')
            if (name == null || name == "." || name == ".." || name.isEmpty()) return null
            Log.w(TAG, "ls parse best-effort (unknown date/size): $line")
            val path = if (dir == "/") "/$name" else "$dir/$name"
            return Entry(name, path, isDir, 0L, 0L, null)
        }
        if (t.size <= nameIndex) return null
        var name = t.subList(nameIndex, t.size).joinToString(" ")
        // 软链接：名字是 "xxx -> 目标"，只留名字部分，目标记下来后面判目录。
        var linkTarget: String? = null
        if (perms[0] == 'l') {
            val arrow = name.indexOf(" -> ")
            if (arrow >= 0) {
                linkTarget = name.substring(arrow + 4).trim()
                name = name.substring(0, arrow)
            }
        }
        if (name == "." || name == ".." || name.isEmpty()) return null
        val path = if (dir == "/") "/$name" else "$dir/$name"
        return Entry(name, path, isDir, size, modified, linkTarget)
    }

    /** 兜底取名字：第 nameIndex 列起拼起来；软链接去掉 " -> 目标" 部分。 */
    private fun bestEffortName(t: List<String>, nameIndex: Int, isLink: Boolean): String? {
        var name = t.subList(nameIndex, t.size).joinToString(" ")
        if (isLink) {
            val arrow = name.indexOf(" -> ")
            if (arrow >= 0) name = name.substring(0, arrow)
        }
        return name.trim().ifEmpty { null }
    }

    /** 从 ls 的 stderr 里捞回 stat 失败的条目名（见 runLs 的注释）：
     *  只认 "ls: <路径>: Permission denied" 这种行，路径的父目录必须
     *  就是当前目录，名字去重；捞回来的条目类型/大小/日期未知（0），
     *  界面显示“未知”，点它进不去就弹明确提示。 */
    private fun recoverDeniedNames(errText: String, dir: String, entries: MutableList<Entry>) {
        val known = entries.map { it.name }.toSet()
        val expectParent = if (dir == "/") "" else dir
        errText.lineSequence().forEach { raw ->
            val line = raw.trim()
            val m = Regex("^ls: (.+): Permission denied$").matchEntire(line) ?: return@forEach
            val fullPath = m.groupValues[1].replace(Regex("/+"), "/")
            val parent = fullPath.substringBeforeLast('/', "")
            if (parent != expectParent) return@forEach
            val name = fullPath.substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == ".." || name in known) return@forEach
            Log.w(TAG, "ls denied (stat failed), listing as unknown: $fullPath")
            entries.add(Entry(name, fullPath, false, 0L, 0L, null))
        }
    }

    /**
     * 软链接的目标是目录还是文件，`ls -la` 本身看不出来（都显示 l），
     * 用一个 `test -d` 批量判一次；判不出来就按文件算（点它只提示，不闪退）。
     */
    private fun resolveSymlinks(dir: String, entries: List<Entry>) {
        val links = entries.filter { it.linkTarget != null }
        if (links.isEmpty()) return
        val targets = links.map { link ->
            val t = link.linkTarget!!
            if (t.startsWith("/")) t else if (dir == "/") "/$t" else "$dir/$t"
        }
        // 每条链接测两次：目标路径，和链接本身（跟随链接）。
        // v1.8.1：一次 `test -d` 的结果可能不全（模拟器上 /mnt/sdcard ->
        // /storage/self/primary 在 00:15/00:20 被判成文件、00:22/00:41 又是
        // 文件夹），结果不够数就重试一次；软链误判成文件会走到 stageRootFile 报错。
        val paths = ArrayList<String>(links.size * 2)
        links.forEachIndexed { i, link ->
            paths.add(targets[i])
            paths.add(if (dir == "/") "/${link.name}" else "$dir/${link.name}")
        }
        var results: List<String> = emptyList()
        for (attempt in 0 until 2) {
            results = runDirCheck(paths)
            if (results.size >= paths.size) break
            Log.w(TAG, "resolveSymlinks attempt $attempt short results ${results.size}/${paths.size}")
        }
        for (i in links.indices) {
            if (results.getOrNull(i * 2) == "D" || results.getOrNull(i * 2 + 1) == "D") links[i].isDir = true
        }
    }

    /** 对一批路径各跑一次 `test -d`，每条路径输出一行 D/F。 */
    private fun runDirCheck(paths: List<String>): List<String> {
        return try {
            val script = "for t in \"\$@\"; do if [ -d \"\$t\" ]; then echo D; else echo F; fi; done"
            val proc = Shizuku.newProcess(
                arrayOf("sh", "-c", script, "sh", *paths.toTypedArray()), null, null
            ) ?: return emptyList()
            Thread { try { proc.errorStream.readBytes() } catch (_: Throwable) { } }.start()
            val results = try {
                BufferedReader(InputStreamReader(proc.inputStream)).readLines()
            } catch (_: Throwable) { return emptyList() }
            try { proc.waitFor() } catch (_: Throwable) { }
            results
        } catch (_: Throwable) { emptyList() }
    }

    private fun parseIsoDateTime(date: String, time: String): Long {
        return try {
            val dp = date.split("-")
            val tp = time.split(":")
            val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.US)
            cal.set(
                dp[0].toInt(), dp[1].toInt() - 1, dp[2].toInt(),
                tp[0].toInt(), tp[1].toInt(), tp.getOrNull(2)?.toIntOrNull() ?: 0
            )
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Throwable) { System.currentTimeMillis() }
    }

    private fun parseLsDate(month: Int, day: Int, timeOrYear: String): Long {
        val now = Calendar.getInstance()
        return try {
            val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.US)
            if (timeOrYear.contains(":")) {
                val parts = timeOrYear.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                cal.set(now.get(Calendar.YEAR), month, day, h, m, 0)
                cal.set(Calendar.MILLISECOND, 0)
                // 今年的这个日期还没到就是去年的（ls 对半年内的文件只显示时间不显示年）。
                if (cal.timeInMillis > now.timeInMillis + 24L * 3600 * 1000) {
                    cal.add(Calendar.YEAR, -1)
                }
            } else {
                val year = timeOrYear.toIntOrNull() ?: now.get(Calendar.YEAR)
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Throwable) { now.timeInMillis }
    }
}
