import CoreGraphics
import Foundation

/// Turns a finger path across the keys into ranked words.
///
/// The method is shape matching: a word implies an "ideal" path - the polyline
/// through the centres of the keys that spell it - and the word whose ideal
/// path best fits what the finger actually did wins. Both paths are resampled
/// to the same number of evenly spaced points, so the comparison is of shape
/// and position, not of speed or of how the finger was held.
///
/// Everything is measured in KEY WIDTHS rather than points, which is what lets
/// the same thresholds hold on a 9.7" iPad and on a phone in landscape.
///
/// The decoder never loads the dictionary. `SwipeDictionary` reads the mapped
/// file in place and only walks the buckets whose first and last letters the
/// gesture could plausibly have touched - typically nine of 676.
struct SwipeDecoder {

    struct Candidate {
        let word: String
        let score: Double        // lower is better
    }

    /// How many points both paths are resampled to. Higher sees more detail and
    /// costs linearly; 32 is enough to separate words of ordinary length.
    private static let samples = 32

    /// A key this many key-widths from the path's end is still a plausible
    /// first or last letter. Generous on purpose: fingers overshoot, and a
    /// wrong bucket cannot be recovered later.
    private static let endpointRadius: Double = 1.4

    /// See `decode`: how far real touches sit above the intended key, in key
    /// widths. The starting value; each phone then learns its own thumb
    /// (`SwipeInput.learn`).
    static let defaultTouchLift: Double = 0.35
    /// How hard frequency pulls. 0.03 before; 0.06 took the real-gesture set
    /// from 23/34 to 25/34 top-1 (28 -> 30 top-3) at a cost of 1.6 points on
    /// synthetic glides. Short common words (`se`, `ne`, `kako`) are what a
    /// thumb actually swipes, so real data wins.
    private static let freqWeight: Double = 0.06

    let dictionary: SwipeDictionary
    /// Centre of each letter key, indexed 0..25 for 'a'..'z'. Missing keys are
    /// .zero and are skipped - a layer without letters simply decodes nothing.
    let keyCentres: [CGPoint]
    let keyWidth: CGFloat
    var touchLift: Double = SwipeDecoder.defaultTouchLift
    /// The user's own words (`＋` in the bar), with their folded keys. Scored
    /// like a fairly common word - around `kolega` - because adding one is a
    /// deliberate act; it should win its shape, not every shape.
    var userWords: [(word: String, keys: [UInt8])] = []
    static let userWordFreq: Double = 140

    func decode(path rawPath: [CGPoint], times: [Double] = [], limit: Int = 4) -> [Candidate] {
        guard keyWidth > 0, rawPath.count >= 2 else { return [] }
        // Measured on 34 labelled real gestures on an iPhone 13 (2026-10-07):
        // touches register on average 0.35 key widths ABOVE the key the thumb
        // meant, at both ends of the path. Shifting the path back down moved
        // top-1 from 16/34 to 23/34 on its own - `ne` stopped starting on
        // `h`/`j`, `da` stopped ending on `q`.
        let lift = Double(keyWidth) * touchLift
        let path = Self.resample(rawPath.map { CGPoint(x: $0.x, y: $0.y + lift) },
                                 to: Self.samples)
        guard path.count == Self.samples else { return [] }

        // The first and last letter are read from the slowest sample near each
        // end, not from the end samples themselves (Android 3.6.0-3.6.2).
        let shifted = rawPath.map { CGPoint(x: $0.x, y: $0.y + lift) }
        let starts = nearestKeys(to: Self.anchorPoint(shifted, times: times, atEnd: false))
        let ends = nearestKeys(to: Self.anchorPoint(shifted, times: times, atEnd: true))
        guard !starts.isEmpty, !ends.isEmpty else { return [] }

        // A gesture cannot spell more letters than it had room to cross, and a
        // very long word cannot be drawn in a very short path. Both bounds are
        // loose - they exist to skip obvious nonsense, not to judge.
        let travelled = Self.length(of: path) / Double(keyWidth)
        let maxLetters = max(3, Int(travelled) + 6)

        var best: [(score: Double, offset: Int, length: Int)] = []
        var worstKept = Double.greatestFiniteMagnitude

        for first in starts {
            for last in ends {
                dictionary.forEachEntry(first: first.key, last: last.key) { entry in
                    guard entry.keys.count <= maxLetters else { return }
                    guard let ideal = idealPath(for: entry.keys) else { return }

                    var sum = 0.0
                    for i in 0..<Self.samples {
                        let dx = Double(path[i].x - ideal[i].x)
                        let dy = Double(path[i].y - ideal[i].y)
                        sum += dx * dx + dy * dy
                    }
                    let rms = (sum / Double(Self.samples)).squareRoot() / Double(keyWidth)

                    // Frequency breaks ties between shapes the finger cannot
                    // distinguish anyway. It nudges, it does not decide: a
                    // common word still loses to a clearly better fit.
                    let score = rms - Self.freqWeight * (Double(entry.freq) + 1).squareRoot()
                    guard best.count < limit * 4 || score < worstKept else { return }

                    best.append((score, entry.wordOffset, entry.wordLength))
                    if best.count > limit * 4 {
                        best.sort { $0.score < $1.score }
                        best.removeLast(best.count - limit * 4)
                        worstKept = best.last!.score
                    }
                }
            }
        }

        // User words: few, so simply scored one by one. A negative offset
        // marks them in `best`: -1 is userWords[0], -2 userWords[1], ...
        let startKeys = Set(starts.map(\.key)), endKeys = Set(ends.map(\.key))
        for (i, extra) in userWords.enumerated() {
            guard let f = extra.keys.first, let l = extra.keys.last,
                  startKeys.contains(Int(f)), endKeys.contains(Int(l)),
                  extra.keys.count <= maxLetters,
                  let ideal = extra.keys.withUnsafeBufferPointer({ idealPath(for: $0) })
            else { continue }
            var sum = 0.0
            for j in 0..<Self.samples {
                let dx = Double(path[j].x - ideal[j].x)
                let dy = Double(path[j].y - ideal[j].y)
                sum += dx * dx + dy * dy
            }
            let rms = (sum / Double(Self.samples)).squareRoot() / Double(keyWidth)
            best.append((rms - Self.freqWeight * (Self.userWordFreq + 1).squareRoot(), -1 - i, 0))
        }

        best.sort { $0.score < $1.score }

        // Strings are built only for the survivors - decoding a gesture touches
        // thousands of entries and allocating a String for each one is the
        // difference between instant and sluggish.
        var seen = Set<String>()
        var out: [Candidate] = []
        for hit in best {
            let word = hit.offset < 0
                ? userWords[-1 - hit.offset].word
                : dictionary.word(at: hit.offset, length: hit.length)
            guard seen.insert(word).inserted else { continue }
            out.append(Candidate(word: word, score: hit.score + Self.acronymPenalty(word)))
        }
        // `SSSR` kept beating `se`: an acronym has the frequency of a word but
        // is almost never what a swipe meant. It still wins a clearly better fit.
        out.sort { $0.score < $1.score }
        return Array(out.prefix(limit))
    }

