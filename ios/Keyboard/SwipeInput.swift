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

        queue.async { [weak self] in
            let decoder = SwipeDecoder(dictionary: dictionary,
                                       keyCentres: centres,
                                       keyWidth: keyWidth)
            let words = decoder.decode(path: path).map(\.word)
            swipeLog.notice("isv-swipe: \(path.count, privacy: .public) points -> \(words.joined(separator: " "), privacy: .public)")
            DispatchQueue.main.async { self?.host?.swipeDidFinish(candidates: words) }
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
