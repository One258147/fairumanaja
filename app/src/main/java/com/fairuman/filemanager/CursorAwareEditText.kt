package com.fairuman.filemanager

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 能报告光标位置变化的 EditText：文本编辑器靠它更新右上角的“行:列”、
 * 行号高亮、光标历史和代码补全（普通 EditText 没有光标变化回调）。
 */
class CursorAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var onSelectionChanged: ((selStart: Int, selEnd: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }
}