    static func acronymPenalty(_ word: String) -> Double {
        word.filter(\.isUppercase).count >= 2 ? 0.6 : 0
    }

    // MARK: - Anchors

    /// How far back from each end `anchorPoint` looks, in milliseconds.
    static let anchorMs: Double = 120
    /// Moving-average width over the speed curve, as on Android (V_SMOOTH).
    private static let speedSmooth = 5

    /// Smoothed speed per sample, in points per millisecond. Raw deltas are far
    /// too noisy to take a minimum of - one jittery sample reads as a pause.
    static func speedCurve(_ pts: [CGPoint], _ times: [Double]) -> [Double]? {
        let n = pts.count
        guard n >= 2, times.count == n else { return nil }
        var raw = [Double](repeating: 0, count: n)
        for i in 1..<n {
            var dt = times[i] - times[i - 1]
            if dt <= 0 { dt = 8 }          // a batched/duplicate timestamp
            raw[i] = distance(pts[i], pts[i - 1]) / dt
        }
        raw[0] = raw[1]
        let half = speedSmooth / 2
        var out = [Double](repeating: 0, count: n)
        for i in 0..<n {
            let lo = max(0, i - half), hi = min(n, i + half + 1)
            var acc = 0.0
            for j in lo..<hi { acc += raw[j] }
            out[i] = acc / Double(hi - lo)
        }
        return out
    }

    /// Where to read the first and last letter from. NOT the first and last
    /// sample: a thumb decelerates onto the letter it means and carries past
    /// it, so those two are the approach and the overshoot. This returns the
    /// SLOWEST sample within `anchorMs` of the chosen end.
    ///
    /// The window never exceeds a THIRD of the gesture. A quick flick can last
    /// under 240 ms, and with a flat window the two would overlap - the start
    /// anchor reading a sample from the end of the path. Both anchors are hard
    /// gates, so that destroys the result: Android 3.6.1 typed `se` as `resě`
    /// and `pisati` as `porasteš` until 3.6.2 added this clamp.
    ///
    /// ⚠ OFF on iOS (`slowAnchors = false`), measured 2026-11-05 on 21 real
    /// glides from an iPhone 13 replayed both ways: `pisati` top-1 4/13 -> 0/13,
    /// `slovo` 8/8 -> 3/8, `se`/`to` unchanged. iOS sends no touchesMoved while
    /// the finger rests, so the deceleration onto the last letter is never
    /// sampled and the slowest point near the end is the previous TURN (`v` in
    /// `slovo` -> `suprug`, `doč`). The code stays so the replay can measure it
    /// again once the end of the gesture is sampled (e.g. coalesced touches).
    static var slowAnchors = false

