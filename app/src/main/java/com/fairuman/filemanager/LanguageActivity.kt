package com.fairuman.filemanager

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * 多语言设置页（简单版 1.3.0）：18 种语言列表。
 * 点语言只选中 RadioButton，不立即切换；点右上角 ✓ 确认才
 * AppCompatDelegate.setApplicationLocales 切换；直接按返回则不切换。
 * 语言切换用现代接口，不用已废弃的 resources.updateConfiguration。
 */
class LanguageActivity : BaseActivity() {

    data class Lang(val tag: String, val native: String, val sub: String)

    companion object {
        val LANGUAGES = listOf(
            Lang("zh-CN", "简体中文", "Chinese (Simplified)"),
            Lang("zh-TW", "繁體中文", "Chinese (Traditional)"),
            Lang("en", "English", "英语"),
            Lang("fr", "Français", "法语"),
            Lang("ja", "日本語", "日语"),
            Lang("ko", "한국어", "韩语"),
            Lang("ar", "العربية", "阿拉伯语 · RTL"),
            Lang("es", "Español", "西班牙语"),
            Lang("pt", "Português", "葡萄牙语"),
            Lang("th", "ไทย", "泰语"),
            Lang("eo", "Esperanto", "世界语"),
            Lang("de", "Deutsch", "德语"),
            Lang("ru", "Русский", "俄语"),
            Lang("mn", "Монгол", "蒙古语（西里尔）"),
            Lang("mn-Mong", "ᠮᠣᠩᠭᠣᠯ", "蒙古语（传统蒙古文）"),
            Lang("bo", "བོད་སྐད་", "藏语"),
            Lang("ug", "ئۇيغۇرچە", "维吾尔语 · RTL"),
            Lang("za", "Vahcuengh", "壮语")
        )

        /** 当前生效的语言 tag：优先用保存的设置，没有就按系统语言匹配。 */
        fun currentTag(ctx: android.content.Context): String {
            val sp = ctx.getSharedPreferences("fairu_prefs", MODE_PRIVATE)
            val saved = sp.getString(MainActivity.PREF_LOCALE, null)
            if (!saved.isNullOrBlank()) return saved
            val sys = Locale.getDefault()
            val sysTag = sys.toLanguageTag()
            LANGUAGES.firstOrNull { it.tag.equals(sysTag, ignoreCase = true) }?.let { return it.tag }
            LANGUAGES.firstOrNull { it.tag.substringBefore('-').equals(sys.language, ignoreCase = true) }?.let { return it.tag }
            return "zh-CN"
        }

        fun nativeName(tag: String): String =
            LANGUAGES.firstOrNull { it.tag == tag }?.native ?: tag
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: LangAdapter
    private var pendingTag: String = "zh-CN"

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashHandler.install(this)
        // 语言/主题/配色由 BaseActivity.onCreate 在 super.onCreate 之前统一应用。
        super.onCreate(savedInstanceState)
        try {
            onCreateInner()
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

    private fun onCreateInner() {
        setContentView(R.layout.activity_language)
        prefs = getSharedPreferences("fairu_prefs", MODE_PRIVATE)
        pendingTag = currentTag(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.languageToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.languageList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = LangAdapter(pendingTag) { lang ->
            // 只选中，不切换。
            pendingTag = lang.tag
            adapter.selectedTag = lang.tag
            adapter.notifyDataSetChanged()
        }
        list.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.language_menu, menu)
        // ✓ 图标自带 colorOnPrimary 白色着色；Miuix 下工具栏是浅色，须跟随标题色着色。
        menu.findItem(R.id.menu_confirm)?.icon?.let { d ->
            val c = MaterialColors.getColor(
                this, R.attr.fairuToolbarTitleColor, Color.WHITE
            )
            DrawableCompat.setTint(d.mutate(), c)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_confirm) {
            // 点了 ✓ 才真正切换语言。
            prefs.edit().putString(MainActivity.PREF_LOCALE, pendingTag).apply()
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.create(Locale.forLanguageTag(pendingTag))
            )
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class LangAdapter(var selectedTag: String, private val onPick: (Lang) -> Unit) :
        RecyclerView.Adapter<LangAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.langName)
            val sub: TextView = v.findViewById(R.id.langSub)
            val radio: RadioButton = v.findViewById(R.id.langRadio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = LANGUAGES.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val lang = LANGUAGES[position]
            holder.name.text = lang.native
            holder.sub.text = lang.sub
            holder.radio.isChecked = lang.tag == selectedTag
            holder.itemView.setOnClickListener { onPick(lang) }
        }
    }
}
