package com.radoslove.interslavic

import android.content.Context

/**
 * The emoji panel behind the 😀 key (owner 2026-10-25: in place of `,`).
 *
 * Ported from iOS 0.4.2 (`ios/Keyboard/Emoji.swift`): the SAME set in the SAME
 * order, because the order is muscle memory - the ones used lately first, then
 * the fixed common set. iOS has no choice but its own panel (a custom keyboard
 * cannot open the system emoji keyboard there); Android could hand over to
 * another input method instead, but that would leave the keyboard entirely and
 * the two phones would stop matching, so the panel is ours here too.
 *
 * The list is generated from the Swift file, never retyped: see
 * docs/PLATFORM_PARITY.md, "the layout tables are copied by hand".
 */
object Emoji {

    /** Most used first, grouped loosely: faces, hands and hearts, things. */
    val common: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😋", "😛", "😜", "🤪", "😝",
        "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑",
        "😶", "😏", "😒", "🙄", "😬", "😮‍💨", "😌", "😔",
        "😪", "😴", "😷", "🤒", "🤕", "🤢", "🤧", "🥵",
        "🥶", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐",
        "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺",
        "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
        "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
        "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻",
        "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👋",
        "👏", "🙌", "👐", "🤝", "🙏", "💪", "👀", "🫶",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "💔", "💕", "💖", "💯", "🔥", "✨", "⭐", "🎉",
        "✅", "❌", "⚠️", "❓", "❗", "💡", "📌", "⏰",
        "☀️", "🌙", "⛅", "🌧️", "❄️", "🌈", "🌍", "🌲",
        "🐶", "🐱", "🐻", "🦊", "🐺", "🐴", "🐝", "🦉",
        "☕", "🍺", "🍷", "🍕", "🍔", "🍎", "🍰", "🎂",
        "⚽", "🏃", "🚴", "🚗", "✈️", "🏠", "💻", "📱",
        "📚", "✏️", "💊", "🩺", "🎵", "🎸", "📷", "🎁",
    )

    private const val PREFS = "isv_collector"
    private const val KEY_RECENT = "emojiRecent"
    private const val RECENT_MAX = 16

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Used lately, newest first. Stays on the phone. */
    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, "").orEmpty()
            .split(" ").filter { it.isNotEmpty() }

    fun used(context: Context, e: String) {
        val next = (listOf(e) + recent(context).filter { it != e }).take(RECENT_MAX)
        prefs(context).edit().putString(KEY_RECENT, next.joinToString(" ")).apply()
    }

    /** What the panel shows: lately used first, then the rest of [common]. */
    fun panel(context: Context): List<String> {
        val r = recent(context)
        return r + common.filter { it !in r }
    }
}
