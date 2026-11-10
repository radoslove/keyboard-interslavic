import UIKit

/// Interslavic keyboard extension.
///
/// Deliberately plain UIKit: a keyboard extension runs under a hard memory cap,
/// and every framework pulled in here is memory the keys have to share.
///
/// Not implemented yet, in rough order of how much they are missed:
///   - numeric layer switching is wired but the layer is minimal
///   - no key-press sound (a keyboard without full access may not play one)
///
/// Swiping lives in `SwipeInput` / `SwipeDecoder`; this class only lends it the
/// key geometry and puts the chosen word into the document.
final class KeyboardViewController: UIInputViewController {

    private enum ShiftState { case off, on, locked }

    private var shift: ShiftState = .on {        // sentence start
        didSet { if shift == .off { shiftByHand = false } }
    }
    /// Shift was pressed, not armed by the text. The automatic re-arm must
    /// not take it back: a press mid-sentence was undone by the next
    /// `textDidChange`, so the second press (meant for Caps Lock) only turned
    /// it on again (owner 2026-10-25: "after the next one it goes back to small").
    private var shiftByHand = false
    private var showingNumeric = false
    /// The `#+=` page of the numeric layer.
    private var showingSymbols = false
    /// The emoji panel behind 😀, in place of the letter rows.
    private var showingEmoji = false
    /// The settings panel in place of the letters (hold `123` -> ⚙).
    private var showingSettings = false
    private var rowsStack: UIStackView!
    private var letterButtons: [UIButton] = []
    private var popup: UIView?
    private var popupStrip: UIStackView?
    /// The strip shows the variants mirrored (first one on the right).
    private var popupOpensLeft = false
    private var variantLabels: [UILabel] = []
    private var selected = 0

    private var swipeInput: SwipeInput!
    private var suggestionBar: UIStackView!
    private var suggestionButtons: [UIButton] = []
    /// What the last swipe put in, so tapping another suggestion knows how much
    /// to take back out.
    private var lastSwipedWord: String?
    /// When that word went in. A backspace inside `wipeWindow` means "wrong
    /// word" and takes it whole; later it deletes one letter, because in
    /// Interslavic the stem is usually right and only the ending needs fixing.
    /// Mirrors the Android keyboard (SWIPE_WIPE_WINDOW_MS).
    private var swipeCommittedAt = Date.distantPast
    private static let wipeWindow: TimeInterval = 1.2
    /// Smart space: a swiped word gets its space BEFORE it, never after, and
    /// owes one to whatever comes next. A trailing space doubled up with the
    /// space bar habit (`Kako  se`) and sat between a word and its punctuation.
    private var pendingSpace = false
    /// The candidates of the last swipe, kept so a rejected word can be
    /// replaced by tapping the next one.
    private var lastCandidates: [String] = []
    /// The partly typed word the bar is completing; nil when the bar shows
    /// swipe candidates instead.
    private var completingPrefix: String?
    private var backspaceRepeat: Timer?
    /// " " when a swiped word went in right before more text and brought its
    /// own space after it (`byh dobra |možlivost`); empty otherwise.
    private var swipeSuffix = ""
    /// Between the finger lifting and the word landing. Keys pressed then are
    /// held back: a quick space used to land BEFORE the word and look missed.
    private var decoding = 0
    private var queuedKeys: [() -> Void] = []
    /// The word a backspace press has just wiped; if the press is held, it
    /// comes back one letter short and keeps trimming.
    private var trimAfterWipe: String?
    private var wipedSuffix = ""
    /// No space after these: `(jedino`, `„dobro`.
    private static let openers: Set<Character> = ["(", "[", "{", "„", "‚", "«", "‹", "¿", "¡", "\n"]

