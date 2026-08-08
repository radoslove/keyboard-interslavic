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
        var rowViews: [UIView] = rows.map { characterRow($0) }
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

    private func characterRow(_ chars: [Character]) -> UIView {
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
            letterButtons.append(b)
            stack.addArrangedSubview(b)
        }
        if isLastLetterRow {
            stack.addArrangedSubview(characterKey("."))
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
        b.addTarget(self, action: action, for: .touchUpInside)
        return b
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
        case .ended, .cancelled:
            // Single-variant keys (the four letters) commit on release; the
            // multi-variant punctuation popup needs a real picker, which is
            // the next thing to build here.
            if variants.count == 1 { insert(variants[0]) }
            dismissPopup()
            if shift == .on { shift = .off; refreshTitles() }
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
        let label = UILabel()
        label.text = variants.map(String.init).joined(separator: " ")
        label.font = .systemFont(ofSize: 26)
        label.textAlignment = .center
        label.textColor = .label
        label.backgroundColor = .systemBackground
        label.layer.cornerRadius = 6
        label.layer.masksToBounds = true
        label.layer.borderWidth = 1
        label.layer.borderColor = UIColor.separator.cgColor
        label.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: key.centerXAnchor),
            label.bottomAnchor.constraint(equalTo: key.topAnchor, constant: -2),
            label.widthAnchor.constraint(greaterThanOrEqualTo: key.widthAnchor),
            label.heightAnchor.constraint(equalTo: key.heightAnchor, multiplier: 1.2),
        ])
        popup = label
    }

    private func dismissPopup() {
        popup?.removeFromSuperview()
        popup = nil
    }
}
