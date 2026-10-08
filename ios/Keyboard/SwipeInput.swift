import UIKit
import os

/// Device-side diagnostics. A keyboard extension cannot be attached to a
/// debugger the way an app can, so when it misbehaves on real hardware the
/// system log is the only witness.
///
/// Read it in Console.app with the device selected in the sidebar. NOT with
/// `idevicesyslog`: that relays the old syslog, which does not carry unified
/// logging below error level, so these notices are invisible there and their
/// absence proves nothing about whether the code ran.
let swipeLog = Logger(subsystem: "com.radoslove.interslavic", category: "isv-swipe")

/// What the keyboard has to provide for swiping to work.
protocol SwipeHost: AnyObject {
    /// The view the gesture and the trail live in.
    var swipeSurface: UIView { get }
    /// Centres of 'a'..'z' in that view's coordinates, and one key's width.
    /// `.zero` marks a letter the current layer does not show.
    var swipeKeyCentres: ([CGPoint], CGFloat) { get }
    /// False while something else owns the finger - the longpress popup, or a
    /// layer without letters.
    var swipeCanBegin: Bool { get }
    func swipeCanStart(at point: CGPoint) -> Bool
    /// The finger lifted and decoding started; keys pressed from now until
    /// `swipeDidFinish` must wait, or they land BEFORE the word.
    func swipeWillDecode()
    /// Best first, already ranked. Empty means the path decoded to nothing.
    func swipeDidFinish(candidates: [String])
}

/// Recognises a swipe and refuses to be a tap.
///
/// `UIPanGestureRecognizer` begins after about 10 points, which on a key grid
/// is inside the key you are pressing: sloppy taps would be swallowed and the
/// letter would not appear. This one waits for a distance expressed in key
/// widths, so the boundary between "pressed a key" and "started a word" is the
/// same gesture on every screen size.
final class SwipeGestureRecognizer: UIGestureRecognizer {

    /// Set by the host from the live key width.
    var threshold: CGFloat = 22

    private(set) var points: [CGPoint] = []
    private var origin: CGPoint = .zero

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        guard touches.count == 1, let touch = touches.first else {
            state = .failed
            return
        }
        origin = touch.location(in: view)
        points = [origin]
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesMoved(touches, with: event)
        guard let touch = touches.first, let view else { return }
        let p = touch.location(in: view)
        points.append(p)

        if state == .possible {
            let dx = p.x - origin.x, dy = p.y - origin.y
            if (dx * dx + dy * dy).squareRoot() >= threshold {
                state = .began
            }
        } else if state == .began || state == .changed {
            state = .changed
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesEnded(touches, with: event)
        state = (state == .began || state == .changed) ? .ended : .failed
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesCancelled(touches, with: event)
        state = .cancelled
    }

    override func reset() {
        super.reset()
        points = []
    }
}

/// Owns the gesture, the trail and the decoding.
final class SwipeInput: NSObject, UIGestureRecognizerDelegate {

    private weak var host: SwipeHost?
    private let recognizer = SwipeGestureRecognizer()
    private let trail = CAShapeLayer()
    private var dictionary: SwipeDictionary?

    /// Where this user's touches sit above the key they mean, in key widths.
    /// Learned from words they KEPT, so a thumb that lands high (or a phone
    /// whose glass makes it look so) stops costing them the first guess.
    private(set) var touchLift: Double = {
        let d = UserDefaults.standard
        return d.object(forKey: "touchLift") == nil
            ? SwipeDecoder.defaultTouchLift : d.double(forKey: "touchLift")
    }()
    /// Words the user added with `＋`, lowercase. On this phone only.
    private(set) var userWords: [String] = UserDefaults.standard.stringArray(forKey: "userWords") ?? []

    /// Saves a word the wordlist lacks (`napiše`, `koležanka`). From then on
    /// it completes and can be swiped. Never leaves the phone.
    func addUserWord(_ word: String) {
        let w = word.lowercased()
        guard SwipeDictionary.keys(of: w) != nil, !userWords.contains(w) else { return }
        userWords.append(w)
        UserDefaults.standard.set(userWords, forKey: "userWords")
    }

    private var userWordKeys: [(word: String, keys: [UInt8])] {
        userWords.compactMap { w in SwipeDictionary.keys(of: w).map { (w, $0.map(UInt8.init)) } }
    }

    /// The last gesture's ends, waiting to learn from until its word is kept.
    private var sample: (start: CGPoint, end: CGPoint, centres: [CGPoint], keyWidth: CGFloat)?

    /// Decoding runs off the main thread. On a 248 845-form lexicon a gesture
    /// touches a few thousand entries, which is fast - but "fast" on the main
    /// thread still means the trail stops moving, and a keyboard that stutters
    /// under the finger feels broken however good its guesses are.
    private let queue = DispatchQueue(label: "isv.swipe.decode", qos: .userInitiated)

