package com.fairuman.filemanager

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.KeyListener
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.graphics.ColorUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * 文本编辑器（v1.7.0 照 MT 重做）：
 * 工具栏（撤销/重做/保存/⋮）+ 状态行（文件名 + 行:列 + UTF-8）+
 * 左侧行号 + 编辑区 + 搜索条 + 底部符号栏；
 * ⋮ 菜单：文件（新建/保存/另存为）、搜索、语法着色、光标历史、
 * 转到指定行、自动换行、只读、流畅模式、代码补全、设置、关闭文件。
 * 仍支持 File 和 SAF URI 两种打开方式，UTF-8 读写，>2MB 先提示，读写走后台线程。
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

        // ⋮ 菜单项 id（PopupMenu 用，不与 R.id 冲突）
        private const val M_FILE = 1001
        private const val M_SEARCH = 1002
        private const val M_SYNTAX = 1003
        private const val M_PREV = 1004
        private const val M_NEXT = 1005
        private const val M_GOTO = 1006
        private const val M_WRAP = 1007
        private const val M_READONLY = 1008
        private const val M_SMOOTH = 1009
        private const val M_COMPLETE = 1010
        private const val M_SETTINGS = 1011
        private const val M_CLOSE = 1012
        private const val M_NEW = 1101
        private const val M_SAVE = 1102
        private const val M_SAVE_AS = 1103

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

    private lateinit var toolbar: MaterialToolbar
    private lateinit var editorText: CursorAwareEditText
    private lateinit var gutter: TextView
    private lateinit var pathView: TextView
    private lateinit var cursorView: TextView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchCount: TextView
    private lateinit var symbolRow: LinearLayout

    private var currentFile: File? = null
    private var currentUri: Uri? = null
    private var uriName: String? = null
    private var loaded = false
    private var savedText: String? = ""
    private var dirty = false

    // 撤销 / 重做
    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    private var suppressUndo = false
    private var lastUndoPushTime = 0L

    // 语法着色
    private var syntaxChoice = "" // ""=按扩展名自动，否则为指定语言
    private var keywordColor = Color.BLUE
    private var stringColor = Color.GREEN
    private var commentColor = Color.GRAY
    private var numberColor = Color.MAGENTA
    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightRunnable: Runnable? = null
    private var lineBgSpan: BackgroundColorSpan? = null

    // 光标历史
    private val cursorHistory = ArrayList<Int>()
    private var historyIndex = -1
    private var suppressHistory = false
    private var lastHistoryTime = 0L

    // 搜索
    private var searchMatches: List<IntRange> = emptyList()
    private var searchIndex = 0

    // 开关与偏好
    private var fontSize = 16
    private var showLines = true
    private var wrap = true
    private var smoothMode = false
    private var completionEnabled = false
    private var readOnly = false
    private var savedKeyListener: KeyListener? = null
    private var completionPopup: ListPopupWindow? = null
    private var insertingCompletion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // super.onCreate 必须无条件最先调用。
        super.onCreate(savedInstanceState)
        try {
            onCreateInner(savedInstanceState)
        } catch (e: Throwable) {
            CrashHandler.writeLog(this, e)
            try {
                MaterialAlertDialogBuilder(this)
                    .setTitle("启动失败")
                    .setMessage("启动时出错了，错误已保存到 crash.log。\n\n" + CrashHandler.logPathText(this) + "\n\n错误：" + e.toString())
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (_: Exception) { }
            try { finish() } catch (_: Exception) { }
        }
    }

    private fun onCreateInner(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_editor)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        fontSize = prefs.getInt(KEY_FONT_SIZE, 16)
        showLines = prefs.getBoolean(KEY_SHOW_LINES, true)
        wrap = prefs.getBoolean(KEY_WRAP, true)
        smoothMode = prefs.getBoolean(KEY_SMOOTH, false)
        completionEnabled = prefs.getBoolean(KEY_COMPLETION, false)
        syntaxChoice = prefs.getString(KEY_SYNTAX, "") ?: ""

        toolbar = findViewById(R.id.editorToolbar)
        editorText = findViewById(R.id.editorText)
        gutter = findViewById(R.id.lineNumbers)
        pathView = findViewById(R.id.editorPath)
        cursorView = findViewById(R.id.editorCursor)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        searchCount = findViewById(R.id.searchCount)
        symbolRow = findViewById(R.id.symbolRow)

        toolbar.setNavigationOnClickListener { closeFile() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_editor_undo -> { undo(); true }
                R.id.menu_editor_redo -> { redo(); true }
                R.id.menu_editor_save -> { save(); true }
                R.id.menu_editor_more -> { showEditorMenu(); true }
                else -> false
            }
        }

        keywordColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        commentColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        numberColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, Color.MAGENTA)
        stringColor = if (isNight()) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()

        editorText.typeface = Typeface.MONOSPACE
        editorText.textSize = fontSize.toFloat()
        gutter.textSize = fontSize.toFloat()
        gutter.visibility = if (showLines) View.VISIBLE else View.GONE
        applyWrap()
        editorText.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            gutter.scrollTo(0, scrollY)
        }
        editorText.onSelectionChanged = { start, _ ->
            updateCursorStatus()
            updateCurrentLine()
            completionPopup?.dismiss()
            if (!suppressHistory) recordCursorPosition(start)
        }
        editorText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (suppressUndo) return
                val now = SystemClock.uptimeMillis()
                if (undoStack.isEmpty() || now - lastUndoPushTime > UNDO_COALESCE_MS) {
                    undoStack.addFirst(EditState(s?.toString() ?: "", editorText.selectionStart.coerceAtLeast(0)))
                    if (undoStack.size > UNDO_CAP) undoStack.removeLast()
                    lastUndoPushTime = now
                }
                redoStack.clear()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                dirty = (s?.toString() != savedText)
                updateUndoRedo()
                updateLineNumbers()
                requestHighlight()
                maybeShowCompletion()
            }
        })

        buildSymbolBar()
        findViewById<ImageButton>(R.id.searchUp).setOnClickListener { moveSearch(-1) }
        findViewById<ImageButton>(R.id.searchDown).setOnClickListener { moveSearch(1) }
        findViewById<ImageButton>(R.id.searchClose).setOnClickListener { hideSearch() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch() }
        })

        val uriStr = intent?.getStringExtra(EXTRA_URI)
        if (!uriStr.isNullOrBlank()) {
            // URI 模式（v1.7.0：SAF 储存里的文本文件）：读流进来，保存写流回去。
            val uri = try { Uri.parse(uriStr) } catch (_: Exception) { null }
            if (uri == null) {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            currentUri = uri
            val (displayName, size) = queryUriInfo(uri)
            uriName = displayName
            updateTitleAndPath()
            if (size > MAX_SIZE) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.text_editor).setMessage(R.string.file_too_large)
                    .setPositiveButton(R.string.open) { _, _ -> loadUri(uri) }
                    .setNegativeButton(R.string.cancel) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } else {
                loadUri(uri)
            }
            return
        }
        val path = intent?.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            // 新建模式。
            currentFile = null
            savedText = ""
            loaded = true
            updateTitleAndPath()
            updateLineNumbers()
            updateUndoRedo()
            return
        }
        val f = File(path)
        currentFile = f
        updateTitleAndPath()
        if (!f.exists() || !f.isFile) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (f.length() > MAX_SIZE) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.text_editor).setMessage(R.string.file_too_large)
                .setPositiveButton(R.string.open) { _, _ -> loadFile(f) }
                .setNegativeButton(R.string.cancel) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            loadFile(f)
        }
    }

    override fun onBackPressed() {
        if (searchBar.visibility == View.VISIBLE) {
            hideSearch()
            return
        }
        closeFile()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ---------- 加载 / 保存（读写走后台线程） ----------

    /** 问提供方要一个 URI 文档的显示名和大小（查不到大小返回 0）。 */
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
        editorText.setText(text)
        editorText.setSelection(0)
        suppressUndo = false
        undoStack.clear()
        redoStack.clear()
        lineBgSpan = null
        dirty = false
        loaded = true
        cursorHistory.clear()
        historyIndex = -1
        updateTitleAndPath()
        updateLineNumbers()
        updateUndoRedo()
        updateCursorStatus()
        requestHighlight()
    }

    private fun save() {
        val uri = currentUri
        if (uri != null) {
            // URI 模式：写流回去（"wt" 截断重写）。
            val text = editorText.text.toString()
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
            saveAs()
        } else {
            writeFile(target)
        }
    }

    private fun writeFile(file: File, onDone: (() -> Unit)? = null) {
        val text = editorText.text.toString()
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

    private fun saveAs() {
        val input = EditText(this).apply {
            hint = getString(R.string.file_name_hint)
            setText(currentFile?.name ?: uriName ?: "untitled.txt")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.save_as)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                // File 模式：存到原文件同目录；SAF 模式：内部储存根目录（写不进会弹错）。
                val dir = currentFile?.parentFile ?: Environment.getExternalStorageDirectory()
                val f = File(dir, name)
                currentFile = f
                currentUri = null
                uriName = null
                updateTitleAndPath()
                writeFile(f) { requestHighlight() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun newFile() {
        if (dirty) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.dont_save) { _, _ -> doNewFile() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            doNewFile()
        }
    }

    private fun doNewFile() {
        currentFile = null
        currentUri = null
        uriName = null
        suppressUndo = true
        editorText.setText("")
        editorText.setSelection(0)
        suppressUndo = false
        undoStack.clear()
        redoStack.clear()
        lineBgSpan = null
        savedText = ""
        dirty = false
        cursorHistory.clear()
        historyIndex = -1
        updateTitleAndPath()
        updateLineNumbers()
        updateUndoRedo()
        updateCursorStatus()
        requestHighlight()
    }

    private fun closeFile() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.save) { _, _ -> save(); finish() }
            .setNeutralButton(R.string.dont_save) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateTitleAndPath() {
        val name = currentFile?.name ?: uriName ?: getString(R.string.new_text_file)
        toolbar.title = name
        pathView.text = when {
            currentFile != null -> currentFile!!.absolutePath
            currentUri != null -> currentUri.toString()
            else -> ""
        }
    }

    // ---------- 撤销 / 重做 ----------

    private fun undo() {
        if (readOnly || undoStack.isEmpty()) return
        val cur = EditState(editorText.text?.toString() ?: "", editorText.selectionStart.coerceAtLeast(0))
        redoStack.addFirst(cur)
        val st = undoStack.removeFirst()
        suppressUndo = true
        editorText.setText(st.text)
        editorText.setSelection(st.sel.coerceIn(0, st.text.length))
        suppressUndo = false
        lineBgSpan = null
        updateUndoRedo()
        updateLineNumbers()
        updateCursorStatus()
        requestHighlight()
    }

    private fun redo() {
        if (readOnly || redoStack.isEmpty()) return
        val cur = EditState(editorText.text?.toString() ?: "", editorText.selectionStart.coerceAtLeast(0))
        undoStack.addFirst(cur)
        val st = redoStack.removeFirst()
        suppressUndo = true
        editorText.setText(st.text)
        editorText.setSelection(st.sel.coerceIn(0, st.text.length))
        suppressUndo = false
        lineBgSpan = null
        updateUndoRedo()
        updateLineNumbers()
        updateCursorStatus()
        requestHighlight()
    }

    private fun updateUndoRedo() {
        toolbar.menu.findItem(R.id.menu_editor_undo)?.isEnabled = !readOnly && undoStack.isNotEmpty()
        toolbar.menu.findItem(R.id.menu_editor_redo)?.isEnabled = !readOnly && redoStack.isNotEmpty()
    }

    // ---------- 行号 / 光标状态 ----------

    private fun lineCount(): Int {
        val t = editorText.text?.toString() ?: ""
        return t.count { it == '\n' } + 1
    }

    private fun updateLineNumbers() {
        if (!showLines) return
        val n = lineCount()
        val sb = StringBuilder()
        for (i in 1..n) {
            if (i > 1) sb.append('\n')
            sb.append(i)
        }
        gutter.text = sb.toString()
        gutter.scrollTo(0, editorText.scrollY)
    }

    private fun updateCursorStatus() {
        val text = editorText.text?.toString() ?: ""
        val sel = editorText.selectionStart.coerceAtLeast(0).coerceAtMost(text.length)
        val layout = editorText.layout
        val (line, col) = if (layout != null && sel <= layout.text.length) {
            val l = layout.getLineForOffset(sel)
            (l + 1) to (sel - layout.getLineStart(l) + 1)
        } else {
            val before = text.substring(0, sel)
            val nl = before.lastIndexOf('\n')
            (before.count { it == '\n' } + 1) to (sel - nl)
        }
        cursorView.text = "$line:$col  UTF-8"
    }

    private fun updateCurrentLine() {
        val editable = editorText.text ?: return
        lineBgSpan?.let { editable.removeSpan(it); lineBgSpan = null }
        val layout = editorText.layout ?: return
        val sel = editorText.selectionStart.coerceAtLeast(0).coerceAtMost(editable.length)
        if (sel > layout.text.length) return
        val line = layout.getLineForOffset(sel)
        val start = layout.getLineStart(line)
        val end = layout.getLineEnd(line)
        val color = ColorUtils.setAlphaComponent(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE), 24
        )
        val span = BackgroundColorSpan(color)
        if (start < end) {
            editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            lineBgSpan = span
        }
    }

    private fun scrollToOffset(offset: Int) {
        val layout = editorText.layout ?: return
        val line = layout.getLineForOffset(offset.coerceIn(0, layout.text.length))
        val top = layout.getLineTop(line)
        // EditText 没有 smoothScrollTo：流畅模式用 ValueAnimator 做平滑滚动。
        if (smoothMode) {
            val startY = editorText.scrollY
            if (startY != top) {
                val anim = android.animation.ValueAnimator.ofInt(startY, top)
                anim.duration = 200
                anim.addUpdateListener { editorText.scrollTo(0, it.animatedValue as Int) }
                anim.start()
            }
        } else {
            editorText.scrollTo(0, top)
        }
    }

    private fun lineStartOffset(lineOneBased: Int): Int {
        val layout = editorText.layout
        if (layout != null && lineOneBased - 1 < layout.lineCount) {
            return layout.getLineStart(lineOneBased - 1)
        }
        val text = editorText.text?.toString() ?: ""
        var offset = 0
        var line = 1
        while (line < lineOneBased) {
            val idx = text.indexOf('\n', offset)
            if (idx == -1) return text.length
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
                    // 在历史中间又动了光标：后面的“未来”作废。
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
        editorText.setSelection(offset.coerceIn(0, editorText.text?.length ?: 0))
        scrollToOffset(offset)
        suppressHistory = false
        editorText.requestFocus()
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

    private fun buildSymbolBar() {
        for (sym in SYMBOLS) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btn.text = sym
            btn.textSize = 16f
            btn.minimumWidth = dp(44)
            btn.setPadding(dp(4), 0, dp(4), 0)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(2), 0, dp(2), 0)
            btn.layoutParams = lp
            btn.setOnClickListener { insertSymbol(sym) }
            symbolRow.addView(btn)
        }
    }

    private fun insertSymbol(sym: String) {
        if (readOnly) return
        val insert = if (sym == "→") "\t" else sym
        val editable = editorText.text ?: return
        val start = editorText.selectionStart.coerceAtLeast(0)
        val end = editorText.selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, insert)
        editorText.setSelection(start + insert.length)
        editorText.requestFocus()
    }

    // ---------- 搜索 ----------

    private fun showSearch() {
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        runSearch()
    }

    private fun hideSearch() {
        searchBar.visibility = View.GONE
        searchMatches = emptyList()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun runSearch() {
        val kw = searchInput.text?.toString() ?: ""
        val text = editorText.text?.toString() ?: ""
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
            searchCount.text = if (kw.isEmpty()) "" else getString(R.string.no_match)
            return
        }
        searchIndex = 0
        selectSearchMatch()
    }

    private fun selectSearchMatch() {
        if (searchMatches.isEmpty()) return
        val m = searchMatches[searchIndex]
        searchCount.text = getString(R.string.search_match_count, searchIndex + 1, searchMatches.size)
        suppressHistory = true
        editorText.setSelection(m.first, m.last + 1)
        scrollToOffset(m.first)
        suppressHistory = false
    }

    private fun moveSearch(delta: Int) {
        if (searchMatches.isEmpty()) return
        searchIndex = (searchIndex + delta + searchMatches.size) % searchMatches.size
        selectSearchMatch()
    }

    // ---------- ⋮ 菜单 ----------

    private fun showEditorMenu() {
        val anchor = toolbar.findViewById<View>(R.id.menu_editor_more) ?: toolbar
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        menu.add(0, M_FILE, 0, R.string.file).setIcon(R.drawable.ic_file)
        menu.add(0, M_SEARCH, 1, R.string.editor_search).setIcon(R.drawable.ic_search)
        menu.add(0, M_SYNTAX, 2, R.string.syntax).setIcon(R.drawable.ic_code)
        menu.add(0, M_PREV, 3, R.string.prev_position).setIcon(R.drawable.ic_arrow_back)
            .isEnabled = historyIndex > 0
        menu.add(0, M_NEXT, 4, R.string.next_position).setIcon(R.drawable.ic_arrow_forward)
            .isEnabled = historyIndex >= 0 && historyIndex < cursorHistory.size - 1
        menu.add(0, M_GOTO, 5, R.string.go_to_line).setIcon(R.drawable.ic_arrow_down)
        menu.add(0, M_WRAP, 6, R.string.word_wrap).setIcon(R.drawable.ic_wrap)
            .setCheckable(true).isChecked = wrap
        menu.add(0, M_READONLY, 7, R.string.read_only).setIcon(R.drawable.ic_eye)
            .setCheckable(true).isChecked = readOnly
        menu.add(0, M_SMOOTH, 8, R.string.smooth_mode).setIcon(R.drawable.ic_smooth)
            .setCheckable(true).isChecked = smoothMode
        menu.add(0, M_COMPLETE, 9, R.string.code_completion).setIcon(R.drawable.ic_complete)
            .setCheckable(true).isChecked = completionEnabled
        menu.add(0, M_SETTINGS, 10, R.string.settings).setIcon(R.drawable.ic_drawer_settings)
        menu.add(0, M_CLOSE, 11, R.string.close_file).setIcon(R.drawable.ic_close)
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                M_FILE -> { showFileSubMenu(anchor); true }
                M_SEARCH -> { showSearch(); true }
                M_SYNTAX -> { showSyntaxSubMenu(anchor); true }
                M_PREV -> { goPrevPosition(); true }
                M_NEXT -> { goNextPosition(); true }
                M_GOTO -> { showGoToLine(); true }
                M_WRAP -> {
                    item.isChecked = !item.isChecked
                    wrap = item.isChecked
                    applyWrap()
                    savePrefs()
                    true
                }
                M_READONLY -> {
                    item.isChecked = !item.isChecked
                    setReadOnly(item.isChecked)
                    savePrefs()
                    true
                }
                M_SMOOTH -> {
                    item.isChecked = !item.isChecked
                    smoothMode = item.isChecked
                    savePrefs()
                    true
                }
                M_COMPLETE -> {
                    item.isChecked = !item.isChecked
                    completionEnabled = item.isChecked
                    if (!completionEnabled) completionPopup?.dismiss()
                    savePrefs()
                    true
                }
                M_SETTINGS -> { showEditorSettings(); true }
                M_CLOSE -> { closeFile(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showFileSubMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, M_NEW, 0, R.string.new_file).setIcon(R.drawable.ic_add)
        popup.menu.add(0, M_SAVE, 1, R.string.save).setIcon(R.drawable.ic_save)
        popup.menu.add(0, M_SAVE_AS, 2, R.string.save_as).setIcon(R.drawable.ic_file)
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                M_NEW -> { newFile(); true }
                M_SAVE -> { save(); true }
                M_SAVE_AS -> { saveAs(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showSyntaxSubMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
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
        val effective = effectiveSyntax()
        for ((i, pair) in langs.withIndex()) {
            val (id, label) = pair
            val item = popup.menu.add(0, 1200 + i, i, label)
            item.setIcon(if (id == "plain" || id.isEmpty()) R.drawable.ic_file else R.drawable.ic_code)
            item.setCheckable(true)
            item.isChecked = (id == syntaxChoice) || (syntaxChoice.isEmpty() && id.isEmpty())
            item.setOnMenuItemClickListener {
                syntaxChoice = id
                savePrefs()
                requestHighlight()
                true
            }
        }
        popup.setForceShowIcon(true)
        popup.show()
    }

    private fun showGoToLine() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.go_to_line_hint)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.go_to_line)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val line = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val target = line.coerceIn(1, lineCount())
                jumpToOffset(lineStartOffset(target))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditorSettings() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        val sizeLabel = TextView(this).apply {
            text = getString(R.string.font_size) + ": " + fontSize + "sp"
        }
        val seek = SeekBar(this).apply {
            max = 16 // 12..28sp
            progress = (fontSize - 12).coerceIn(0, 16)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                sizeLabel.text = getString(R.string.font_size) + ": " + size + "sp"
                editorText.textSize = size.toFloat()
                gutter.textSize = size.toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val linesCheck = CheckBox(this).apply {
            text = getString(R.string.line_numbers)
            isChecked = showLines
        }
        container.addView(sizeLabel)
        container.addView(seek)
        container.addView(linesCheck)
        val savedSize = fontSize
        val savedShowLines = showLines
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                fontSize = seek.progress + 12
                showLines = linesCheck.isChecked
                editorText.textSize = fontSize.toFloat()
                gutter.textSize = fontSize.toFloat()
                gutter.visibility = if (showLines) View.VISIBLE else View.GONE
                updateLineNumbers()
                savePrefs()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // 取消：恢复进对话框前的字号和行号。
                fontSize = savedSize
                showLines = savedShowLines
                editorText.textSize = fontSize.toFloat()
                gutter.textSize = fontSize.toFloat()
                gutter.visibility = if (showLines) View.VISIBLE else View.GONE
                updateLineNumbers()
            }
            .show()
    }

    private fun applyWrap() {
        editorText.setHorizontallyScrolling(!wrap)
        editorText.isHorizontalScrollBarEnabled = !wrap
    }

    private fun setReadOnly(on: Boolean) {
        readOnly = on
        if (on) {
            if (savedKeyListener == null) savedKeyListener = editorText.keyListener
            editorText.keyListener = null
            editorText.isCursorVisible = false
            editorText.isFocusable = false
            editorText.isFocusableInTouchMode = false
            completionPopup?.dismiss()
        } else {
            editorText.keyListener = savedKeyListener
            editorText.isCursorVisible = true
            editorText.isFocusable = true
            editorText.isFocusableInTouchMode = true
            editorText.requestFocus()
        }
        updateUndoRedo()
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
        val snapshot = editorText.text?.toString() ?: return
        if (snapshot.length > HIGHLIGHT_MAX_LEN) {
            applySpans(emptyList())
            return
        }
        val lang = effectiveSyntax()
        val kw = keywordColor
        val str = stringColor
        val cmt = commentColor
        val num = numberColor
        val r = Runnable {
            Thread {
                val spans = computeHighlights(snapshot, lang, kw, str, cmt, num)
                runOnUiThread {
                    if (editorText.text?.toString() != snapshot) return@runOnUiThread
                    applySpans(spans)
                }
            }.start()
        }
        highlightRunnable = r
        highlightHandler.postDelayed(r, HIGHLIGHT_DEBOUNCE_MS)
    }

    private fun applySpans(spans: List<Triple<Int, Int, Int>>) {
        val editable = editorText.text ?: return
        for (span in editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)) {
            editable.removeSpan(span)
        }
        for ((s, e, c) in spans) {
            if (s < e && e <= editable.length) {
                editable.setSpan(ForegroundColorSpan(c), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
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
        if (!completionEnabled || readOnly || suppressUndo || insertingCompletion || !editorText.hasFocus()) {
            completionPopup?.dismiss()
            return
        }
        val text = editorText.text?.toString() ?: return
        val sel = editorText.selectionStart
        if (sel <= 0) {
            completionPopup?.dismiss()
            return
        }
        var start = sel
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        val prefix = text.substring(start, sel)
        if (prefix.length < 2) {
            completionPopup?.dismiss()
            return
        }
        val candidates = text.split(Regex("[^A-Za-z0-9_]+"))
            .filter { it.length >= prefix.length && it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }
            .distinct()
            .sorted()
            .take(8)
        if (candidates.isEmpty()) {
            completionPopup?.dismiss()
            return
        }
        showCompletionPopup(candidates, start, sel)
    }

    private fun showCompletionPopup(candidates: List<String>, wordStart: Int, wordEnd: Int) {
        val layout = editorText.layout ?: return
        val line = layout.getLineForOffset(wordEnd.coerceIn(0, layout.text.length))
        val y = layout.getLineBottom(line) - editorText.scrollY - editorText.height
        val x = layout.getPrimaryHorizontal(wordEnd).toInt()
        val popup = completionPopup ?: ListPopupWindow(this).also { completionPopup = it }
        popup.anchorView = editorText
        popup.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, candidates))
        popup.width = ListPopupWindow.WRAP_CONTENT
        popup.height = ListPopupWindow.WRAP_CONTENT
        popup.verticalOffset = y
        popup.horizontalOffset = x
        popup.isModal = false
        popup.setOnItemClickListener { _, _, pos, _ ->
            val word = candidates[pos]
            insertingCompletion = true
            editorText.text?.replace(wordStart, wordEnd, word)
            editorText.setSelection(wordStart + word.length)
            insertingCompletion = false
            popup.dismiss()
        }
        if (!popup.isShowing) popup.show() else popup.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, candidates))
    }
}
