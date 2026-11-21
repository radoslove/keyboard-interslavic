import SwiftUI
import UIKit

/// The ✎ tab - Android `TryTab.kt` (3.11.13). A box to try the keyboard (it
/// tells "the keyboard is broken" from "it is not switched on"), and above it:
/// - every word of the box as a button: a tap puts it among the cards;
/// - one row of languages, the keyboard's and the ones the text can be
///   translated into, all alphabetical (Interslavic too, owner 261121): a tap
///   translates the text IN the box word by word (Wiktionary, offline; through
///   Polish between two other languages) and switches the keyboard to it;
/// - every alphabet (ABC АБВ ⰀⰁⰂ ᛆᛒᛍ ᚠᚢᚦ ·−): one the language has turns the
///   text in the box into it, one it has not switches to a language that has it.
/// With text selected, an alphabet turns only the selection (owner 261121:
/// "like shift for capitals").
struct TryView: View {
    var active = true
    @State private var text = ""
    @State private var selection = NSRange(location: 0, length: 0)
    @State private var focused = false
    @State private var lang = AppLangs.current
    @State private var known: Set<String> = []
    @State private var toast: String?
    @State private var busy = false
    @Environment(\.scenePhase) private var phase

    /// The last translation, to turn it back exactly instead of translating
    /// a translation (Android `TryTurn`).
    private struct Turn { let from: String; let before: String; let to: String; let after: String; let script: String }
    @State private var turn: Turn?
    /// Words of the last translation (lowercase) -> the word they came from,
    /// so a card made from the box gets its translation.
    @State private var pairs: [String: String] = [:]
    @State private var pairsLang: String?

    // MARK: alphabets

    private static let six: [Scripts.Choice] = [.script(.latin), .script(.cyrillic), .script(.glagolitic),
                                                .runes(.medieval), .runes(.young), .morse]

    private static func raw(_ c: Scripts.Choice) -> String {
        switch c {
        case .script(let m): return m.rawValue
        case .runes(let r): return r.rawValue
        case .morse: return "morse"
        }
    }

    /// The keyboard's store key of [l]'s script (`Keyboard/Script.swift`).
    private static func key(_ l: String) -> String { l == "isv" ? "script" : "script_" + l }

    private static func chosen(_ l: String) -> Scripts.Choice {
        let all = Scripts.choices(l)
        let r = Shared.store.string(forKey: key(l)) ?? ""
        return all.first { raw($0) == r } ?? all[0]
    }

    private static func setChosen(_ l: String, _ c: Scripts.Choice) {
        Shared.store.set(raw(c), forKey: key(l))
    }

    private static func native(_ l: String) -> Script.Mode { Script.offered(l)[0] }

    /// Text written in [c] -> [l]'s own alphabet. Alphabets are read back all
    /// at once, so a box with a word in each still comes out whole.
    private static func toNative(_ s: String, _ c: Scripts.Choice, _ l: String) -> String {
        Layout.language = l
        switch c {
        case .morse: return Morse.decode(s, l)
        case .runes(let r): return Runes.decodeLossy(s, r)
        case .script(let m):
            if native(l) == .latin {
                return Script.back(Script.back(s, .glagolitic), .cyrillic)
            }
            if m == native(l) { return s }
            return Script.convert(Script.back(s, .glagolitic), native(l))
        }
    }

    /// [l]'s own alphabet -> [c].
    private static func fromNative(_ s: String, _ c: Scripts.Choice, _ l: String) -> String {
        Layout.language = l
        switch c {
        case .morse: return Morse.encode(s, l)
        case .runes(let r): return Runes.encode(s, r)
        case .script(let m):
            if m == native(l) { return s }
            let latin = native(l) == .latin ? s : Script.back(s, native(l))
            return Script.convert(latin, m)
        }
    }