    init(host: SwipeHost) {
        self.host = host
        super.init()

        trail.fillColor = nil
        trail.strokeColor = UIColor.systemBlue.withAlphaComponent(0.55).cgColor
        trail.lineWidth = 6
        trail.lineCap = .round
        trail.lineJoin = .round

        recognizer.addTarget(self, action: #selector(handle(_:)))
        recognizer.delegate = self
        // The keys must not also act on a touch that turned into a word.
        recognizer.cancelsTouchesInView = true
        // A tap must reach its key the moment the finger lifts. With the default
        // (true) every key press waited for this recognizer to give up first,
        // which read as "backspace often doesn't catch".
        recognizer.delaysTouchesEnded = false
    }

    func attach() {
        guard let host else { return }
        swipeLog.notice("isv-swipe: attach")
        host.swipeSurface.addGestureRecognizer(recognizer)
        host.swipeSurface.layer.addSublayer(trail)
        loadDictionary()
    }

    private func loadDictionary() {
        // Mapped, so this is cheap - but it is still file I/O and it still
        // happens while the user is looking at the keyboard.
        queue.async { [weak self] in
            guard let url = Bundle.main.url(forResource: "isv_swipe",
                                            withExtension: "bin") else {
                swipeLog.error("isv-swipe: isv_swipe.bin NOT in bundle")
                return
            }
            let dict = SwipeDictionary(url: url)
            swipeLog.notice("isv-swipe: dictionary \(dict?.count ?? -1, privacy: .public) forms")
            DispatchQueue.main.async { self?.dictionary = dict }
        }
    }

    // MARK: - Gesture

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        host?.swipeCanBegin ?? false
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard let host else { return false }
        return host.swipeCanStart(at: touch.location(in: host.swipeSurface))
    }

    func gestureRecognizer(_ g: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer)
    -> Bool {
        // The longpress lives on the keys and picks accented letters; it and a
        // swipe are different intentions and must not both fire.
        false
    }

    @objc private func handle(_ g: SwipeGestureRecognizer) {
        guard let host else { return }
        let (_, keyWidth) = host.swipeKeyCentres
        if keyWidth > 0 { recognizer.threshold = keyWidth * 0.55 }

        switch g.state {
        case .began:
            swipeLog.notice("isv-swipe: gesture began")
            // Rebuilding the key rows puts their layers above ours, so the
            // trail is re-raised at the start of every gesture rather than
            // once at setup.
            host.swipeSurface.layer.addSublayer(trail)
            draw(g.points)
        case .changed:
            draw(g.points)
        case .ended:
            let path = g.points
            clearTrail()
            decode(path)
        case .cancelled, .failed:
            clearTrail()
        default:
            break
        }
    }

    private func decode(_ path: [CGPoint]) {
        guard let host else { return }
        guard let dictionary else {
            swipeLog.error("isv-swipe: gesture ended but dictionary is nil")
            return
        }
        let (centres, keyWidth) = host.swipeKeyCentres
        guard keyWidth > 0 else {
            swipeLog.error("isv-swipe: keyWidth 0 - no letter keys measured")
            return
        }

        host.swipeWillDecode()   // may still learn from the previous gesture
        sample = nil
        let lift = touchLift
        let mine = userWordKeys
        queue.async { [weak self] in
            let decoder = SwipeDecoder(dictionary: dictionary,
                                       keyCentres: centres,
                                       keyWidth: keyWidth,
                                       touchLift: lift,
                                       userWords: mine)
            let words = decoder.decode(path: path).map(\.word)
            swipeLog.notice("isv-swipe: \(path.count, privacy: .public) points -> \(words.joined(separator: " "), privacy: .public)")
            #if DEBUG
            GestureFile.append(path: path, centres: centres, keyWidth: keyWidth, words: words, lift: lift)
            #endif
            DispatchQueue.main.async {
                self?.sample = (path.first!, path.last!, centres, keyWidth)
                self?.host?.swipeDidFinish(candidates: words)
            }
        }
    }

    // MARK: - Learning the thumb

    /// The last swiped word was kept (the next word or a space followed it):
    /// compare where the path began and ended with that word's first and last
    /// keys, and move the learned lift a little toward what was measured.
    func learn(from word: String) {
        guard let s = sample else { return }
        sample = nil
        let folded = word.lowercased().folding(options: .diacriticInsensitive, locale: nil)
        guard folded.count >= 2,
              let a = folded.first?.asciiValue, let b = folded.last?.asciiValue,
              (97...122).contains(a), (97...122).contains(b), s.keyWidth > 0
        else { return }
        let first = s.centres[Int(a) - 97], last = s.centres[Int(b) - 97]
        guard first != .zero, last != .zero else { return }
        let measured = Double((first.y + last.y) - (s.start.y + s.end.y)) / 2 / Double(s.keyWidth)
        // One sloppy word must not drag the keyboard around: wild readings are
        // dropped, the rest only nudge, and the result stays in a sane band.
        guard (-0.3...0.9).contains(measured) else { return }
        touchLift = min(0.6, max(0, touchLift + 0.1 * (measured - touchLift)))
        UserDefaults.standard.set(touchLift, forKey: "touchLift")
    }