    private var insideWipeWindow: Bool {
        Date().timeIntervalSince(swipeCommittedAt) <= Self.wipeWindow
    }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        // Before anything is read: with Full Access the words and settings
        // live in the group the app shares; without it, in this extension.
        Shared.fullAccess = hasFullAccess
        Shared.adoptLocalWords(Layout.language)
        Shared.adoptLocalLift()
        applyTheme()
        view.backgroundColor = UIColor.secondarySystemBackground
        buildSuggestionBar()
        buildKeyboard()
        swipeInput = SwipeInput(host: self)
        swipeInput.attach()
        swipeLog.notice("isv-swipe: keyboard loaded, swipe build")
    }

    // Shift starts armed at load, and the extension is reloaded every time
    // the user comes back from another app - so returning from the dictionary
    // to the middle of a sentence typed the next word with a capital
    // (`byh Dobra`). Ask the text instead, on appearing and on every cursor move.
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        applyTheme()
        lastSwipedWord = nil
        pendingSpace = false
        rearmShiftIfSentenceStart()
    }

    /// The theme picked in the app (Android 3.5.27): auto follows the phone.
    private func applyTheme() {
        switch Shared.theme {
        case .auto: view.overrideUserInterfaceStyle = .unspecified
        case .light: view.overrideUserInterfaceStyle = .light
        case .dark: view.overrideUserInterfaceStyle = .dark
        }
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        rearmShiftIfSentenceStart()
    }

    // MARK: - Building

    private func buildKeyboard() {
        rowsStack?.removeFromSuperview()
        letterButtons.removeAll()

        let rows = showingNumeric
            ? (showingSymbols ? Layout.symbolRows : Layout.numericRows)
            : Layout.letterRows
        var rowViews: [UIView] = showingSettings
            ? [settingsPanel()]
            : showingEmoji
            ? [emojiPanel()]
            : rows.enumerated().map { characterRow($1, row: $0) }
        topLetterRowView = (showingNumeric || showingEmoji || showingSettings) ? nil : rowViews.first
        // No digit row above the letters (tried in 0.1.7): the host does not
        // let the keyboard grow, so five rows squeezed every key. Digits live
        // on `123` only.
        rowViews.append(bottomRow())

        let stack = UIStackView(arrangedSubviews: rowViews)
        stack.axis = .vertical
        stack.distribution = .fillEqually
        stack.spacing = 6
        if showingEmoji || showingSettings, let bottom = rowViews.last {
            // The panel takes the three letter rows, the bottom row stays one.
            stack.distribution = .fill
            bottom.heightAnchor.constraint(equalTo: stack.heightAnchor, multiplier: 0.25,
                                           constant: -4.5).isActive = true
        }
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        rowsStack = stack

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 3),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -3),
            stack.topAnchor.constraint(equalTo: suggestionBar.bottomAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                                          constant: -4),
            view.heightAnchor.constraint(
                greaterThanOrEqualToConstant: 216 + Self.suggestionBarHeight),
        ])
    }

    private func characterRow(_ chars: [Character], row: Int) -> UIView {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.spacing = 5

        // The third letter row carries shift and backspace on either side.
        let isLastLetterRow = !showingNumeric && chars == Layout.letterRows[2]
        // The numeric layer had no backspace at all; its last row gets one too.
        let isLastRow = row == 2
        // Shift and backspace are 1.5 keys wide here, as on the system
        // keyboard: 7 letters + 2 x 1.5 = the 10 keys of the top row, so the
        // letters keep the same width (swipe geometry depends on it). The
        // period moved to the bottom row - next to backspace it stole presses.
        var first: UIButton?
        var wide: [UIButton] = []
        if isLastLetterRow {
            stack.distribution = .fill
            let shiftKey = functionKey(shiftTitle, action: #selector(tapShift))
            if shift == .locked {
                // Caps Lock must look different from a one-shot shift, as on
                // the system keyboard: a light key with a blue arrow.
                shiftKey.backgroundColor = .systemBackground
                shiftKey.setTitleColor(.systemBlue, for: .normal)
            }
            stack.addArrangedSubview(shiftKey)
            wide.append(shiftKey)
        }
        // A script with fewer letters keeps the LATIN key width: Cyrillic and
        // Glagolitic have no `x` (`q` `w` are `lj` `nj` there), and instead of
        // six keys stretching over the row, it gets an empty cell at each end (row 3 grows
        // shift and backspace for the same reason, just above). The stack is
        // `fillEqually`, so an empty cell is exactly one key wide, and the
        // columns of the alphabet stay where the decoder expects them - the
        // learned touch offset and the pivot cap are in key widths, so a
        // changed width would silently miscalibrate both (Android
        // `KeyboardView.spacer`, measured in `docs/script-tables.md`).
        let pad = showingNumeric || isLastLetterRow || row >= Layout.latinLetterRows.count
            ? 0
            : (Layout.latinLetterRows[row].count - chars.count) / 2
        for _ in 0..<pad { stack.addArrangedSubview(UIView()) }
        for ch in chars {
            let b = characterKey(ch)
            b.tag = row                      // which row a key sits in decides
            letterButtons.append(b)          // where its popup can go
            stack.addArrangedSubview(b)
            if isLastRow {
                if let f = first { b.widthAnchor.constraint(equalTo: f.widthAnchor).isActive = true }
                else { first = b }
            }
        }
        for _ in 0..<pad { stack.addArrangedSubview(UIView()) }
        if isLastRow && showingNumeric {
            let page = functionKey(showingSymbols ? "123" : "#+=", action: #selector(tapSymbols))
            stack.insertArrangedSubview(page, at: 0)
            wide.append(page)
        }
        if isLastRow {
            stack.distribution = .fill
            let back = styledKey("⌫")
            back.backgroundColor = .tertiarySystemFill
            back.addTarget(self, action: #selector(backspaceDown), for: .touchDown)
            back.addTarget(self, action: #selector(backspaceUp),
                           for: [.touchUpInside, .touchUpOutside, .touchCancel])
            back.addTarget(self, action: #selector(keyDown(_:)), for: .touchDown)
            back.addTarget(self, action: #selector(keyUp(_:)),
                           for: [.touchUpInside, .touchUpOutside, .touchCancel])
            stack.addArrangedSubview(back)
            wide.append(back)
            if let f = first {
                // Shift + letters + backspace = the width of the top row, so
                // every letter keeps the same width (swipe geometry depends on
                // it): 1.5 keys each beside 7 letters under 10, 2 under the
                // Norwegian 11.
                let side = showingNumeric ? 1.5
                    : CGFloat(Layout.rowUnits - chars.count) / 2
                for w in wide {
                    w.widthAnchor.constraint(equalTo: f.widthAnchor, multiplier: side,
                                             constant: 2.5).isActive = true
                }
            }
        }
        return stack
    }

    private func bottomRow() -> UIView {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fill
        stack.spacing = 5

        // Widths as shares of the row, the weights of the Android row
        // (KeyboardView.kt), so both phones put each key in the same place.
        var weights: [(UIView, CGFloat)] = []
        let isv = Layout.language == "isv"

        // The emoji panel has the bottom row of `123`: ABC back, space, and a
        // backspace, since the letter row that holds one is gone.
        let panel = showingNumeric || showingEmoji || showingSettings
        let layerKey = functionKey(panel ? "ABC" : "123",
                                   action: showingSettings ? #selector(closeSettings)
                                       : showingEmoji ? #selector(tapEmojiKey) : #selector(tapLayer))
        if !panel {
            // Hold `123` for ⚙: the keyboard's own settings, drawn in place of
            // the letters (owner 2026-11-07). Android holds 🌐 for the same
            // strip; on iOS 🌐 belongs to the system, and a keyboard may not
            // open other apps (App Review 4.4.1), so nothing leaves the keyboard.
            layerKey.tag = 3        // bottom row: the strip goes above the key
            let hold = UILongPressGestureRecognizer(target: self, action: #selector(holdLayer(_:)))
            hold.minimumPressDuration = 0.3
            layerKey.addGestureRecognizer(hold)
        }
        stack.addArrangedSubview(layerKey)
        weights.append((layerKey, panel ? 1.6 : 1.2))

        // Apple requires a way off our keyboard when the system offers one.
        if needsInputModeSwitchKey {
            let globe = functionKey("🌐", action: #selector(tapNextKeyboard))
            stack.addArrangedSubview(globe)
            weights.append((globe, panel ? 1.2 : 0.95))
        }

        var spaceWeight: CGFloat = showingNumeric ? 5.6 : (showingEmoji || showingSettings) ? 4.2
            : (isv ? 3.45 : 4.65)
        if !panel {
            // Script key: as 🌐 switches the keyboard, this switches the
            // alphabet, Latin -> Cyrillic -> Glagolitic, and names the current
            // one (ABC / АБВ / ⰀⰁⰂ). Interslavic only.
            if isv {
                let script = functionKey(Script.keyLabel, action: #selector(tapScript))
                script.titleLabel?.adjustsFontSizeToFitWidth = true
                script.titleLabel?.minimumScaleFactor = 0.5
                stack.addArrangedSubview(script)
                weights.append((script, 1.2))
            }
            // 😀 left of space where `,` was (owner 2026-10-25); the comma is
            // now the FIRST item held on `.`, so it is still one gesture away.
            // `?` and `!` live on `123` next to backspace.
            let emoji = functionKey("😀", action: #selector(tapEmojiKey))
            stack.addArrangedSubview(emoji)
            weights.append((emoji, 0.9))
        }
        if !needsInputModeSwitchKey { spaceWeight += panel ? 1.2 : 0.95 }

        // Space fires on touch DOWN, like backspace: on lift, a thumb that
        // slid a little lost the press ("space sometimes takes two taps").
        // The space bar names the keyboard that is on (owner 2026-11-07), the
        // way the system keyboard does: its display name from Settings ->
        // Keyboards, lowercase (`medžuslovjansky`, `polski`, `češsky`).
        let space = styledKey(Self.spaceLabel)
        space.titleLabel?.font = .systemFont(ofSize: 15)
        space.setTitleColor(.secondaryLabel, for: .normal)
        space.titleLabel?.lineBreakMode = .byTruncatingTail
        space.backgroundColor = .systemBackground
        space.addTarget(self, action: #selector(tapSpace), for: .touchDown)
        space.addTarget(self, action: #selector(keyDown(_:)), for: .touchDown)
        space.addTarget(self, action: #selector(keyUp(_:)),
                        for: [.touchUpInside, .touchUpOutside, .touchCancel])
        stack.addArrangedSubview(space)
        bottomRowView = stack

        if showingEmoji || showingSettings {
            let back = styledKey("⌫")
            back.backgroundColor = .tertiarySystemFill
            back.addTarget(self, action: #selector(backspaceDown), for: .touchDown)
            back.addTarget(self, action: #selector(backspaceUp),
                           for: [.touchUpInside, .touchUpOutside, .touchCancel])
            stack.addArrangedSubview(back)
            weights.append((back, 1.4))
        } else if !showingNumeric {
            let period = characterKey(".")
            period.tag = 3
            stack.addArrangedSubview(period)
            weights.append((period, 0.9))
        }

        let ret = functionKey("⏎", action: #selector(tapReturn))
        stack.addArrangedSubview(ret)
        weights.append((ret, panel ? 1.6 : 1.4))

        // Space takes what is left; every other key gets its share.
        let total = weights.reduce(spaceWeight) { $0 + $1.1 }
        let gaps = CGFloat(weights.count) * stack.spacing
        for (key, w) in weights {
            key.widthAnchor.constraint(equalTo: stack.widthAnchor, multiplier: w / total,
                                       constant: -gaps * w / total).isActive = true
        }
        return stack
    }

    // MARK: - Keys

    private var bottomRowView: UIView?
    private var topLetterRowView: UIView?

    private static let displayName =
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "").lowercased()
    /// Through the script, like every label: `меджусловјанскы` in Cyrillic.
    private static var spaceLabel: String { Script.out(displayName) }

    private func styledKey(_ title: String) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 22)
        b.setTitleColor(.label, for: .normal)
        b.backgroundColor = .systemBackground
        b.layer.cornerRadius = 5
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.28
        b.layer.shadowOffset = CGSize(width: 0, height: 1)
        b.layer.shadowRadius = 0
        return b
    }

    private func characterKey(_ ch: Character) -> UIButton {
        let b = styledKey(title(for: ch))
        b.accessibilityIdentifier = String(ch)
        b.addTarget(self, action: #selector(tapCharacter(_:)), for: .touchUpInside)
        // Without this a key looks dead while you press it, and a keyboard that
        // does not visibly answer a touch reads as broken even when it works.
        b.addTarget(self, action: #selector(keyDown(_:)), for: .touchDown)
        b.addTarget(self, action: #selector(keyUp(_:)),
                    for: [.touchUpInside, .touchUpOutside, .touchCancel])
        if Layout.variants(for: ch, uppercase: false, numericLayer: showingNumeric,
                           latin: Script.mode == .latin) != nil {
            let hold = UILongPressGestureRecognizer(target: self,
                                                    action: #selector(holdKey(_:)))
            hold.minimumPressDuration = 0.3
            b.addGestureRecognizer(hold)
        }
        return b
    }

    private func functionKey(_ title: String, action: Selector) -> UIButton {
        let b = styledKey(title)
        b.backgroundColor = .tertiarySystemFill
        b.tintColorDidChange()
        b.addTarget(self, action: action, for: .touchUpInside)
        b.addTarget(self, action: #selector(keyDown(_:)), for: .touchDown)
        b.addTarget(self, action: #selector(keyUp(_:)),
                    for: [.touchUpInside, .touchUpOutside, .touchCancel])
        return b
    }

    @objc private func keyDown(_ sender: UIButton) {
        sender.backgroundColor = .systemGray3
        // Keyboard extensions may not play key clicks without full access, but
        // haptics are allowed and carry the same "it registered" signal.
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    @objc private func keyUp(_ sender: UIButton) {
        let isFunction = sender.accessibilityIdentifier == nil
        UIView.animate(withDuration: 0.08) {
            sender.backgroundColor = isFunction ? .tertiarySystemFill : .systemBackground
        }
    }

    private var shiftTitle: String {
        switch shift {
        case .off: return "⇧"
        case .on: return "⬆"
        case .locked: return "⇪"
        }
    }

    /// The label in the chosen script; the key itself stays the Latin letter.
    private func title(for ch: Character) -> String {
        guard !showingNumeric, ch.isLetter else { return String(ch) }
        let latin = Layout.keyText(shift == .off ? ch : Character(ch.uppercased()),
                                   latin: Script.mode == .latin)
        return Script.out(latin)
    }

    private var isUppercase: Bool { shift != .off }

    // MARK: - Actions

    @objc private func tapCharacter(_ sender: UIButton) {
        guard let id = sender.accessibilityIdentifier, let ch = id.first else { return }
        let typed: Character = isUppercase && ch.isLetter ? Character(ch.uppercased()) : ch
        insert(showingNumeric ? String(typed) : Layout.keyText(typed, latin: Script.mode == .latin))
        if ch.isLetter { lastLetterTapAt = Date() }
        if shift == .on { shift = .off; refreshTitles() }
    }

    @objc private func holdKey(_ g: UILongPressGestureRecognizer) {
        guard let key = g.view as? UIButton,
              let id = key.accessibilityIdentifier, let ch = id.first,
              let variants = Layout.variants(for: ch, uppercase: isUppercase,
                                             numericLayer: showingNumeric,
                                             latin: Script.mode == .latin)
        else { return }

        switch g.state {
        case .began:
            showPopup(over: key, variants: variants)
            selected = 0
            highlightSelection()
        case .changed:
            // Slide the finger along the popup to choose. Anything above or
            // below the strip still tracks horizontally, so the gesture does
            // not need to be precise vertically.
            let x = g.location(in: popupStrip ?? view).x
            selected = indexOfVariant(atX: x, count: variants.count)
            highlightSelection()
        case .ended:
            if variants.indices.contains(selected) { insert(variants[selected]) }
            dismissPopup()
            if shift == .on { shift = .off; refreshTitles() }
        case .cancelled, .failed:
            dismissPopup()
        default:
            break
        }
    }

    /// One press: the next letter capital. Two: Caps Lock. Three: off.
    /// With text selected it changes THAT text instead: small -> Capital ->
    /// CAPS -> small, as on the system keyboard (owner 2026-10-25).
    @objc private func tapShift() {
        if cycleCase() { return }
        switch shift {
        case .off: shift = .on
        case .on: shift = .locked
        case .locked: shift = .off
        }
        shiftByHand = shift != .off
        #if DEBUG
        GestureFile.note("shiftKey", ["state": "\(shift)"])
        #endif
        buildKeyboard()
    }

    /// Fires on touch DOWN, like the system keyboard: waiting for the lift is
    /// what made backspace feel like it "doesn't catch". Holding repeats.
    ///
    /// A hold that began by wiping a swiped word TRIMS it instead: the word
    /// comes back one letter short and loses letters while held, slowly enough
    /// to stop on the right one. That is the one-gesture way to fix an ending
    /// (owner, 2026-10-08: space-then-backspace was too much fiddling).
    @objc private func backspaceDown() {
        recased = nil
        if decoding > 0 { queuedKeys.append { [weak self] in self?.tapBackspace() }; return }
        let before = lastSwipedWord
        tapBackspace()
        trimAfterWipe = (before != nil && lastSwipedWord == nil && !lastCandidates.isEmpty) ? before : nil
        backspaceRepeat?.invalidate()
        backspaceRepeat = Timer.scheduledTimer(withTimeInterval: 0.45, repeats: false) { [weak self] _ in
            guard let self else { return }
            if let word = self.trimAfterWipe {
                self.trimAfterWipe = nil
                self.restoreTrimmed(word)
                self.backspaceRepeat = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { _ in
                    self.tapBackspace()
                }
            } else {
                self.backspaceRepeat = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { _ in
                    self.tapBackspace()
                }
            }
        }
    }

    @objc private func backspaceUp() {
        backspaceRepeat?.invalidate()
        backspaceRepeat = nil
        trimAfterWipe = nil
    }

    /// Puts a wiped word back minus its last letter, as plain typed text, so
    /// further backspaces take letters and the bar completes the stem.
    private func restoreTrimmed(_ word: String) {
        // Trimmed in Latin, then written back: Glagolitic `y` is two marks.
        let stem = Script.out(String(Script.toLatin(word).dropLast()))
        if !stem.isEmpty { textDocumentProxy.insertText(stem) }
        if !wipedSuffix.isEmpty {
            textDocumentProxy.insertText(wipedSuffix)
            textDocumentProxy.adjustTextPosition(byCharacterOffset: -wipedSuffix.count)
        }
        wipedSuffix = ""
        swipeSuffix = ""
        lastSwipedWord = nil
        pendingSpace = false
        lastCandidates = []
        swipeInput.forgetSample()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        refreshCompletions()
    }

    @objc private func tapBackspace() {
        defer { rearmShiftIfSentenceStart() }
        // Right after a swipe (no other key in between) backspace means "wrong
        // word" and takes it whole - at once, no matter how long the thumb took
        // to come back (owner, 2026-10-07: "nobody can be made to wait").
        // To fix only an ending: space first, then backspace deletes letters.
        // Only if the word is still right behind the cursor: after a tap
        // somewhere else in the text, wiping word.count letters there would
        // eat whatever sits at the new spot.
        if let word = lastSwipedWord,
           (textDocumentProxy.documentContextBeforeInput ?? "").hasSuffix(word + swipeSuffix) {
            for _ in 0..<(word.count + swipeSuffix.count) { textDocumentProxy.deleteBackward() }
            wipedSuffix = swipeSuffix
            swipeSuffix = ""
            lastSwipedWord = nil
            pendingSpace = false
            swipeCommittedAt = .distantPast
            // Offer the other guesses first; the rejected one goes last, not
            // away - the press is often reflex, before the eye has read it.
            let latin = Script.toLatin(word)
            var rest = lastCandidates.filter { $0.lowercased() != latin.lowercased() }
            rest.append(latin)
            lastCandidates = rest
            showSuggestions(rest, current: nil)
            return
        }
        lastSwipedWord = nil
        swipeSuffix = ""
        pendingSpace = false
        tappedWord = tappedWord.map { String($0.dropLast()) }
        textDocumentProxy.deleteBackward()
        refreshCompletions()
    }
    @objc private func tapSpace() { insert(" ") }
    @objc private func tapReturn() { insert("\n") }
    @objc private func tapNextKeyboard() { advanceToNextInputMode() }

    @objc private func tapScript() {
        Script.next()
        buildKeyboard()
        refreshCompletions()
    }

    @objc private func tapSymbols() {
        showingSymbols.toggle()
        buildKeyboard()
    }

    @objc private func tapEmojiKey() {
        showingEmoji.toggle()
        showingNumeric = false
        showingSymbols = false
        buildKeyboard()
    }

    @objc private func holdLayer(_ g: UILongPressGestureRecognizer) {
        guard let key = g.view as? UIButton else { return }
        switch g.state {
        case .began:
            showPopup(over: key, variants: ["⚙"])
            selected = 0
            highlightSelection()
        case .ended:
            dismissPopup()
            showingSettings = true
            showingEmoji = false
            showingNumeric = false
            showingSymbols = false
            showSuggestions([])
            buildKeyboard()
        case .cancelled, .failed:
            dismissPopup()
        default:
            break
        }
    }

    @objc private func closeSettings() {
        showingSettings = false
        buildKeyboard()
        refreshCompletions()
    }

    /// Theme, script and the words added with `＋`, inside the keyboard. Works
    /// without Full Access too: then it is the keyboard's own copy, which the
    /// app cannot see - the keyboard itself never needs the app.
    private func settingsPanel() -> UIView {
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = true
        let column = UIStackView()
        column.axis = .vertical
        column.spacing = 6
        column.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 4),
            column.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -4),
            column.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 2),
            column.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -2),
        ])

        func header(_ text: String) -> UILabel {
            let l = UILabel()
            l.text = Script.out(text)
            l.font = .systemFont(ofSize: 14, weight: .semibold)
            l.textColor = .secondaryLabel
            return l
        }
        func choiceRow(_ items: [(String, Bool, () -> Void)]) -> UIView {
            let row = UIStackView()
            row.axis = .horizontal
            row.distribution = .fillEqually
            row.spacing = 5
            for (title, on, act) in items {
                let b = styledKey(title)
                b.titleLabel?.font = .systemFont(ofSize: 20)
                if on {
                    b.backgroundColor = .systemBlue
                    b.setTitleColor(.white, for: .normal)
                }
                b.addAction(UIAction { _ in act() }, for: .touchUpInside)
                row.addArrangedSubview(b)
            }
            row.heightAnchor.constraint(equalToConstant: 40).isActive = true
            return row
        }

        column.addArrangedSubview(header("Tema"))
        let themes: [(String, Shared.Theme)] = [("◐", .auto), ("☀", .light), ("☾", .dark)]
        column.addArrangedSubview(choiceRow(themes.map { icon, t in
            (icon, Shared.theme == t, { [weak self] in
                Shared.theme = t
                self?.applyTheme()
                self?.buildKeyboard()
            })
        }))

        if Layout.qwertzOffered {
            column.addArrangedSubview(header("Razloženje klaviatury"))
            column.addArrangedSubview(choiceRow([false, true].map { z in
                (z ? "QWERTZ" : "QWERTY", Layout.qwertz == z, { [weak self] in
                    Layout.qwertz = z
                    self?.buildKeyboard()
                })
            }))
        }

        if Layout.language == "isv" {
            column.addArrangedSubview(header("Pismo"))
            let scripts: [(String, Script.Mode)] = [("ABC", .latin), ("АБВ", .cyrillic), ("ⰀⰁⰂ", .glagolitic)]
            column.addArrangedSubview(choiceRow(scripts.map { label, m in
                (label, Script.mode == m, { [weak self] in
                    Shared.store.set(m.rawValue, forKey: "script")
                    self?.buildKeyboard()
                })
            }))
        }

        column.addArrangedSubview(header("Moje slova"))
        let words = swipeInput.userWords
        if words.isEmpty {
            let l = UILabel()
            l.text = "—"
            l.textColor = .secondaryLabel
            column.addArrangedSubview(l)
        }
        for w in words.sorted() {
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 8
            let l = UILabel()
            l.text = Script.out(w)
            l.font = .systemFont(ofSize: 18)
            l.textColor = .label
            let x = styledKey("✕")
            x.titleLabel?.font = .systemFont(ofSize: 16)
            x.widthAnchor.constraint(equalToConstant: 44).isActive = true
            x.addAction(UIAction { [weak self] _ in
                self?.swipeInput.removeUserWord(w)
                self?.buildKeyboard()
            }, for: .touchUpInside)
            row.addArrangedSubview(l)
            row.addArrangedSubview(x)
            row.heightAnchor.constraint(equalToConstant: 36).isActive = true
            column.addArrangedSubview(row)
        }
        return scroll
    }

    /// The panel: lately used first, then the common set, in rows of eight.
    private func emojiPanel() -> UIView {
        let scroll = UIScrollView()
        scroll.showsVerticalScrollIndicator = true
        let column = UIStackView()
        column.axis = .vertical
        column.spacing = 2
        column.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            column.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            column.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            column.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            column.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])
        let recent = Emoji.recent
        let all = recent + Emoji.common.filter { !recent.contains($0) }
        let perRow = 8
        for start in stride(from: 0, to: all.count, by: perRow) {
            let row = UIStackView()
            row.axis = .horizontal
            row.distribution = .fillEqually
            for i in start..<start + perRow {
                let b = UIButton(type: .system)
                if i < all.count {
                    b.setTitle(all[i], for: .normal)
                    b.titleLabel?.font = .systemFont(ofSize: 28)
                    b.addTarget(self, action: #selector(tapEmoji(_:)), for: .touchUpInside)
                }
                row.addArrangedSubview(b)
            }
            row.heightAnchor.constraint(equalToConstant: 42).isActive = true
            column.addArrangedSubview(row)
        }
        return scroll
    }

    @objc private func tapEmoji(_ sender: UIButton) {
        guard let e = sender.title(for: .normal) else { return }
        // After a swipe the owed space comes first: `pisem 😀`, not `pisem😀`.
        if pendingSpace { insert(" ") }
        insert(e)
        Emoji.used(e)
    }

    @objc private func tapLayer() {
        showingEmoji = false
        showingNumeric.toggle()
        showingSymbols = false
        showSuggestions([])
        buildKeyboard()
    }

    /// Puts LATIN text in, written in the chosen script. One key's worth: a
    /// letter, a held letter or digraph (`dž`), a mark, space or return.
    private func insert(_ latin: String) {
        guard let ch = latin.first else { return }
        recased = nil
        if decoding > 0 { queuedKeys.append { [weak self] in self?.insert(latin) }; return }
        // A letter right after a glide EXTENDS the glided word only if the two
        // together can still grow into a word. The window is now just a
        // ceiling: time alone glued the start of the next word onto the last
        // one whenever the user typed quickly (`wyjsc` + `s` -> `wyjscs`), and
        // that is the common case (Android 3.5.39). Price: extending into a
        // word the list does not know gets a space; `＋` once fixes it for good.
        let extendsSwipe = ch.isLetter && insideWipeWindow
            && lastSwipedWord.map { swipeInput.canExtend(Script.toLatin($0) + latin) } == true
        // Anything but a letter extending a swiped word means the word was
        // kept: that gesture now tells us where this thumb lands.
        if let kept = lastSwipedWord, !extendsSwipe {
            swipeInput.learn(from: Script.toLatin(kept))
        }
        if pendingSpace {
            // A letter right after a swipe extends that word (`pisem` + `s`);
            // a letter later starts the next word and gets the owed space.
            // Space and punctuation simply cancel it.
            if ch.isLetter && !extendsSwipe { textDocumentProxy.insertText(" ") }
            pendingSpace = false
        }
        // Track a word typed letter by letter: that is the sign a swipe did
        // not find it, and the only case where `＋` is offered for a word the
        // list already has. Kept in Latin, like everything inside.
        if ch.isLetter && !extendsSwipe {
            let prev = textDocumentProxy.documentContextBeforeInput?.last
            tappedWord = (prev?.isLetter == true) ? tappedWord.map { $0 + latin } : latin
        } else if ch.isLetter {
            tappedWord = nil
        } else {
            lastTappedWord = tappedWord
            tappedWord = nil
        }
        lastSwipedWord = nil
        swipeSuffix = ""
        textDocumentProxy.insertText(Script.out(latin))
        rearmShiftIfSentenceStart()
        refreshCompletions()
    }

    /// The text the last Shift press re-cased. Inserting it drops the
    /// selection (the proxy cannot select), so the next press finds it right
    /// before the cursor and goes on from there. Any other key ends it.
    private var recased: String?

    /// Re-cases the selection, or the word the previous press re-cased.
    /// False when there is nothing to re-case and Shift should act as Shift.
    private func cycleCase() -> Bool {
        let proxy = textDocumentProxy
        let text: String
        if let sel = proxy.selectedText, sel.contains(where: \.isLetter) {
            text = sel
        } else if let last = recased, proxy.selectedText?.isEmpty ?? true,
                  proxy.documentContextBeforeInput?.hasSuffix(last) == true {
            for _ in 0..<last.count { proxy.deleteBackward() }
            text = last
        } else {
            recased = nil
            return false
        }
        let next: String
        if text == text.lowercased() {
            next = text.capitalized
        } else if text == text.capitalized {
            next = text.uppercased()
        } else {
            next = text.lowercased()
        }
        proxy.insertText(next)
        recased = next
        lastSwipedWord = nil
        tappedWord = nil
        refreshCompletions()
        return true
    }

    /// When a letter was last typed by a tap. A touch this soon after belongs
    /// to the typing run, never to a swipe (Android 3.5.30, TAP_RUN_MS):
    /// `master` typed fast was decoded as `mastet` (owner 2026-10-15).
    private var lastLetterTapAt = Date.distantPast
    private static let tapRun: TimeInterval = 0.25

    /// The word being typed by taps, nil once a swipe or a suggestion touched it.
    private var tappedWord: String?
    /// The tapped word just finished by a space or punctuation.
    private var lastTappedWord: String?

    /// The bar while typing by taps: completions of the word under the cursor.
    /// Before this the bar kept the LAST SWIPE's candidates, which read as
    /// suggestions with no relation to what was being typed.
    ///
    /// A word the wordlist does not know gets `＋ word` in the FIRST slot,
    /// where it is seen (Android 3.5.29) - also right after its space, since
    /// that is when the eye notices nothing was offered. Lowercase-initial
    /// only: a capital is usually a name, and names do not belong in the list.
    ///
    /// The text is read in the chosen script and looked up in Latin.
    private func refreshCompletions() {
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let typedShown = String(before.reversed().prefix { $0.isLetter }.reversed())
        let typed = Script.toLatin(typedShown)
        barToken += 1
        let token = barToken
        guard !typed.isEmpty else {
            completingPrefix = nil
            showSuggestions([])
            guard before.hasSuffix(" ") else { return }
            let shownWord = String(before.dropLast().reversed().prefix { $0.isLetter }.reversed())
            let finished = Script.toLatin(shownWord)
            guard let save = Self.saveForm(finished,
                                           after: String(before.dropLast(shownWord.count + 1)))
            else { return }
            let tapped = lastTappedWord
            swipeInput.complete(finished, limit: 0) { [weak self] _, known, rare in
                guard let self, self.barToken == token,
                      !known || (rare && tapped == finished) else { return }
                self.showSuggestions([Self.savePrefix + save], current: nil)
            }
            return
        }
        completingPrefix = typedShown
        let tapped = tappedWord
        swipeInput.complete(typed) { [weak self] words, known, rare in
            guard let self, self.barToken == token, self.completingPrefix == typedShown else { return }
            let upper = typed.first?.isUppercase == true
            var shown = words.map { upper ? $0.prefix(1).uppercased() + $0.dropFirst() : $0 }
            if !known || (rare && tapped == typed),
               let save = Self.saveForm(typed, after: String(before.dropLast(typedShown.count))) {
                shown = [Self.savePrefix + save] + shown.prefix(2)
            }
            self.showSuggestions(shown, current: nil)
        }
    }

    private var barToken = 0
    /// What each bar slot holds, in Latin (`＋ ` marks a save chip); the
    /// buttons show it in the chosen script.
    fileprivate var slotWords: [String?] = [nil, nil, nil]
    fileprivate static let savePrefix = "＋ "

    /// What `＋` would save for `word`, or nil. Lowercase-initial words as
    /// they are. A capital only where shift put it - the first word of a
    /// sentence (`Neznaneslovo` got no chip, owner 2026-10-25) - and then
    /// saved in lowercase; a capital mid-sentence is a name and stays out.
    private static func saveForm(_ word: String, after before: String) -> String? {
        guard word.count >= 3, SwipeDictionary.keys(of: word.lowercased()) != nil,
              let first = word.first else { return nil }
        if first.isLowercase { return word }
        guard first.isUppercase, !word.dropFirst().contains(where: \.isUppercase),
              sentenceStart(before) else { return nil }
        return word.lowercased()
    }

    /// True when `before` (the text ahead of a word) ends a sentence: nothing
    /// yet, a new line, or `.` `?` `!` and spaces.
    private static func sentenceStart(_ before: String) -> Bool {
        let rest = before.reversed().drop { $0 == " " }
        guard let last = rest.first else { return true }
        return last.isNewline || ".?!…".contains(last)
    }

    /// Shift used to arm once at load and never again, so everything after the
    /// first word was lowercase forever. Re-arm at the start of a sentence -
    /// but never while Caps Lock is deliberately on.
    private func rearmShiftIfSentenceStart(confirmed: Bool = false) {
        guard shift != .locked, !shiftByHand else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        // `Probne versije SŠA`: shift came on mid-line after a cursor move.
        // Right after one, the proxy can report no context AND no text for a
        // moment. Never decide from that - look again once it has settled.
        if before.isEmpty && !confirmed {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { [weak self] in
                self?.rearmShiftIfSentenceStart(confirmed: true)
            }
            return
        }
        // An EMPTY context is not proof of an empty field: right after our own
        // edits some hosts briefly report nothing, and that armed shift in the
        // middle of a word (`napiŠ`). Empty counts only when the field is.
        // A bare new line is NOT a sentence start (owner 2026-11-07): typing a
        // list or a word per line got a capital on every line, and deleting a
        // test mark at the start of a line armed it again each time (log
        // 17:38, `chciałbym \n\n` -> on). A new line after `.` `!` `?` still is.
        let lines = before.trimmingCharacters(in: .whitespacesAndNewlines)
        let atStart = before.isEmpty ? !textDocumentProxy.hasText : lines.isEmpty
        let afterStop = lines.last.map { ".!?".contains($0) } ?? false
        let gap = before.last.map { $0 == " " || $0.isNewline } ?? false
        // Only act on a real transition, or every keystroke rebuilds the view.
        let wanted: ShiftState = (atStart || (afterStop && gap)) ? .on : .off
        if wanted != shift {
            #if DEBUG
            // Why shift switched: the tail the proxy reported at that moment.
            GestureFile.note("shift", ["on": wanted == .on, "ctx": String(before.suffix(16)),
                                       "hasText": textDocumentProxy.hasText, "confirmed": confirmed])
            #endif
            shift = wanted
            refreshTitles()
            rowsStack?.arrangedSubviews.forEach { row in
                (row as? UIStackView)?.arrangedSubviews.forEach { v in
                    if let b = v as? UIButton, b.accessibilityIdentifier == nil,
                       b.title(for: .normal)?.contains("⇧") == true
                        || b.title(for: .normal)?.contains("⬆") == true {
                        b.setTitle(shiftTitle, for: .normal)
                    }
                }
            }
        }
    }

    private func refreshTitles() {
        for b in letterButtons {
            if let id = b.accessibilityIdentifier, let ch = id.first {
                b.setTitle(title(for: ch), for: .normal)
            }
        }
    }

    // MARK: - Longpress popup

    private func showPopup(over key: UIButton, variants: [String]) {
        dismissPopup()

        // One label per variant so a finger slide can pick between them.
        let strip = UIStackView()
        strip.axis = .horizontal
        strip.distribution = .fillEqually
        strip.spacing = 0
        strip.backgroundColor = .systemBackground
        strip.layer.cornerRadius = 6
        strip.layer.borderWidth = 1
        strip.layer.borderColor = UIColor.separator.cgColor
        strip.layer.masksToBounds = true
        strip.translatesAutoresizingMaskIntoConstraints = false

        // The first variant sits over the key and the rest run AWAY from the
        // nearer edge, so a key on the right half opens leftward. Centring the
        // strip pushed a long one (11 marks on `.`) against the left margin,
        // and the comma - the first and most used - landed far from the thumb
        // (owner 2026-11-07). `variantLabels` stays in variant order; only the
        // strip shows them mirrored.
        let opensLeft = key.convert(key.bounds, to: view).midX > view.bounds.midX
        popupOpensLeft = opensLeft
        variantLabels = variants.map { item in
            let l = UILabel()
            // Every surface the user sees goes through the script - the
            // Android popup once skipped it and Cyrillic `з` held showed `з`.
            l.text = Script.out(item)
            l.adjustsFontSizeToFitWidth = true
            l.minimumScaleFactor = 0.6
            l.font = .systemFont(ofSize: 24)
            l.textAlignment = .center
            l.textColor = .label
            return l
        }
        for l in (opensLeft ? variantLabels.reversed() : variantLabels) {
            strip.addArrangedSubview(l)
        }

        view.addSubview(strip)

        // A keyboard extension is clipped to its own frame - it cannot draw
        // over the app above it the way the system keyboard does. So a popup
        // pinned above a TOP ROW key would be laid out off-screen and simply
        // never appear. Only row 0 has nowhere to go; measuring this in points
        // was a mistake, because row 1 missed the threshold by a fraction and
        // dropped below too. The row index is exact and cannot drift.
        let fitsAbove = key.tag > 0

        var constraints = [
            opensLeft
                ? strip.trailingAnchor.constraint(equalTo: key.trailingAnchor, constant: 4)
                : strip.leadingAnchor.constraint(equalTo: key.leadingAnchor, constant: -4),
            strip.heightAnchor.constraint(equalTo: key.heightAnchor, multiplier: 1.15),
            strip.widthAnchor.constraint(greaterThanOrEqualTo: key.widthAnchor),
            strip.widthAnchor.constraint(
                equalToConstant: max(CGFloat(variants.count) * 40, 44)),
            strip.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor,
                                           constant: 2),
            strip.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor,
                                            constant: -2),
        ]
        constraints.append(fitsAbove
            ? strip.bottomAnchor.constraint(equalTo: key.topAnchor, constant: -2)
            : strip.topAnchor.constraint(equalTo: key.bottomAnchor, constant: 2))
        if fitsAbove {
            constraints.append(
                strip.topAnchor.constraint(greaterThanOrEqualTo: view.topAnchor,
                                           constant: 1))
        }
        // The width constraint is the one to give up if the strip would run off
        // the edge; the anchor to the key and the edge margins matter more.
        constraints[3].priority = .defaultHigh
        constraints[0].priority = .defaultHigh
        NSLayoutConstraint.activate(constraints)

        view.bringSubviewToFront(strip)
        view.layoutIfNeeded()
        popupStrip = strip
        popup = strip
    }

    private func indexOfVariant(atX x: CGFloat, count: Int) -> Int {
        guard let strip = popupStrip, count > 0, strip.bounds.width > 0 else { return 0 }
        let slot = strip.bounds.width / CGFloat(count)
        let i = min(count - 1, max(0, Int(x / slot)))
        return popupOpensLeft ? count - 1 - i : i
    }

    private func highlightSelection() {
        for (i, l) in variantLabels.enumerated() {
            let on = (i == selected)
            l.backgroundColor = on ? .systemBlue : .clear
            l.textColor = on ? .white : .label
        }
    }

    private func dismissPopup() {
        popup?.removeFromSuperview()
        popup = nil
        popupStrip = nil
        variantLabels = []
        selected = 0
    }
}

// MARK: - Suggestions and swiping

extension KeyboardViewController: SwipeHost {

    static let suggestionBarHeight: CGFloat = 38

    /// The bar sits INSIDE our own frame, above the keys, and the extension is
    /// made taller to hold it. That is the difference between this and the
    /// longpress popup: a keyboard extension may grow downward into its own
    /// height, it may not draw upward over the host app.
    fileprivate func buildSuggestionBar() {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.spacing = 1
        stack.translatesAutoresizingMaskIntoConstraints = false

        suggestionButtons = (0..<3).map { _ in
            let b = UIButton(type: .system)
            b.titleLabel?.font = .systemFont(ofSize: 17)
            b.titleLabel?.adjustsFontSizeToFitWidth = true
            b.titleLabel?.minimumScaleFactor = 0.7
            b.setTitleColor(.label, for: .normal)
            b.addTarget(self, action: #selector(tapSuggestion(_:)), for: .touchUpInside)
            stack.addArrangedSubview(b)
            return b
        }

        view.addSubview(stack)
        suggestionBar = stack
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 3),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -3),
            stack.topAnchor.constraint(equalTo: view.topAnchor, constant: 2),
            stack.heightAnchor.constraint(equalToConstant: Self.suggestionBarHeight),
        ])
    }

    /// `current` is the index of the word now in the text, marked bold; nil
    /// after a rejected swipe, when none of them is.
    fileprivate func showSuggestions(_ words: [String], current: Int? = 0) {
        slotWords = suggestionButtons.indices.map { i in
            i < words.count && !words[i].isEmpty ? words[i] : nil
        }
        for (i, b) in suggestionButtons.enumerated() {
            let word = slotWords[i].map { w in
                w.hasPrefix(Self.savePrefix)
                    ? Self.savePrefix + Script.out(String(w.dropFirst(Self.savePrefix.count)))
                    : Script.out(w)
            }
            b.setTitle(word, for: .normal)
            b.isEnabled = word != nil
            b.setTitleColor(i == current ? .label : .secondaryLabel, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 17, weight: i == current ? .semibold : .regular)
        }
    }

    @objc fileprivate func tapSuggestion(_ sender: UIButton) {
        guard let i = suggestionButtons.firstIndex(of: sender),
              let word = slotWords[i], !word.isEmpty else { return }
        if word.hasPrefix(Self.savePrefix) {
            // Saves; the text stays exactly as typed.
            let saved = String(word.dropFirst(Self.savePrefix.count))
            swipeInput.addUserWord(saved)
            slotWords[i] = nil
            sender.setTitle("✓ " + Script.out(saved), for: .normal)
            sender.isEnabled = false
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            return
        }
        if let typed = completingPrefix {
            // Replace the partly typed word and treat the result like a swipe:
            // space owed before the next word, backspace takes it whole.
            completingPrefix = nil
            tappedWord = nil
            for _ in 0..<typed.count { textDocumentProxy.deleteBackward() }
            let shown = Script.out(word)
            textDocumentProxy.insertText(shown)
            lastSwipedWord = shown
            swipeSuffix = ""
            swipeCommittedAt = Date()
            pendingSpace = true
            lastCandidates = []
            swipeInput.forgetSample()
            showSuggestions([word])
            return
        }
        if lastSwipedWord == nil {
            // After a rejected swipe nothing is in the text to replace.
            commitSwipe(word)
        } else {
            replaceLastSwipedWord(with: word)
        }
        // The tapped word becomes the committed one, so the bar re-marks it.
        var words = slotWords.compactMap { $0 }
        if let at = words.firstIndex(of: word) {
            words.remove(at: at)
            words.insert(word, at: 0)
        }
        showSuggestions(words)
    }

    /// Puts a decoded word in, spacing it from whatever came before.
    fileprivate func commitSwipe(_ word: String) {
        tappedWord = nil
        let cased = isUppercase ? word.prefix(1).uppercased() + word.dropFirst() : word
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let after = textDocumentProxy.documentContextAfterInput ?? ""
        let lead = pendingSpace
            || (before.last.map { !$0.isWhitespace && !Self.openers.contains($0) } ?? false)
        // Swiped into the middle of text, right before a word: bring the space
        // after it too, or it glues on (`Dobramožlivost`).
        let trail = after.first.map { $0.isLetter || $0.isNumber } ?? false
        swipeSuffix = trail ? " " : ""
        let shown = Script.out(cased)
        textDocumentProxy.insertText((lead ? " " : "") + shown + swipeSuffix)
        lastSwipedWord = shown
        swipeCommittedAt = Date()
        pendingSpace = !trail
        if shift == .on { shift = .off; refreshTitles() }
    }

    fileprivate func replaceLastSwipedWord(with word: String) {
        guard let previous = lastSwipedWord else { return }
        for _ in 0..<(previous.count + swipeSuffix.count) { textDocumentProxy.deleteBackward() }
        // Exactly as tapped. Copying the old word's capital here is why
        // tapping `dobra` under a wrongly capitalised `Dobra` changed nothing;
        // the bar already shows the candidates in the case they went in.
        let shown = Script.out(word)
        textDocumentProxy.insertText(shown + swipeSuffix)
        lastSwipedWord = shown
        pendingSpace = swipeSuffix.isEmpty
    }

    // MARK: SwipeHost

    var swipeSurface: UIView { view }

    /// A gesture may not START on the bottom row: a thumb sliding a little on
    /// space was taken for a swipe and the space was cancelled.
    func swipeCanStart(at point: CGPoint) -> Bool {
        // Mid-word hand typing: a touch landing right after a typed letter is
        // the next tap, even if the finger rolls. What is typed by hand stays
        // as typed - nothing gets decoded over it.
        if Date().timeIntervalSince(lastLetterTapAt) < Self.tapRun { return false }
        guard let bottom = bottomRowView else { return true }
        // Nor above the letters (the suggestion bar).
        if let top = topLetterRowView, point.y < top.convert(top.bounds, to: view).minY - 3 {
            return false
        }
        return point.y < bottom.convert(bottom.bounds, to: view).minY - 3
    }

    var swipeCanBegin: Bool {
        // No letters to cross on the numeric layer, and the longpress popup
        // owns the finger while it is up.
        !showingNumeric && !showingEmoji && !showingSettings && popup == nil
    }

    var swipeKeyCentres: ([CGPoint], CGFloat) {
        var centres = [CGPoint](repeating: .zero, count: Layout.swipeKeys.count)
        var width: CGFloat = 0
        guard !showingNumeric else { return (centres, 0) }

        for b in letterButtons {
            guard let id = b.accessibilityIdentifier,
                  let ch = id.lowercased().first,
                  let index = Layout.swipeIndex(ch)
            else { continue }
            let centre = b.superview?.convert(b.center, to: view) ?? b.center
            centres[index] = centre
            width = max(width, b.bounds.width)
        }
        return (centres, width)
    }

    func swipeWillDecode() {
        // Two swipes in a row: the first word was kept.
        if let kept = lastSwipedWord { swipeInput.learn(from: kept) }
        decoding += 1
        // Never strand the keys: if a decode somehow never reports back, let
        // them through after a second anyway.
        let mine = decoding
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            guard let self, self.decoding == mine else { return }
            self.decoding = 0
            self.flushQueuedKeys()
        }
    }

    private func flushQueuedKeys() {
        let keys = queuedKeys
        queuedKeys = []
        keys.forEach { $0() }
    }

    /// A glide that produced NOTHING still ends the glide before it (Android
    /// 3.7, `KeyboardView.glideMissed`). Returning early left the state of the
    /// word committed EARLIER armed: the next backspace wiped it whole, the
    /// bar still offered its old alternatives, and a letter typed inside the
    /// extend window glued onto it. Nothing is deleted - the previous word
    /// stays as typed, it just stops being "the word just glided".
    ///
    /// The owed space is kept: it belongs to the word standing in the text.
    /// The earlier word was already learned from in `swipeWillDecode`; the
    /// sample of THIS gesture is dropped, because no word came from it.
    private func glideMissed() {
        lastSwipedWord = nil
        lastCandidates = []
        swipeSuffix = ""
        swipeCommittedAt = .distantPast
        trimAfterWipe = nil
        completingPrefix = nil
        swipeInput.forgetSample()
        // Deliberately not refreshCompletions(): the cursor sits after the
        // previous word, so completions would continue a word nobody is typing.
        showSuggestions([])
    }

    func swipeDidFinish(candidates: [String]) {
        decoding = max(0, decoding - 1)
        defer { if decoding == 0 { flushQueuedKeys() } }
        guard let best = candidates.first else {
            glideMissed()
            return
        }
        completingPrefix = nil
        commitSwipe(best)
        // Show the candidates in the case the word went in, so the bar and the
        // text agree and a tap puts in exactly what it shows.
        let upper = lastSwipedWord.map { Script.toLatin($0) }?.first?.isUppercase == true
        var shown: [String] = []
        for c in candidates {
            let w = upper ? c.prefix(1).uppercased() + c.dropFirst() : c
            if !shown.contains(w) { shown.append(w) }
        }
        lastCandidates = shown
        showSuggestions(shown)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
