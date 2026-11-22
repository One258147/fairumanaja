package com.fairuman.filemanager

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 文本编辑器（v1.7.0 照 MT 重做；v1.9.8.1 改 Compose）：
 * 工具栏（撤销/重做/保存/⋮）+ 状态行（文件名 + 行:列 + UTF-8）+
 * 左侧行号 + 编辑区 + 搜索条 + 底部符号栏；
 * ⋮ 菜单：文件（新建/保存/另存为）、搜索、语法着色、光标历史、
 * 转到指定行、自动换行、只读、流畅模式、代码补全、设置、关闭文件。
 * 仍支持 File 和 SAF URI 两种打开方式，UTF-8 读写，>2MB 先提示，读写走后台线程。
 *
 * Compose 实现说明：编辑区是 BasicTextField，行号栏与编辑区共享同一个
 * verticalScroll 状态；语法着色用 AnnotatedString 的 SpanStyle；
 * 撤销/重做沿用原来的 EditState 栈（800ms 合并、最多 50 条）；
 * 代码补全改成编辑区上方的候选词横排（原 ListPopupWindow 在 Compose 里
 * 没有等价的锚点弹出，改成横排 chips，点词即补全）。
 * 当前行高亮在 Compose 里没有等价实现，本版去掉（只影响一行底色）。
 */
