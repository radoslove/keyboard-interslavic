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

    private var shift: ShiftState = .on          // sentence start
    private var showingNumeric = false
    private var rowsStack: UIStackView!
    private var letterButtons: [UIButton] = []
    private var popup: UIView?
    private var popupStrip: UIStackView?
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
        lastSwipedWord = nil
        pendingSpace = false
        rearmShiftIfSentenceStart()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        rearmShiftIfSentenceStart()
    }

    // MARK: - Building

    private func buildKeyboard() {
        rowsStack?.removeFromSuperview()
        letterButtons.removeAll()

        let rows = showingNumeric ? Layout.numericRows : Layout.letterRows
        var rowViews: [UIView] = rows.enumerated().map { characterRow($1, row: $0) }
        rowViews.append(bottomRow())

        let stack = UIStackView(arrangedSubviews: rowViews)
        stack.axis = .vertical
        stack.distribution = .fillEqually
        stack.spacing = 6
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
        // Shift and backspace are 1.5 keys wide here, as on the system
        // keyboard: 7 letters + 2 x 1.5 = the 10 keys of the top row, so the
        // letters keep the same width (swipe geometry depends on it). The
        // period moved to the bottom row - next to backspace it stole presses.
        var first: UIButton?
        var wide: [UIButton] = []
        if isLastLetterRow {
            stack.distribution = .fill
            let shiftKey = functionKey(shiftTitle, action: #selector(tapShift))
            stack.addArrangedSubview(shiftKey)
            wide.append(shiftKey)
        }
        for ch in chars {
            let b = characterKey(ch)
            b.tag = row                      // which row a key sits in decides
            letterButtons.append(b)          // where its popup can go
            stack.addArrangedSubview(b)
            if isLastLetterRow {
                if let f = first { b.widthAnchor.constraint(equalTo: f.widthAnchor).isActive = true }
                else { first = b }
            }
        }
        if isLastLetterRow {
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
                for w in wide {
                    w.widthAnchor.constraint(equalTo: f.widthAnchor, multiplier: 1.5,
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

        let layerKey = functionKey(showingNumeric ? "ABC" : "123",
                                   action: #selector(tapLayer))
        layerKey.widthAnchor.constraint(equalToConstant: 46).isActive = true
        stack.addArrangedSubview(layerKey)

        // Apple requires a way off our keyboard when the system offers one.
        if needsInputModeSwitchKey {
            let globe = functionKey("🌐", action: #selector(tapNextKeyboard))
            globe.widthAnchor.constraint(equalToConstant: 46).isActive = true
            stack.addArrangedSubview(globe)
        }

        let space = functionKey(" ", action: #selector(tapSpace))
        space.backgroundColor = .systemBackground
        stack.addArrangedSubview(space)

        if !showingNumeric {
            let period = characterKey(".")
            period.tag = 3
            period.widthAnchor.constraint(equalToConstant: 40).isActive = true
            stack.addArrangedSubview(period)
        }

        let ret = functionKey("⏎", action: #selector(tapReturn))
        ret.widthAnchor.constraint(equalToConstant: 74).isActive = true
        stack.addArrangedSubview(ret)
        return stack
    }

    // MARK: - Keys

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
        if Layout.variants(for: ch, uppercase: false) != nil {
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

    private func title(for ch: Character) -> String {
        guard !showingNumeric, ch.isLetter else { return String(ch) }
        return shift == .off ? String(ch) : ch.uppercased()
    }

    private var isUppercase: Bool { shift != .off }

    // MARK: - Actions

    @objc private func tapCharacter(_ sender: UIButton) {
        guard let id = sender.accessibilityIdentifier, let ch = id.first else { return }
        insert(isUppercase && ch.isLetter ? Character(ch.uppercased()) : ch)
        if shift == .on { shift = .off; refreshTitles() }
    }

    @objc private func holdKey(_ g: UILongPressGestureRecognizer) {
        guard let key = g.view as? UIButton,
              let id = key.accessibilityIdentifier, let ch = id.first,
              let variants = Layout.variants(for: ch, uppercase: isUppercase)
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

    @objc private func tapShift() {
        switch shift {
        case .off: shift = .on
        case .on: shift = .locked
        case .locked: shift = .off
        }
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
        let stem = String(word.dropLast())
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
            var rest = lastCandidates.filter { $0.lowercased() != word.lowercased() }
            rest.append(word)
            lastCandidates = rest
            showSuggestions(rest, current: nil)
            return
        }
        lastSwipedWord = nil
        swipeSuffix = ""
        pendingSpace = false
        textDocumentProxy.deleteBackward()
        refreshCompletions()
    }
    @objc private func tapSpace() { insert(" ") }
    @objc private func tapReturn() { insert("\n") }
    @objc private func tapNextKeyboard() { advanceToNextInputMode() }

    @objc private func tapLayer() {
        showingNumeric.toggle()
        showSuggestions([])
        buildKeyboard()
    }

    private func insert(_ ch: Character) {
        if decoding > 0 { queuedKeys.append { [weak self] in self?.insert(ch) }; return }
        // Anything but a letter extending a swiped word means the word was
        // kept: that gesture now tells us where this thumb lands.
        if let kept = lastSwipedWord, !(ch.isLetter && insideWipeWindow) {
            swipeInput.learn(from: kept)
        }
        if pendingSpace {
            // A letter right after a swipe extends that word (`pisem` + `s`);
            // a letter later starts the next word and gets the owed space.
            // Space and punctuation simply cancel it.
            if ch.isLetter && !insideWipeWindow { textDocumentProxy.insertText(" ") }
            pendingSpace = false
        }
        lastSwipedWord = nil
        swipeSuffix = ""
        textDocumentProxy.insertText(String(ch))
        rearmShiftIfSentenceStart()
        refreshCompletions()
    }

    /// The bar while typing by taps: completions of the word under the cursor.
    /// Before this the bar kept the LAST SWIPE's candidates, which read as
    /// suggestions with no relation to what was being typed.
    ///
    /// A word the wordlist does not know gets `＋ word` in the last slot - also
    /// right after its space, since that is when the eye notices nothing was
    /// offered. Lowercase-initial only, as on Android: a capital is usually a
    /// name, and names do not belong in the wordlist.
    private func refreshCompletions() {
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let typed = String(before.reversed().prefix { $0.isLetter }.reversed())
        barToken += 1
        let token = barToken
        guard !typed.isEmpty else {
            completingPrefix = nil
            showSuggestions([])
            guard before.hasSuffix(" ") else { return }
            let finished = String(before.dropLast().reversed().prefix { $0.isLetter }.reversed())
            guard Self.saveable(finished) else { return }
            swipeInput.complete(finished, limit: 0) { [weak self] _, known in
                guard let self, self.barToken == token, !known else { return }
                self.showSuggestions(["", "", Self.savePrefix + finished], current: nil)
            }
            return
        }
        completingPrefix = typed
        swipeInput.complete(typed) { [weak self] words, known in
            guard let self, self.barToken == token, self.completingPrefix == typed else { return }
            let upper = typed.first?.isUppercase == true
            var shown = words.map { upper ? $0.prefix(1).uppercased() + $0.dropFirst() : $0 }
            if !known && Self.saveable(typed) {
                shown = Array(shown.prefix(2))
                while shown.count < 2 { shown.append("") }
                shown.append(Self.savePrefix + typed)
            }
            self.showSuggestions(shown, current: nil)
        }
    }

    private var barToken = 0
    fileprivate static let savePrefix = "＋ "

    private static func saveable(_ word: String) -> Bool {
        word.count >= 3 && word.first?.isLowercase == true && SwipeDictionary.keys(of: word) != nil
    }

    /// Shift used to arm once at load and never again, so everything after the
    /// first word was lowercase forever. Re-arm at the start of a sentence -
    /// but never while Caps Lock is deliberately on.
    private func rearmShiftIfSentenceStart() {
        guard shift != .locked else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let trimmed = before.trimmingCharacters(in: .whitespaces)
        // An EMPTY context is not proof of an empty field: right after our own
        // edits some hosts briefly report nothing, and that armed shift in the
        // middle of a word (`napiŠ`). Empty counts only when the field is.
        let atStart = before.isEmpty ? !textDocumentProxy.hasText : trimmed.isEmpty
        let afterStop = trimmed.last.map { ".!?".contains($0) } ?? false
        // Only act on a real transition, or every keystroke rebuilds the view.
        let wanted: ShiftState = (atStart || (afterStop && before.hasSuffix(" ")))
            ? .on : .off
        if wanted != shift {
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

    private func showPopup(over key: UIButton, variants: [Character]) {
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

        variantLabels = variants.map { ch in
            let l = UILabel()
            l.text = String(ch)
            l.font = .systemFont(ofSize: 24)
            l.textAlignment = .center
            l.textColor = .label
            strip.addArrangedSubview(l)
            return l
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
            strip.centerXAnchor.constraint(equalTo: key.centerXAnchor),
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
        // the edge; centring and the edge margins matter more.
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
        return min(count - 1, max(0, Int(x / slot)))
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
        for (i, b) in suggestionButtons.enumerated() {
            let word = i < words.count && !words[i].isEmpty ? words[i] : nil
            b.setTitle(word, for: .normal)
            b.isEnabled = word != nil
            b.setTitleColor(i == current ? .label : .secondaryLabel, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 17, weight: i == current ? .semibold : .regular)
        }
    }

    @objc fileprivate func tapSuggestion(_ sender: UIButton) {
        guard let word = sender.title(for: .normal), !word.isEmpty else { return }
        if word.hasPrefix(Self.savePrefix) {
            // Saves; the text stays exactly as typed.
            let saved = String(word.dropFirst(Self.savePrefix.count))
            swipeInput.addUserWord(saved)
            sender.setTitle("✓ " + saved, for: .normal)
            sender.isEnabled = false
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            return
        }
        if let typed = completingPrefix {
            // Replace the partly typed word and treat the result like a swipe:
            // space owed before the next word, backspace takes it whole.
            completingPrefix = nil
            for _ in 0..<typed.count { textDocumentProxy.deleteBackward() }
            textDocumentProxy.insertText(word)
            lastSwipedWord = word
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
        var words = suggestionButtons.compactMap { $0.title(for: .normal) }
        if let at = words.firstIndex(of: word) {
            words.remove(at: at)
            words.insert(word, at: 0)
        }
        showSuggestions(words)
    }

    /// Puts a decoded word in, spacing it from whatever came before.
    fileprivate func commitSwipe(_ word: String) {
        let cased = isUppercase ? word.prefix(1).uppercased() + word.dropFirst() : word
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let after = textDocumentProxy.documentContextAfterInput ?? ""
        let lead = pendingSpace
            || (before.last.map { !$0.isWhitespace && !Self.openers.contains($0) } ?? false)
        // Swiped into the middle of text, right before a word: bring the space
        // after it too, or it glues on (`Dobramožlivost`).
        let trail = after.first.map { $0.isLetter || $0.isNumber } ?? false
        swipeSuffix = trail ? " " : ""
        textDocumentProxy.insertText((lead ? " " : "") + cased + swipeSuffix)
        lastSwipedWord = cased
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
        textDocumentProxy.insertText(word + swipeSuffix)
        lastSwipedWord = word
        pendingSpace = swipeSuffix.isEmpty
    }

    // MARK: SwipeHost

    var swipeSurface: UIView { view }

    var swipeCanBegin: Bool {
        // No letters to cross on the numeric layer, and the longpress popup
        // owns the finger while it is up.
        !showingNumeric && popup == nil
    }

    var swipeKeyCentres: ([CGPoint], CGFloat) {
        var centres = [CGPoint](repeating: .zero, count: 26)
        var width: CGFloat = 0
        guard !showingNumeric else { return (centres, 0) }

        for b in letterButtons {
            guard let id = b.accessibilityIdentifier,
                  let ch = id.lowercased().first,
                  let ascii = ch.asciiValue,
                  ascii >= 97, ascii <= 122
            else { continue }
            let centre = b.superview?.convert(b.center, to: view) ?? b.center
            centres[Int(ascii) - 97] = centre
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

    func swipeDidFinish(candidates: [String]) {
        decoding = max(0, decoding - 1)
        defer { if decoding == 0 { flushQueuedKeys() } }
        guard let best = candidates.first else {
            showSuggestions([])
            return
        }
        completingPrefix = nil
        commitSwipe(best)
        // Show the candidates in the case the word went in, so the bar and the
        // text agree and a tap puts in exactly what it shows.
        let upper = lastSwipedWord?.first?.isUppercase == true
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
