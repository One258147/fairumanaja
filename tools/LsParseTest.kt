import com.fairuman.filemanager.ShizukuRoot

/**
 * ShizukuRoot.parseLsOutput 的单测（不打包进 APK）。
 * 跑法（项目根目录）：
 *   bash tools/run-ls-parse-test.sh
 * v1.5.0 回归用例：旧解析器只认 GNU 格式（英文月份、9 列），Android 自带的
 * toybox `ls -la` 是 ISO 日期（`2021-08-21 06:01`，8 列），旧解析器会把
 * 每一行都丢掉，根目录显示空。下面 toybox 样本在旧解析器下是 0 条。
 */
private var failures = 0

private fun check(name: String, cond: Boolean) {
    if (cond) println("PASS: $name") else { println("FAIL: $name"); failures++ }
}

fun main() {
    // ---- 1. toybox 格式（Android 真机实测样式：模拟器 Android 10 + Max 手机 Android 16）----
    val toybox = """
        total 64
        drwxr-xr-x  21 root   root    4096 2021-08-21 06:01 .
        drwxr-xr-x  21 root   root    4096 2021-08-21 06:01 ..
        dr-xr-xr-x  26 root   root       0 2026-11-17 23:27 acct
        drwxr-xr-x  14 root   root     280 2026-11-17 23:27 apex
        lrw-r--r--   1 root   root      11 2021-08-21 06:01 bin -> /system/bin
        drwxrwx---   2 system cache   4096 2021-08-21 05:18 cache
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 3rdmodem
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 3rdmodemnvm
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 3rdmodemmvbkp
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 ai_model
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 ai_model_vendor
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 bootstrap-apex
        -rw-r--r--   1 root   root    1234 2026-11-18 07:18 build.prop
        drwxr-xr-x   2 root   root    4096 2026-11-18 07:18 My Folder
        -rw-r--r--   1 root   root     567 2026-11-18 07:18 file with spaces.txt
        lrw-r--r--   1 root   root      15 2021-08-21 06:01 sdcard -> /storage/self/primary
        this is not an ls line at all
        drwxr-xr-x   2 root   root
    """.trimIndent().lines()
    val (entries, candidates) = ShizukuRoot.parseLsOutput(toybox, "/")
    val byName = entries.associateBy { it.name }
    check("toybox: 解析出 14 条", entries.size == 14)
    check("toybox: 有疑似条目行 17 行（14 解析成功 + . .. 和 1 行截断的坏行）", candidates == 17)
    check("toybox: 目录 10 个", entries.count { it.isDir } == 10)
    check("toybox: 3rdmodem 在", byName.containsKey("3rdmodem"))
    check("toybox: ai_model_vendor 在", byName.containsKey("ai_model_vendor"))
    check("toybox: 带空格的文件夹名完整", byName.containsKey("My Folder"))
    check("toybox: 带空格的文件名完整", byName.containsKey("file with spaces.txt"))
    check("toybox: build.prop 是文件、大小 1234", byName["build.prop"]?.let { !it.isDir && it.size == 1234L } == true)
    check("toybox: bin 是软链、目标 /system/bin", byName["bin"]?.linkTarget == "/system/bin")
    check("toybox: sdcard 是软链、目标 /storage/self/primary", byName["sdcard"]?.linkTarget == "/storage/self/primary")
    check("toybox: . 和 .. 不在结果里", !byName.containsKey(".") && !byName.containsKey(".."))
    check("toybox: 路径拼对了", byName["3rdmodem"]?.path == "/3rdmodem")
    check("toybox: ISO 日期解析出时间戳", (byName["acct"]?.modified ?: 0L) > 0L)

    // ---- 2. GNU 格式（以防个别设备是 GNU coreutils 的 ls）----
    val gnu = """
        total 8
        drwxr-xr-x 41 root root 4096 Nov 17 07:18 .
        drwxr-xr-x 41 root root 4096 Nov 17 07:18 ..
        drwxr-xr-x  2 root root 4096 Jan  5  2024 data
        -rw-r--r--  1 root root  123 Nov 17 07:18 README.txt
        lrwxrwxrwx  1 root root   11 Nov 17 07:18 link -> /system/bin
    """.trimIndent().lines()
    val (gEntries, _) = ShizukuRoot.parseLsOutput(gnu, "/")
    val gByName = gEntries.associateBy { it.name }
    check("GNU: 解析出 3 条", gEntries.size == 3)
    check("GNU: data 是目录", gByName["data"]?.isDir == true)
    check("GNU: README.txt 是文件", gByName["README.txt"]?.isDir == false)
    check("GNU: link 目标 /system/bin", gByName["link"]?.linkTarget == "/system/bin")

    // ---- 3. 空目录：只有 total 行，0 条 0 候选（不是解析失败）----
    val (eEntries, eCandidates) = ShizukuRoot.parseLsOutput(listOf("total 0"), "/empty")
    check("空目录: 0 条 0 候选", eEntries.isEmpty() && eCandidates == 0)

    // ---- 4. 子目录路径拼接 ----
    val (sEntries, _) = ShizukuRoot.parseLsOutput(
        listOf("drwxr-xr-x  2 root root 4096 2026-11-18 07:18 sub"),
        "/system"
    )
    check("子目录: 路径 /system/sub", sEntries.firstOrNull()?.path == "/system/sub")

    println()
    if (failures == 0) println("ALL TESTS PASSED") else println("$failures TEST(S) FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