    /// The alphabet a selection is written in, from its letters.
    private static func detect(_ s: String, _ l: String) -> Scripts.Choice {
        let sc = s.unicodeScalars
        if Morse.has(l), sc.contains(where: { $0 == "·" || $0 == "−" }),
           sc.allSatisfy({ $0 == "·" || $0 == "−" || $0 == "/" || CharacterSet.whitespacesAndNewlines.contains($0) }) {
            return .morse
        }
        if sc.contains(where: { (0x16A0...0x16FF).contains($0.value) }) {
            if case .runes(let r) = chosen(l) { return .runes(r) }
            return .runes(.medieval)
        }
        if sc.contains(where: { (0x2C00...0x2C5F).contains($0.value) }) { return .script(.glagolitic) }
        if sc.contains(where: { (0x0400...0x04FF).contains($0.value) }) { return .script(.cyrillic) }
        return .script(.latin)
    }

    private func tapScript(_ c: Scripts.Choice) {
        let own = Scripts.choices(lang)
        guard own.contains(c) else { otherScript(c); return }
        let ns = text as NSString
        if selection.length > 0, selection.upperBound <= ns.length {
            // Only the selection, from whatever alphabet it is in.
            let part = ns.substring(with: selection)
            let out = Self.fromNative(Self.toNative(part, Self.detect(part, lang), lang), c, lang)
            text = ns.replacingCharacters(in: selection, with: out)
            selection = NSRange(location: selection.location, length: (out as NSString).length)
            Self.setChosen(lang, c)
            turn = nil
            return
        }
        let old = Self.chosen(lang)
        // The alphabet already on, tapped again: back to the language's own.
        let to = c == old ? own[0] : c
        guard to != old else { return }
        text = Self.fromNative(Self.toNative(text, old, lang), to, lang)
        selection = NSRange(location: (text as NSString).length, length: 0)
        Self.setChosen(lang, to)
        turn = nil
    }

    /// Alphabet [m], which the language is not written in: to a language that
    /// has it - Interslavic first among the keyboard's, one where it is an
    /// added alphabet, one the text can be translated into (Android `otherScript`).
    private func otherScript(_ m: Scripts.Choice) {
        let has = AppLangs.codes.filter { $0 != lang && Scripts.choices($0).contains(m) }
        guard !has.isEmpty else { return }
        let on = Gloss.enabled.sorted(by: Self.isvFirst)
        let reach = Self.reach(lang)
        func added(_ l: String) -> Bool { Scripts.choices(l)[0] != m }
        let to = on.first { has.contains($0) && added($0) && reach.contains($0) }
            ?? on.first { has.contains($0) && added($0) }
            ?? on.first { has.contains($0) }
            ?? (has.contains("isv") ? "isv" : has.sorted()[0])
        Self.setChosen(to, m)
        go(to)
    }

    // MARK: languages and translation

    /// The languages [from]'s text can be translated into: Polish into the 19
    /// of its Wiktionary, the others into Polish and through it into the rest.
    /// None for a language without a gloss (Interslavic).
    private static func reach(_ from: String) -> [String] {
        if from == "pl" { return Gloss.targets("pl", enabled: Gloss.enabled) }
        guard Gloss.has(from) else { return [] }
        return (Gloss.targets("pl", enabled: Gloss.enabled) + ["pl"]).filter { $0 != from }
    }

    /// Interslavic first, then alphabetical.
    private static func isvFirst(_ a: String, _ b: String) -> Bool {
        if a == "isv" || b == "isv" { return a == "isv" && b != "isv" }
        return a < b
    }

    private var languages: [String] {
        var seen = Set<String>()
        // Alphabetical, Interslavic among the rest (owner 261121).
        return (Gloss.enabled + Self.reach(lang)).filter { seen.insert($0).inserted }.sorted()
    }

    /// The gloss files write Serbian in Cyrillic, every other language in its own alphabet.
    private static func toGloss(_ s: String, _ l: String) -> String {
        let n = toNative(s, chosen(l), l)
        return l == "sr" ? Scripts.convert(n, .cyrillic, "sr") : n
    }

    private static func fromGloss(_ s: String, _ l: String) -> String {
        Layout.language = l
        let n = l == "sr" ? Script.back(s, .cyrillic) : s
        return fromNative(n, chosen(l), l)
    }

