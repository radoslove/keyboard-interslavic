import UIKit

/// Interslavic keyboard extension.
///
/// Deliberately plain UIKit: a keyboard extension runs under a hard memory cap,
/// and every framework pulled in here is memory the keys have to share.
///
/// Not implemented yet, in rough order of how much they are missed:
///   - numeric layer switching is wired but the layer is minimal
///   - no prediction bar (that is where the wordlist would go)
///   - no haptics, no key-press sound
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

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.secondarySystemBackground
        buildKeyboard()
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
            stack.topAnchor.constraint(equalTo: view.topAnchor, constant: 6),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                                          constant: -4),
            view.heightAnchor.constraint(greaterThanOrEqualToConstant: 216),
        ])
    }

    private func characterRow(_ chars: [Character], row: Int) -> UIView {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.spacing = 5

        // The third letter row carries shift and backspace on either side.
        let isLastLetterRow = !showingNumeric && chars == Layout.letterRows[2]
        if isLastLetterRow {
            stack.addArrangedSubview(functionKey(shiftTitle, action: #selector(tapShift)))
        }
        for ch in chars {
            let b = characterKey(ch)
            b.tag = row                      // which row a key sits in decides
            letterButtons.append(b)          // where its popup can go
            stack.addArrangedSubview(b)
        }
        if isLastLetterRow {
            let period = characterKey(".")
            period.tag = row
            stack.addArrangedSubview(period)
            stack.addArrangedSubview(functionKey("⌫", action: #selector(tapBackspace)))
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

    @objc private func tapBackspace() { textDocumentProxy.deleteBackward() }
    @objc private func tapSpace() { insert(" ") }
    @objc private func tapReturn() { insert("\n") }
    @objc private func tapNextKeyboard() { advanceToNextInputMode() }

    @objc private func tapLayer() {
        showingNumeric.toggle()
        buildKeyboard()
    }

    private func insert(_ ch: Character) {
        textDocumentProxy.insertText(String(ch))
        rearmShiftIfSentenceStart()
    }

    /// Shift used to arm once at load and never again, so everything after the
    /// first word was lowercase forever. Re-arm at the start of a sentence -
    /// but never while Caps Lock is deliberately on.
    private func rearmShiftIfSentenceStart() {
        guard shift != .locked else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let trimmed = before.trimmingCharacters(in: .whitespaces)
        let atStart = trimmed.isEmpty
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