    /// The word did not come from this gesture (a completion was tapped, or it
    /// was trimmed by hand), so the gesture says nothing about the thumb.
    func forgetSample() { sample = nil }

    /// Words starting with what is being typed by taps. Diacritics fold to
    /// their base key (`č` -> `c`); if the typed part has diacritics, only
    /// words that really start with it are offered.
    /// `known` says whether `typed` itself is a word (wordlist or user's own),
    /// which decides whether the bar offers `＋` to save it.
    func complete(_ typed: String, limit: Int = 3,
                  _ done: @escaping (_ words: [String], _ known: Bool, _ rare: Bool) -> Void) {
        guard let dictionary else { done([], true, false); return }
        let lower = typed.lowercased()
        let folded = lower.folding(options: .diacriticInsensitive, locale: nil)
        guard let keys = SwipeDictionary.keys(of: lower) else { done([], true, false); return }
        let exact = lower != folded
        let mine = userWords
        queue.async {
            var out: [String] = []
            // `rare`: in the list but ranked below the user's own words
            // (`pisanja`). The bar may offer `＋` for it to make it swipeable.
            let freq = dictionary.frequency(of: lower)
            let known = mine.contains(lower) || freq != nil
            let rare = !mine.contains(lower)
                && (freq.map { Double($0) < SwipeDecoder.userWordFreq } ?? false)
            // The user's own words first: they were added because they are used.
            for w in mine where w.hasPrefix(lower) && w != lower && out.count < limit {
                out.append(w)
            }
            for hit in dictionary.completions(prefix: keys, limit: limit) {
                let w = hit.word
                if exact && !w.lowercased().hasPrefix(lower) { continue }
                if w.lowercased() == lower || out.contains(w) { continue }
                out.append(w)
                if out.count == limit { break }
            }
            DispatchQueue.main.async { done(out, known, rare) }
        }
    }

    // MARK: - Trail

    private func draw(_ points: [CGPoint]) {
        guard points.count > 1 else { return }
        let path = UIBezierPath()
        path.move(to: points[0])
        for p in points.dropFirst() { path.addLine(to: p) }
        trail.path = path.cgPath
        trail.opacity = 1
    }

    private func clearTrail() {
        // Fading out rather than vanishing: the trail is the only feedback that
        // the gesture was seen at all, and cutting it dead reads as a dropped
        // input even when the word lands correctly.
        let fade = CABasicAnimation(keyPath: "opacity")
        fade.fromValue = 1
        fade.toValue = 0
        fade.duration = 0.18
        trail.opacity = 0
        trail.add(fade, forKey: "fade")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            if self?.trail.opacity == 0 { self?.trail.path = nil }
        }
    }
}

#if DEBUG
/// Test builds only: every gesture as one JSON line in the extension's own
/// container, so the ranking can be tuned on real thumbs instead of synthetic
/// paths. Never compiled into a Release build. Pull it from the Mac with
/// `xcrun devicectl device copy from --domain-type appDataContainer
///  --domain-identifier com.radoslove.interslavic.keyboard ...`.
enum GestureFile {
    static func append(path: [CGPoint], centres: [CGPoint], keyWidth: CGFloat, words: [String], lift: Double) {
        guard let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        else { return }
        let row: [String: Any] = [
            "t": Date().timeIntervalSince1970,
            "kw": Double(keyWidth),
            "keys": centres.map { [Double($0.x), Double($0.y)] },
            "path": path.map { [Double($0.x), Double($0.y)] },
            "words": words,
            "lift": lift,
        ]
        write(row)
    }

    /// Any other debug event, same file, marked by `kind`.
    static func note(_ kind: String, _ fields: [String: Any]) {
        var row = fields
        row["kind"] = kind
        row["t"] = Date().timeIntervalSince1970
        write(row)
    }

    private static func write(_ row: [String: Any]) {
        guard let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        else { return }
        guard var line = try? JSONSerialization.data(withJSONObject: row) else { return }
        line.append(0x0A)
        let url = dir.appendingPathComponent("gestures.jsonl")
        if let h = try? FileHandle(forWritingTo: url) {
            h.seekToEndOfFile(); h.write(line); try? h.close()
        } else {
            try? line.write(to: url)
        }
    }
}
#endif