class TextEditorActivity : BaseActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        /** SAF 文档的 content URI（字符串形式）；有它时按 URI 读写，不走 File。 */
        const val EXTRA_URI = "uri"
        const val MAX_SIZE = 2L * 1024 * 1024
        private const val PREFS = "fairumanaja_prefs"
        private const val KEY_FONT_SIZE = "editor_font_size"
        private const val KEY_SHOW_LINES = "editor_show_lines"
        private const val KEY_WRAP = "editor_wrap"
        private const val KEY_SMOOTH = "editor_smooth"
        private const val KEY_COMPLETION = "editor_completion"
        private const val KEY_SYNTAX = "editor_syntax"
        private const val UNDO_COALESCE_MS = 800L
        private const val UNDO_CAP = 50
        private const val HIGHLIGHT_DEBOUNCE_MS = 400L
        private const val HIGHLIGHT_MAX_LEN = 200_000

        private val KEYWORDS = mapOf(
            "java" to setOf("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record", "sealed", "permits", "yield"),
            "kotlin" to setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import", "init", "param", "property", "receiver", "set", "setparam", "where", "it", "data", "sealed", "open", "override", "private", "protected", "public", "internal", "companion", "lateinit", "suspend", "inline", "crossinline", "reified", "out", "vararg"),
            "python" to setOf("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "print"),
            "shell" to setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "select", "in", "time", "until", "echo", "cd", "ls", "export", "local", "return", "break", "continue"),
            "c" to setOf("auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "_Bool", "_Complex", "_Imaginary", "true", "false", "NULL", "include", "define", "ifdef", "ifndef", "endif", "pragma")
        )

        private val SYMBOLS = listOf(
            "→", "/", "+", "-", "*", "=", "<", ">", "(", ")", "[", "]",
            "{", "}", ";", ":", "\"", "'", ",", ".", "?", "\\", "|", "&", "%", "#"
        )
    }

    private data class EditState(val text: String, val sel: Int)

    private var currentFile: File? = null
    private var currentUri: Uri? = null
    private var uriName: String? = null
    private var loaded = false
    private var savedText: String? = ""
    private var dirty by mutableStateOf(false)

    // 编辑区状态：纯文本 + 光标；着色 spans 单独存，显示时合成 AnnotatedString。
    private var plainText by mutableStateOf("")
    private var selection by mutableStateOf(TextRange(0))
    private var spans by mutableStateOf<List<Triple<Int, Int, Int>>>(emptyList())

    // 撤销 / 重做
    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    private var suppressUndo = false
    private var lastUndoPushTime = 0L

    // 语法着色
    private var syntaxChoice = ""
    private var keywordColor = Color.BLUE
    private var stringColor = Color.GREEN
    private var commentColor = Color.GRAY
    private var numberColor = Color.MAGENTA
    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightRunnable: Runnable? = null

    // 光标历史
    private val cursorHistory = ArrayList<Int>()
    private var historyIndex by mutableStateOf(-1)
    private var suppressHistory = false
    private var lastHistoryTime = 0L

    // 搜索
    private var searchMatches by mutableStateOf<List<IntRange>>(emptyList())
    private var searchIndex by mutableStateOf(0)
    private var showSearch by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")

    // 开关与偏好
    private var fontSize by mutableStateOf(16)
    private var showLines by mutableStateOf(true)
    private var wrap by mutableStateOf(true)
    private var smoothMode by mutableStateOf(false)
    private var completionEnabled by mutableStateOf(false)
    private var readOnly by mutableStateOf(false)
    private var candidates by mutableStateOf<List<String>>(emptyList())
    private var candidateRange by mutableStateOf<IntRange?>(null)
    private var insertingCompletion = false

    // 菜单 / 对话框状态
    private var showMenu by mutableStateOf(false)
    private var showFileMenu by mutableStateOf(false)
    private var showSyntaxMenu by mutableStateOf(false)
    private var showGoto by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showSaveAs by mutableStateOf(false)
    private var showTooLarge by mutableStateOf(false)
    private var tooLargeAction: (() -> Unit)? = null
    // 未保存提示：两种场景——关闭文件（保存/不保存/取消）和新建文件（不保存/取消）。
    private var showUnsaved by mutableStateOf(false)
    private var unsavedSaveAction: (() -> Unit)? = null
    private var unsavedDontSaveAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        super.onCreate(savedInstanceState)
        try {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            fontSize = prefs.getInt(KEY_FONT_SIZE, 16)
            showLines = prefs.getBoolean(KEY_SHOW_LINES, true)
            wrap = prefs.getBoolean(KEY_WRAP, true)
            smoothMode = prefs.getBoolean(KEY_SMOOTH, false)
            completionEnabled = prefs.getBoolean(KEY_COMPLETION, false)
            syntaxChoice = prefs.getString(KEY_SYNTAX, "") ?: ""
            keywordColor = Color.BLUE
            commentColor = Color.GRAY
            numberColor = Color.MAGENTA
            stringColor = if (isNight()) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
            setContent {
                FairuTheme { EditorScreen() }
            }
            openFromIntent()
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun openFromIntent() {
        val uriStr = intent?.getStringExtra(EXTRA_URI)
        if (!uriStr.isNullOrBlank()) {
            val uri = try { Uri.parse(uriStr) } catch (_: Exception) { null }
            if (uri == null) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            currentUri = uri
            val (displayName, size) = queryUriInfo(uri)
            uriName = displayName
            if (size > MAX_SIZE) {
                tooLargeAction = { loadUri(uri) }
                showTooLarge = true
            } else {
                loadUri(uri)
            }
            return
        }
        val path = intent?.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            currentFile = null
            savedText = ""
            loaded = true
            return
        }
        val f = File(path)
        currentFile = f
        if (!f.exists() || !f.isFile) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (f.length() > MAX_SIZE) {
            tooLargeAction = { loadFile(f) }
            showTooLarge = true
        } else {
            loadFile(f)
        }
    }

    // ---------- 加载 / 保存（读写走后台线程） ----------

    private fun queryUriInfo(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val iSize = c.getColumnIndex(OpenableColumns.SIZE)
                    if (iName >= 0) name = c.getString(iName)
                    if (iSize >= 0) size = c.getLong(iSize)
                }
            }
        } catch (_: Exception) { }
        return Pair(name, size)
    }

    private fun loadFile(f: File) {
        Thread {
            try {
                val text = f.readText(Charsets.UTF_8)
                runOnUiThread { applyLoadedText(text) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }

    private fun loadUri(uri: Uri) {
        Thread {
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("cannot open $uri")
                runOnUiThread { applyLoadedText(text) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.start()
    }

    private fun applyLoadedText(text: String) {
        savedText = text
        suppressUndo = true
        plainText = text
        selection = TextRange(0)
        suppressUndo = false
        undoStack.clear()
        redoStack.clear()
        spans = emptyList()
        dirty = false
        loaded = true
        cursorHistory.clear()
        historyIndex = -1
        requestHighlight()
    }

    private fun save() {
        val uri = currentUri
        if (uri != null) {
            val text = plainText
            Thread {
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("cannot open $uri for write")
                    runOnUiThread {
                        savedText = text
                        dirty = false
                        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show() }
                }
            }.start()
            return
        }
        val target = currentFile
        if (target == null) {
            showSaveAs = true
        } else {
            writeFile(target)
        }
    }

    private fun writeFile(file: File, onDone: (() -> Unit)? = null) {
        val text = plainText
        Thread {
            try {
                file.parentFile?.mkdirs()
                file.writeText(text, Charsets.UTF_8)
                runOnUiThread {
                    savedText = text
                    dirty = false
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun doSaveAs(name: String) {
        if (name.isEmpty()) return
        val dir = currentFile?.parentFile ?: Environment.getExternalStorageDirectory()
        val f = File(dir, name)
        currentFile = f
        currentUri = null
        uriName = null
        writeFile(f) { requestHighlight() }
    }

    private fun newFile() {
        if (dirty) {
            unsavedSaveAction = null
            unsavedDontSaveAction = { doNewFile() }
            showUnsaved = true
        } else {
            doNewFile()
        }
    }

    private fun doNewFile() {
        currentFile = null
        currentUri = null
        uriName = null
        suppressUndo = true
        plainText = ""
        selection = TextRange(0)
        suppressUndo = false
        undoStack.clear()
        redoStack.clear()
        spans = emptyList()
        savedText = ""
        dirty = false
        cursorHistory.clear()
        historyIndex = -1
        requestHighlight()
    }

    private fun closeFile() {
        if (!dirty) {
            finish()
            return
        }
        unsavedSaveAction = { save(); finish() }
        unsavedDontSaveAction = { finish() }
        showUnsaved = true
    }

    // ---------- 撤销 / 重做 ----------

    private fun pushUndoBeforeChange(oldText: String, oldSel: Int) {
        if (suppressUndo) return
        val now = SystemClock.uptimeMillis()
        if (undoStack.isEmpty() || now - lastUndoPushTime > UNDO_COALESCE_MS) {
            undoStack.addFirst(EditState(oldText, oldSel))
            if (undoStack.size > UNDO_CAP) undoStack.removeLast()
            lastUndoPushTime = now
        }
        redoStack.clear()
    }

    private fun undo() {
        if (readOnly || undoStack.isEmpty()) return
        val cur = EditState(plainText, selection.start.coerceAtLeast(0))
        redoStack.addFirst(cur)
        val st = undoStack.removeFirst()
        suppressUndo = true
        plainText = st.text
        selection = TextRange(st.sel.coerceIn(0, st.text.length))
        suppressUndo = false
        spans = emptyList()
        dirty = plainText != savedText
        requestHighlight()
    }

    private fun redo() {
        if (readOnly || redoStack.isEmpty()) return
        val cur = EditState(plainText, selection.start.coerceAtLeast(0))
        undoStack.addFirst(cur)
        val st = redoStack.removeFirst()
        suppressUndo = true
        plainText = st.text
        selection = TextRange(st.sel.coerceIn(0, st.text.length))
        suppressUndo = false
        spans = emptyList()
        dirty = plainText != savedText
        requestHighlight()
    }

    private fun lineCount(): Int = plainText.count { it == '\n' } + 1

    private fun cursorLineCol(): Pair<Int, Int> {
        val sel = selection.start.coerceAtLeast(0).coerceAtMost(plainText.length)
        val before = plainText.substring(0, sel)
        val nl = before.lastIndexOf('\n')
        return (before.count { it == '\n' } + 1) to (sel - nl)
    }

    private fun lineStartOffset(lineOneBased: Int): Int {
        var offset = 0
        var line = 1
        while (line < lineOneBased) {
            val idx = plainText.indexOf('\n', offset)
            if (idx == -1) return plainText.length
            offset = idx + 1
            line++
        }
        return offset
    }

    // ---------- 光标历史 ----------

    private fun recordCursorPosition(pos: Int) {
        val now = SystemClock.uptimeMillis()
        if (cursorHistory.isEmpty() || cursorHistory.last() != pos) {
            if (cursorHistory.isEmpty() || now - lastHistoryTime > 500) {
                if (historyIndex < cursorHistory.size - 1) {
                    cursorHistory.subList(historyIndex + 1, cursorHistory.size).clear()
                }
                cursorHistory.add(pos)
                if (cursorHistory.size > 100) cursorHistory.removeAt(0)
                historyIndex = cursorHistory.size - 1
                lastHistoryTime = now
            }
        }
    }

    private fun jumpToOffset(offset: Int) {
        suppressHistory = true
        selection = TextRange(offset.coerceIn(0, plainText.length))
        suppressHistory = false
    }

    private fun goPrevPosition() {
        if (historyIndex <= 0) return
        historyIndex--
        jumpToOffset(cursorHistory[historyIndex])
    }

    private fun goNextPosition() {
        if (historyIndex < 0 || historyIndex >= cursorHistory.size - 1) return
        historyIndex++
        jumpToOffset(cursorHistory[historyIndex])
    }

    // ---------- 底部符号栏 ----------

    private fun insertSymbol(sym: String) {
        if (readOnly) return
        val insert = if (sym == "→") "\t" else sym
        val start = selection.start.coerceAtLeast(0)
        val end = selection.end.coerceAtLeast(start)
        val newText = plainText.substring(0, start) + insert + plainText.substring(end)
        pushUndoBeforeChange(plainText, start)
        plainText = newText
        selection = TextRange(start + insert.length)
        dirty = plainText != savedText
        requestHighlight()
        maybeShowCompletion()
    }

    // ---------- 搜索 ----------

    private fun runSearch() {
        val kw = searchQuery
        val text = plainText
        searchMatches = if (kw.isEmpty()) {
            emptyList()
        } else {
            val lower = text.lowercase()
            val kl = kw.lowercase()
            val list = ArrayList<IntRange>()
            var idx = lower.indexOf(kl)
            while (idx >= 0) {
                list.add(idx until idx + kw.length)
                idx = lower.indexOf(kl, idx + kw.length)
            }
            list
        }
        if (searchMatches.isEmpty()) {
            return
        }
        searchIndex = 0
        selectSearchMatch()
    }

    private fun selectSearchMatch() {
        if (searchMatches.isEmpty()) return
        val m = searchMatches[searchIndex]
        suppressHistory = true
        selection = TextRange(m.first, m.last + 1)
        suppressHistory = false
    }

    private fun moveSearch(delta: Int) {
        if (searchMatches.isEmpty()) return
        searchIndex = (searchIndex + delta + searchMatches.size) % searchMatches.size
        selectSearchMatch()
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_FONT_SIZE, fontSize)
            .putBoolean(KEY_SHOW_LINES, showLines)
            .putBoolean(KEY_WRAP, wrap)
            .putBoolean(KEY_SMOOTH, smoothMode)
            .putBoolean(KEY_COMPLETION, completionEnabled)
            .putString(KEY_SYNTAX, syntaxChoice)
            .apply()
    }

    // ---------- 语法着色 ----------

    private fun effectiveSyntax(): String {
        if (syntaxChoice.isNotEmpty()) return syntaxChoice
        val ext = (currentFile?.extension ?: uriName?.substringAfterLast('.', "") ?: "")
            .lowercase()
        return when (ext) {
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "xml" -> "xml"
            "json" -> "json"
            "py" -> "python"
            "sh", "bash" -> "shell"
            "c", "h", "cpp", "cc", "cxx" -> "c"
            else -> "plain"
        }
    }

    private fun requestHighlight() {
        highlightRunnable?.let { highlightHandler.removeCallbacks(it) }
        val snapshot = plainText
        if (snapshot.length > HIGHLIGHT_MAX_LEN) {
            spans = emptyList()
            return
        }
        val lang = effectiveSyntax()
        val kw = keywordColor
        val str = stringColor
        val cmt = commentColor
        val num = numberColor
        val r = Runnable {
            Thread {
                val computed = computeHighlights(snapshot, lang, kw, str, cmt, num)
                runOnUiThread {
                    if (plainText != snapshot) return@runOnUiThread
                    spans = computed
                }
            }.start()
        }
        highlightRunnable = r
        highlightHandler.postDelayed(r, HIGHLIGHT_DEBOUNCE_MS)
    }

    private fun scanString(text: String, start: Int): Int {
        val q = text[start]
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == q) return i + 1
            if (c == '\n') return i
            i++
        }
        return text.length
    }

    private fun computeHighlights(
        text: String, lang: String, kwColor: Int, strColor: Int, cmtColor: Int, numColor: Int
    ): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        if (lang == "plain") return out
        val keywords = KEYWORDS[lang] ?: emptySet()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (lang != "python" && lang != "shell" && c == '/' && i + 1 < n && text[i + 1] == '/') {
                val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                out.add(Triple(i, end, cmtColor)); i = end; continue
            }
            if (lang != "python" && lang != "shell" && c == '/' && i + 1 < n && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                out.add(Triple(i, end, cmtColor)); i = end; continue
            }
            if ((lang == "python" || lang == "shell") && c == '#') {
                val end = text.indexOf('\n', i).let { if (it == -1) n else it }
                out.add(Triple(i, end, cmtColor)); i = end; continue
            }
            if (lang == "xml") {
                if (text.startsWith("<!--", i)) {
                    val end = text.indexOf("-->", i + 4).let { if (it == -1) n else it + 3 }
                    out.add(Triple(i, end, cmtColor)); i = end; continue
                }
                if (c == '<') {
                    val end = text.indexOf('>', i).let { if (it == -1) n else it + 1 }
                    out.add(Triple(i, end, kwColor)); i = end; continue
                }
                i++; continue
            }
            if (lang == "json") {
                if (c == '"') {
                    val end = scanString(text, i)
                    var j = end
                    while (j < n && text[j].isWhitespace()) j++
                    val color = if (j < n && text[j] == ':') kwColor else strColor
                    out.add(Triple(i, end, color)); i = end; continue
                }
                if (c.isDigit() || (c == '-' && i + 1 < n && text[i + 1].isDigit())) {
                    var j = i + 1
                    while (j < n && (text[j].isDigit() || text[j] == '.' || text[j] == 'e' || text[j] == 'E' || text[j] == '+' || text[j] == '-')) j++
                    out.add(Triple(i, j, numColor)); i = j; continue
                }
                if (c.isLetter()) {
                    var j = i
                    while (j < n && text[j].isLetter()) j++
                    val w = text.substring(i, j)
                    if (w == "true" || w == "false" || w == "null") out.add(Triple(i, j, kwColor))
                    i = j; continue
                }
                i++; continue
            }
            if (c == '"' || c == '\'') {
                val end = scanString(text, i)
                out.add(Triple(i, end, strColor)); i = end; continue
            }
            if (c.isDigit()) {
                var j = i
                while (j < n && (text[j].isDigit() || text[j] == '.' || text[j] == 'x' || text[j] == 'X' || text[j].isLetter())) j++
                out.add(Triple(i, j, numColor)); i = j; continue
            }
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                val w = text.substring(i, j)
                if (keywords.contains(w)) out.add(Triple(i, j, kwColor))
                i = j; continue
            }
            i++
        }
        return out
    }

    // ---------- 代码补全 ----------

    private fun maybeShowCompletion() {
        if (!completionEnabled || readOnly || suppressUndo || insertingCompletion) {
            candidates = emptyList()
            candidateRange = null
            return
        }
        val text = plainText
        val sel = selection.start
        if (sel <= 0) {
            candidates = emptyList()
            candidateRange = null
            return
        }
        var start = sel
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        val prefix = text.substring(start, sel)
        if (prefix.length < 2) {
            candidates = emptyList()
            candidateRange = null
            return
        }
        val list = text.split(Regex("[^A-Za-z0-9_]+"))
            .filter { it.length >= prefix.length && it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }
            .distinct()
            .sorted()
            .take(8)
        candidates = list
        candidateRange = if (list.isEmpty()) null else start until sel
    }

    private fun applyCompletion(word: String) {
        val range = candidateRange ?: return
        insertingCompletion = true
        pushUndoBeforeChange(plainText, range.first)
        plainText = plainText.substring(0, range.first) + word + plainText.substring(range.last)
        selection = TextRange(range.first + word.length)
        insertingCompletion = false
        candidates = emptyList()
        candidateRange = null
        dirty = plainText != savedText
        requestHighlight()
    }

    // ---------- 界面 ----------

    @Composable
    private fun EditorScreen() {
        val scrollState = rememberScrollState()
        val hScrollState = rememberScrollState()
        val (line, col) = cursorLineCol()
        val fileName = currentFile?.name ?: uriName ?: stringResource(R.string.new_text_file)
        val pathText = when {
            currentFile != null -> currentFile!!.absolutePath
            currentUri != null -> currentUri.toString()
            else -> ""
        }
        val annotated = remember(plainText, spans) {
            val builder = AnnotatedString.Builder(plainText)
            for ((s, e, c) in spans) {
                if (s < e && e <= plainText.length) {
                    builder.addStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(c)), s, e)
                }
            }
            builder.toAnnotatedString()
        }
        val displayValue = remember(annotated, selection) {
            TextFieldValue(annotated, selection)
        }
        // Miuix：工具栏浅灰底 + 深色字；MD3：主色底 + 白字。
        val miuix = isMiuixEngine()
        val barBg = if (miuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
        else androidx.compose.material3.MaterialTheme.colorScheme.primary
        val barFg = if (miuix) top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
        else androidx.compose.ui.graphics.Color.White
        Column(Modifier.fillMaxSize()) {
            // 工具栏：返回 / 文件名 / 撤销 / 重做 / 保存 / ⋮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barBg)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { closeFile() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = barFg)
                }
                Text(
                    fileName,
                    fontSize = 18.sp,
                    fontWeight = if (miuix) androidx.compose.ui.text.font.FontWeight.Bold
                    else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = barFg,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = { undo() }, enabled = !readOnly && undoStack.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.undo), tint = barFg)
                }
                IconButton(onClick = { redo() }, enabled = !readOnly && redoStack.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_redo), contentDescription = stringResource(R.string.redo), tint = barFg)
                }
                IconButton(onClick = { save() }) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = stringResource(R.string.save), tint = barFg)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, tint = barFg)
                    }
                    EditorMenu()
                }
            }
            // 状态行：路径 + 行:列 + UTF-8
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pathText,
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text("$line:$col  UTF-8", fontSize = 12.sp)
            }
            // 搜索条
            if (showSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; runSearch() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.editor_search)) }
                    )
                    Text(
                        if (searchMatches.isEmpty()) {
                            if (searchQuery.isEmpty()) "" else stringResource(R.string.no_match)
                        } else {
                            stringResource(R.string.search_match_count, searchIndex + 1, searchMatches.size)
                        },
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { moveSearch(-1) }) {
                        Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null)
                    }
                    IconButton(onClick = { moveSearch(1) }) {
                        Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null)
                    }
                    IconButton(onClick = {
                        showSearch = false
                        searchMatches = emptyList()
                        searchQuery = ""
                    }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                    }
                }
            }
            // 编辑区：行号栏 + BasicTextField，共享垂直滚动。
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLines) {
                    Column(
                        Modifier
                            .verticalScroll(scrollState, enabled = false)
                            .padding(start = 8.dp, end = 4.dp, top = 8.dp)
                    ) {
                        val n = lineCount()
                        for (i in 1..n) {
                            Text(
                                i.toString(),
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height((fontSize * 1.5f).dp)
                            )
                        }
                    }
                }
                BasicTextField(
                    value = displayValue,
                    onValueChange = { newValue ->
                        val oldText = plainText
                        val oldSel = selection.start
                        if (!suppressUndo && newValue.text != oldText) {
                            pushUndoBeforeChange(oldText, oldSel)
                        }
                        plainText = newValue.text
                        selection = newValue.selection
                        dirty = plainText != savedText
                        if (!suppressHistory) recordCursorPosition(newValue.selection.start)
                        requestHighlight()
                        maybeShowCompletion()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .then(if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(hScrollState))
                        .padding(8.dp),
                    textStyle = TextStyle(
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(androidx.compose.material3.MaterialTheme.colorScheme.primary),
                    readOnly = readOnly
                )
            }
            // 代码补全候选词横排。
            if (candidates.isNotEmpty()) {
                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(candidates) { word ->
                        OutlinedButton(
                            onClick = { applyCompletion(word) },
                            modifier = Modifier.padding(end = 4.dp)
                        ) { Text(word) }
                    }
                }
            }
            // 底部符号栏。
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = 4.dp)
            ) {
                items(SYMBOLS) { sym ->
                    OutlinedButton(
                        onClick = { insertSymbol(sym) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) { Text(sym, fontSize = 16.sp) }
                }
            }
        }
        EditorDialogs()
    }

    @Composable
    private fun EditorMenu() {
        FairuDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.file)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_file), contentDescription = null) },
                onClick = { showMenu = false; showFileMenu = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                onClick = { showMenu = false; showSearch = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.syntax)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
                onClick = { showMenu = false; showSyntaxMenu = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.prev_position)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) },
                enabled = historyIndex > 0,
                onClick = { showMenu = false; goPrevPosition() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.next_position)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null) },
                enabled = historyIndex >= 0 && historyIndex < cursorHistory.size - 1,
                onClick = { showMenu = false; goNextPosition() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.go_to_line)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null) },
                onClick = { showMenu = false; showGoto = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.word_wrap)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_wrap), contentDescription = null) },
                trailingIcon = { Checkbox(checked = wrap, onCheckedChange = null) },
                onClick = { wrap = !wrap; savePrefs() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.read_only)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_eye), contentDescription = null) },
                trailingIcon = { Checkbox(checked = readOnly, onCheckedChange = null) },
                onClick = { readOnly = !readOnly; savePrefs() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.smooth_mode)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_smooth), contentDescription = null) },
                trailingIcon = { Checkbox(checked = smoothMode, onCheckedChange = null) },
                onClick = { smoothMode = !smoothMode; savePrefs() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_completion)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_complete), contentDescription = null) },
                trailingIcon = { Checkbox(checked = completionEnabled, onCheckedChange = null) },
                onClick = {
                    completionEnabled = !completionEnabled
                    if (!completionEnabled) candidates = emptyList()
                    savePrefs()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_drawer_settings), contentDescription = null) },
                onClick = { showMenu = false; showSettings = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.close_file)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                onClick = { showMenu = false; closeFile() }
            )
        }
        FairuDropdownMenu(expanded = showFileMenu, onDismissRequest = { showFileMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_file)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = { showFileMenu = false; newFile() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_save), contentDescription = null) },
                onClick = { showFileMenu = false; save() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_as)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_file), contentDescription = null) },
                onClick = { showFileMenu = false; showSaveAs = true }
            )
        }
        val langs = listOf(
            "" to R.string.syntax_auto,
            "plain" to R.string.syntax_plain,
            "java" to R.string.syntax_java,
            "kotlin" to R.string.syntax_kotlin,
            "xml" to R.string.syntax_xml,
            "json" to R.string.syntax_json,
            "python" to R.string.syntax_python,
            "shell" to R.string.syntax_shell,
            "c" to R.string.syntax_c
        )
        FairuDropdownMenu(expanded = showSyntaxMenu, onDismissRequest = { showSyntaxMenu = false }) {
            langs.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(painterResource(if (id == "plain" || id.isEmpty()) R.drawable.ic_file else R.drawable.ic_code), contentDescription = null) },
                    trailingIcon = {
                        if (id == syntaxChoice || (syntaxChoice.isEmpty() && id.isEmpty())) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                        }
                    },
                    onClick = {
                        showSyntaxMenu = false
                        syntaxChoice = id
                        savePrefs()
                        requestHighlight()
                    }
                )
            }
        }
    }

    @Composable
    private fun EditorDialogs() {
        if (showGoto) {
            var lineInput by remember { mutableStateOf("") }
            FairuAlertDialog(
                onDismissRequest = { showGoto = false },
                title = { Text(stringResource(R.string.go_to_line)) },
                text = {
                    OutlinedTextField(
                        value = lineInput,
                        onValueChange = { lineInput = it.filter { c -> c.isDigit() } },
                        placeholder = { Text(stringResource(R.string.go_to_line_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showGoto = false
                        val line = lineInput.toIntOrNull() ?: return@TextButton
                        jumpToOffset(lineStartOffset(line.coerceIn(1, lineCount())))
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showGoto = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showSaveAs) {
            var nameInput by remember { mutableStateOf(currentFile?.name ?: uriName ?: "untitled.txt") }
            FairuAlertDialog(
                onDismissRequest = { showSaveAs = false },
                title = { Text(stringResource(R.string.save_as)) },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = { Text(stringResource(R.string.file_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveAs = false
                        doSaveAs(nameInput.trim())
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAs = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showSettings) {
            var size by remember { mutableStateOf(fontSize) }
            var lines by remember { mutableStateOf(showLines) }
            FairuAlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text(stringResource(R.string.settings)) },
                text = {
                    Column {
                        Text(stringResource(R.string.font_size) + ": " + size + "sp")
                        Slider(
                            value = size.toFloat(),
                            onValueChange = { size = it.toInt().coerceIn(12, 28) },
                            valueRange = 12f..28f,
                            steps = 15
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = lines, onCheckedChange = { lines = it })
                            Text(stringResource(R.string.line_numbers))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSettings = false
                        fontSize = size
                        showLines = lines
                        savePrefs()
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showTooLarge) {
            FairuAlertDialog(
                onDismissRequest = { showTooLarge = false },
                title = { Text(stringResource(R.string.text_editor)) },
                text = { Text(stringResource(R.string.file_too_large)) },
                confirmButton = {
                    TextButton(onClick = {
                        showTooLarge = false
                        tooLargeAction?.invoke()
                    }) { Text(stringResource(R.string.open)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTooLarge = false; finish() }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showUnsaved) {
            FairuAlertDialog(
                onDismissRequest = { showUnsaved = false },
                title = { Text(stringResource(R.string.unsaved_title)) },
                text = { Text(stringResource(R.string.unsaved_message)) },
                confirmButton = {
                    if (unsavedSaveAction != null) {
                        TextButton(onClick = {
                            showUnsaved = false
                            unsavedSaveAction?.invoke()
                        }) { Text(stringResource(R.string.save)) }
                    } else {
                        TextButton(onClick = {
                            showUnsaved = false
                            unsavedDontSaveAction?.invoke()
                        }) { Text(stringResource(R.string.dont_save)) }
                    }
                },
                dismissButton = {
                    Row {
                        if (unsavedSaveAction != null) {
                            TextButton(onClick = {
                                showUnsaved = false
                                unsavedDontSaveAction?.invoke()
                            }) { Text(stringResource(R.string.dont_save)) }
                        }
                        TextButton(onClick = { showUnsaved = false }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            )
        }
    }
}
