package com.focuslamp.app.ui.coach

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

/** Just enough formatting for coach replies: **bold** and "- " / "* " bullets. */
object MarkdownLite {
    fun render(text: String): CharSequence {
        val out = SpannableStringBuilder()
        text.lines().forEachIndexed { i, raw ->
            if (i > 0) out.append('\n')
            var line = raw.trimEnd()
            val bullet = Regex("^\\s*[-*•]\\s+").find(line)
            if (bullet != null) {
                out.append("•  ")
                line = line.substring(bullet.range.last + 1)
            }
            line = line.replace(Regex("^#{1,6}\\s+"), "")
            var idx = 0
            val bold = Regex("\\*\\*(.+?)\\*\\*")
            for (m in bold.findAll(line)) {
                out.append(line, idx, m.range.first)
                val start = out.length
                out.append(m.groupValues[1])
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                idx = m.range.last + 1
            }
            out.append(line, idx, line.length)
        }
        return out
    }
}