    /// Switch the box and the keyboard to [l], translating the text.
    private func go(_ l: String) {
        let from = lang
        guard l != from, !busy else { return }
        let typed = text
        if !Gloss.enabled.contains(l) {
            Shared.store.set(Gloss.enabled + [l], forKey: "languages")
            show("⌨ ＋ \(l.uppercased())")
        }
        Shared.store.set(l, forKey: "language")
        let fromScript = Self.raw(Self.chosen(from))
        func done(_ out: String) {
            turn = typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil
                : Turn(from: from, before: typed, to: l, after: out, script: fromScript)
            lang = l
            text = out
            selection = NSRange(location: (out as NSString).length, length: 0)
            reloadKnown()
        }
        // Back to the language just translated from, text untouched since:
        // the original comes back, in the alphabet chosen for it now.
        if let t = turn, t.to == from, t.from == l, t.after == typed {
            let then = Scripts.choices(l).first { Self.raw($0) == t.script } ?? Self.chosen(l)
            done(Self.fromNative(Self.toNative(t.before, then, l), Self.chosen(l), l))
            return
        }
        if typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { done(""); return }
        guard Self.reach(from).contains(l) else {
            // No gloss (Interslavic): the text stays, in the new language's alphabet.
            pairs = [:]; pairsLang = nil
            Layout.language = from
            let n = Self.toNative(typed, Self.chosen(from), from)
            let latin = Self.native(from) == .latin ? n : Script.back(n, Self.native(from))
            Layout.language = l
            let nl = Self.native(l) == .latin ? latin : Script.convert(latin, Self.native(l))
            done(Self.fromNative(nl, Self.chosen(l), l))
            return
        }
        let src = Self.toGloss(typed, from)
        busy = true
        DispatchQueue.global(qos: .userInitiated).async {
            let (joined, found) = Self.translate(src, from: from, to: l)
            DispatchQueue.main.async {
                busy = false
                pairs = found; pairsLang = l
                done(Self.fromGloss(joined, l))
            }
        }
    }

    /// [src] (gloss form of [from]) word by word into [to]; words with no
    /// gloss stay as typed. Off the main thread: the tables are large.
    private static func translate(_ src: String, from: String, to: String) -> (String, [String: String]) {
        let direct = from == "pl" || to == "pl"
        let tFrom = Gloss.table(from)
        let tPl = direct ? tFrom : Gloss.table("pl")
        func hasLetter(_ s: String) -> Bool { s.contains { $0.isLetter } }
        let pieces: [(String, Bool)] = direct ? Gloss.pieces(tFrom, src, from: from, to: to)
            : Gloss.pieces(tFrom, src, from: from, to: "pl").flatMap { p, ok -> [(String, Bool)] in
                guard ok, hasLetter(p) else { return [(p, ok)] }
                let ps = Gloss.pieces(tPl, p, from: "pl", to: to)
                // A Polish word with no gloss in [to] would stay Polish: the typed word is honester.
                return ps.contains { !$0.1 && hasLetter($0.0) } ? [(p, false)] : ps
            }
        var found: [String: String] = [:]
        for w in src.components(separatedBy: CharacterSet.letters.inverted) where !w.isEmpty {
            let g: String?
            if direct { g = Gloss.word(tFrom, w, from: from, to: to) }
            else if let pl = Gloss.word(tFrom, w, from: from, to: "pl") {
                let ps = Gloss.pieces(tPl, pl, from: "pl", to: to)
                g = ps.contains { !$0.1 && hasLetter($0.0) } ? nil : ps.map(\.0).joined()
            } else { g = nil }
            guard let g else { continue }
            let typed = from == "de" ? w : w.lowercased()
            if found[g.lowercased()] == nil { found[g.lowercased()] = typed }
        }
        return (pieces.map(\.0).joined(), found)
    }

    // MARK: words to learn