    static func anchorPoint(_ pts: [CGPoint], times: [Double], atEnd: Bool) -> CGPoint {
        let n = pts.count
        let edgeIndex = atEnd ? n - 1 : 0
        guard slowAnchors, n >= 3, let v = speedCurve(pts, times) else { return pts[edgeIndex] }
        let edge = times[edgeIndex]
        let span = times[n - 1] - times[0]
        let reach = span > 0 ? min(anchorMs, span / 3) : 0
        var best = edgeIndex
        var bestV = Double.greatestFiniteMagnitude
        let order: [Int] = atEnd ? Array((0..<n).reversed()) : Array(0..<n)
        for i in order {
            if abs(times[i] - edge) > reach { break }
            if v[i] < bestV { bestV = v[i]; best = i }
        }
        return pts[best]
    }

    // MARK: - Geometry

    private func nearestKeys(to point: CGPoint) -> [(key: Int, distance: Double)] {
        let limit = Self.endpointRadius * Double(keyWidth)
        var hits: [(key: Int, distance: Double)] = []
        for (index, centre) in keyCentres.enumerated() where centre != .zero {
            let d = Self.distance(point, centre)
            if d <= limit { hits.append((index, d)) }
        }
        hits.sort { $0.distance < $1.distance }
        // Four is already forgiving; more only adds buckets full of words the
        // finger never went near.
        return Array(hits.prefix(4))
    }

    private func idealPath(for keys: UnsafeBufferPointer<UInt8>) -> [CGPoint]? {
        var points: [CGPoint] = []
        points.reserveCapacity(keys.count)
        for key in keys {
            let centre = keyCentres[Int(key)]
            if centre == .zero { return nil }
            // A finger crossing a doubled letter draws one visit, not two, so
            // the ideal path must not claim two either.
            if points.last != centre { points.append(centre) }
        }
        guard points.count >= 2 else {
            // Single-key words: a dot of a path. Compare against the key twice
            // so resampling has a segment to work with.
            guard let only = points.first else { return nil }
            return Array(repeating: only, count: Self.samples)
        }
        return Self.resample(Self.cutCorners(points), to: Self.samples)
    }

    /// How much of each segment next to an inner corner the ideal path cuts
    /// off (0 = sharp polyline through the key centres). A thumb rounds every
    /// turn, so a sharp ideal penalises exactly the words with corners.
    /// EXPERIMENT, measured by replay before any value ships.
    static var cornerCut: Double = 0
    /// Only turns gentler than this (degrees between the incoming and the
    /// outgoing direction) are rounded. A hairpin (`o-v-o` in `slovo`) is not
    /// a bend the thumb rounds - it has to reach the key to come back.
    static var cornerMaxTurn: Double = 180

    static func cutCorners(_ pts: [CGPoint]) -> [CGPoint] {
        guard cornerCut > 0, pts.count >= 3 else { return pts }
        let c = CGFloat(cornerCut)
        var out: [CGPoint] = [pts[0]]
        for i in 1..<(pts.count - 1) {
            let a = pts[i - 1], p = pts[i], b = pts[i + 1]
            let ux = Double(p.x - a.x), uy = Double(p.y - a.y)
            let vx = Double(b.x - p.x), vy = Double(b.y - p.y)
            let nu = (ux * ux + uy * uy).squareRoot(), nv = (vx * vx + vy * vy).squareRoot()
            let cosT = nu > 0 && nv > 0 ? (ux * vx + uy * vy) / (nu * nv) : 1
            let turn = acos(max(-1, min(1, cosT))) * 180 / .pi
            if turn > cornerMaxTurn { out.append(p); continue }
            out.append(CGPoint(x: p.x + (a.x - p.x) * c, y: p.y + (a.y - p.y) * c))
            out.append(CGPoint(x: p.x + (b.x - p.x) * c, y: p.y + (b.y - p.y) * c))
        }
        out.append(pts[pts.count - 1])
        return out
    }

    /// Even spacing along the polyline, endpoints preserved.
    static func resample(_ points: [CGPoint], to count: Int) -> [CGPoint] {
        guard points.count >= 2, count >= 2 else { return points }
        let total = length(of: points)
        guard total > 0 else { return Array(repeating: points[0], count: count) }

        let step = total / Double(count - 1)
        var out: [CGPoint] = [points[0]]
        var segment = 0
        var walked = 0.0          // distance covered before `segment`
        var target = step

        while out.count < count && segment < points.count - 1 {
            let a = points[segment], b = points[segment + 1]
            let segLength = distance(a, b)
            if segLength <= 0 { segment += 1; continue }

            if walked + segLength >= target {
                let t = (target - walked) / segLength
                out.append(CGPoint(x: a.x + (b.x - a.x) * t,
                                   y: a.y + (b.y - a.y) * t))
                target += step
            } else {
                walked += segLength
                segment += 1
            }
        }
        while out.count < count { out.append(points[points.count - 1]) }
        return out
    }

    static func length(of points: [CGPoint]) -> Double {
        guard points.count >= 2 else { return 0 }
        var total = 0.0
        for i in 1..<points.count { total += distance(points[i - 1], points[i]) }
        return total
    }

    static func distance(_ a: CGPoint, _ b: CGPoint) -> Double {
        let dx = Double(a.x - b.x), dy = Double(a.y - b.y)
        return (dx * dx + dy * dy).squareRoot()
    }
}
