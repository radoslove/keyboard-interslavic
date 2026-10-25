import Foundation

/// The emoji panel behind the 😀 key (owner 2026-10-25: in place of `,`).
///
/// A custom keyboard cannot open the system emoji keyboard on iOS - there is
/// no API for it, only 🌐 to leave our keyboard altogether - so the panel is
/// ours: the ones used lately first, then a fixed set of the common ones.
enum Emoji {

    /// Most used first, grouped loosely: faces, hands and hearts, things.
    static let common: [String] = [
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
    ]

    private static let recentKey = "emojiRecent"
    private static let recentMax = 16

    /// Used lately, newest first; with Full Access shared by all three keyboards.
    static var recent: [String] {
        Shared.store.stringArray(forKey: recentKey) ?? []
    }

    static func used(_ e: String) {
        var r = recent.filter { $0 != e }
        r.insert(e, at: 0)
        Shared.store.set(Array(r.prefix(recentMax)), forKey: recentKey)
    }
}