    /// The last 12 distinct words of the box, longer than one letter, in the
    /// language's own alphabet.
    private var words: [String] {
        let plain = Self.toNative(text, Self.chosen(lang), lang)
        var seen = Set<String>(), out: [String] = []
        for w in plain.components(separatedBy: CharacterSet.letters.inverted)
        where w.count > 1 && seen.insert(w.lowercased()).inserted { out.append(w) }
        return Array(out.suffix(12))
    }

    private func reloadKnown() {
        known = Set(Learn.cards(order: AppLangs.codes).map { "\($0.lang)\t\($0.word.lowercased())" })
    }

    private func inDeck(_ w: String) -> Bool { known.contains("\(lang)\t\(w.lowercased())") }

    private func show(_ s: String) {
        toast = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { if toast == s { toast = nil } }
    }

    /// The keyboard may have switched language while the app was away.
    private func follow() {
        let now = AppLangs.current
        if now != lang { lang = now; turn = nil }
        reloadKnown()
        // The gloss tables load once, in the background, before the first tap.
        let l = lang
        DispatchQueue.global(qos: .utility).async { if Gloss.has(l) { _ = Gloss.table(l) }; _ = Gloss.table("pl") }
    }

    // MARK: view

    private func chip(_ label: String, on: Bool, shade: Double, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.body.weight(on ? .bold : .regular))
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(on ? Color.accentColor.opacity(0.25) : Color(.systemGray4).opacity(0.6),
                            in: Capsule())
                .opacity(shade)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var learnRow: some View {
        if !words.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text(Ui.t("try_learn")).font(.footnote).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(words, id: \.self) { w in
                            let has = inDeck(w)
                            Button(has ? "✓ \(w)" : w) {
                                // German nouns keep their capital, everything else is stored lowercase.
                                let word = lang == "de" ? w : w.lowercased()
                                // A word the box was translated into carries the word it came from.
                                let tr = pairsLang == lang ? pairs[word.lowercased()] ?? "" : ""
                                Learn.add(lang, word, tr: tr)
                                reloadKnown()
                                show("\(Ui.t("learn_added")): \(word)")
                            }
                            .buttonStyle(.bordered)
                            .disabled(has)
                            .opacity(has ? 0.5 : 1)
                        }
                    }
                }
            }
        }
    }

    private var languageRow: some View {
        HStack {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(languages, id: \.self) { l in
                            chip(l.uppercased(), on: l == lang,
                                 shade: l == lang ? 1 : Gloss.enabled.contains(l) ? 0.75 : 0.5) { go(l) }
                                .id(l)
                        }
                    }
                }
                .onAppear { proxy.scrollTo(lang, anchor: .center) }
                .onChange(of: lang) { l in withAnimation { proxy.scrollTo(l, anchor: .center) } }
            }
            Button("⧉") {
                UIPasteboard.general.string = text
                show("✓ \(lang.uppercased())")
            }
            .buttonStyle(.bordered)
            .disabled(text.isEmpty)
        }
    }

    private var scriptRow: some View {
        let own = Scripts.choices(lang)
        let on = Self.chosen(lang)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Self.six, id: \.self) { c in
                    chip(c.name, on: c == on, shade: c == on ? 1 : own.contains(c) ? 0.75 : 0.45) { tapScript(c) }
                }
            }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    learnRow
                    languageRow
                    scriptRow
                    // Only while ✎ is shown: a UIKit view keeps taking touches
                    // under `opacity(0)` / `allowsHitTesting(false)` (the 🎓 wheels
                    // blocked ✎ in 1.0 (2); tester review 261121). The text lives
                    // in this view's state, so nothing is lost.
                    Group {
                        if active {
                            BoxView(text: $text, selection: $selection, focused: $focused,
                                    placeholder: Ui.t("try_hint"))
                        } else {
                            Color.clear
                        }
                    }
                        .frame(minHeight: 120, alignment: .topLeading)
                        .padding(.trailing, text.isEmpty ? 0 : 34)
                        .overlay(alignment: .topTrailing) {
                            if busy { ProgressView() }
                            else if !text.isEmpty {
                                // Clear the box in one tap (owner 261121).
                                Button {
                                    text = ""; turn = nil; pairs = [:]; pairsLang = nil
                                    selection = NSRange(location: 0, length: 0)
                                } label: {
                                    Image(systemName: "xmark.circle.fill").font(.title2)
                                        .foregroundStyle(.secondary)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("✕")
                            }
                        }
                    // Attribution the licence asks for, under the box the translation lands in.
                    if !Self.reach(lang).isEmpty {
                        Text("Wiktionary · CC BY-SA 4.0").font(.caption2).foregroundStyle(.secondary)
                    }
                } header: {
                    Text(Ui.head("try_head"))
                }
            }
            .brandTitle(Ui.t("tab_write"))
            .overlay(alignment: .bottom) {
                if let t = toast {
                    Text(t).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 12)
                }
            }
            .onAppear { follow() }
            .onChange(of: active) { if $0 { follow() } }
            .onChange(of: phase) { if $0 == .active { follow() } }
            .onChange(of: text) { _ in
                // A language switched on the keyboard while typing here: the
                // rows follow (the text is not translated behind the user's back).
                if AppLangs.current != lang, !busy { lang = AppLangs.current; turn = nil }
            }
        }
    }
}

/// A text box that reports its selection (SwiftUI's `TextField` does not on
/// iOS 16), so an alphabet can turn just the selected words.
private struct BoxView: UIViewRepresentable {
    @Binding var text: String
    @Binding var selection: NSRange
    @Binding var focused: Bool
    let placeholder: String

    func makeUIView(context: Context) -> UITextView {
        let v = UITextView()
        v.font = .preferredFont(forTextStyle: .title3)
        v.adjustsFontForContentSizeCategory = true
        v.backgroundColor = .clear
        v.isScrollEnabled = false
        v.autocorrectionType = .no
        v.autocapitalizationType = .sentences
        v.textContainerInset = .zero
        v.textContainer.lineFragmentPadding = 0
        v.delegate = context.coordinator
        v.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        let p = UILabel()
        p.text = placeholder
        p.font = v.font
        p.adjustsFontForContentSizeCategory = true
        p.textColor = .placeholderText
        p.numberOfLines = 0
        p.translatesAutoresizingMaskIntoConstraints = false
        v.addSubview(p)
        NSLayoutConstraint.activate([
            p.topAnchor.constraint(equalTo: v.topAnchor),
            p.leadingAnchor.constraint(equalTo: v.leadingAnchor),
            p.widthAnchor.constraint(equalTo: v.widthAnchor),
        ])
        context.coordinator.placeholder = p
        p.isHidden = !text.isEmpty
        return v
    }

    func updateUIView(_ v: UITextView, context: Context) {
        let c = context.coordinator
        c.parent = self
        c.applying = true
        if v.text != text { v.text = text }
        let len = (v.text as NSString).length
        if selection.upperBound <= len, v.selectedRange != selection { v.selectedRange = selection }
        c.placeholder?.isHidden = !text.isEmpty
        c.applying = false
    }

    @available(iOS 16.0, *)
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let w = proposal.width ?? 320
        let fit = uiView.sizeThatFits(CGSize(width: w, height: .greatestFiniteMagnitude))
        return CGSize(width: w, height: max(fit.height, (uiView.font?.lineHeight ?? 24) * 4))
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: BoxView
        var applying = false
        weak var placeholder: UILabel?
        init(_ p: BoxView) { parent = p }

        func textViewDidChange(_ v: UITextView) {
            parent.text = v.text
            parent.selection = v.selectedRange
            placeholder?.isHidden = !v.text.isEmpty
        }
        func textViewDidChangeSelection(_ v: UITextView) {
            guard !applying else { return }
            let r = v.selectedRange
            DispatchQueue.main.async { if self.parent.selection != r { self.parent.selection = r } }
        }
        func textViewDidBeginEditing(_ v: UITextView) { parent.focused = true }
        func textViewDidEndEditing(_ v: UITextView) { parent.focused = false }
    }
}
