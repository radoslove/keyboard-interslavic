import Foundation

/// The wordlist, read where it lies.
///
/// 248 845 forms, the full Android lexicon with nothing pruned. It is mapped,
/// not parsed: a keyboard extension is killed for holding memory, and building
/// 248 845 Swift `String`s at launch would both blow that budget and make the
/// keyboard appear a second after the field is tapped. Mapped, the file costs
/// almost no resident memory and the pages a gesture touches are the only ones
/// the system ever reads.
///
/// Written by `build_swipe_dictionary.py`, which documents the layout.
final class SwipeDictionary {

    struct Entry {
        let freq: UInt8
        /// Folded key indices, 0..25. Valid only inside the enumeration.
        let keys: UnsafeBufferPointer<UInt8>
        /// Where the display form sits in the file, so the decoder can defer
        /// building a String until a word is actually worth showing.
        let wordOffset: Int
        let wordLength: Int
    }

    private static let magic = Array("ISVSWIPE".utf8)
    private static let bucketCount = 676

    private let data: Data
    private let blobStart: Int
    private let offsets: [Int]      // bucketCount + 1 byte offsets into the blob
    let count: Int

    init?(url: URL) {
        guard let data = try? Data(contentsOf: url, options: .mappedIfSafe),
              data.count > 8 + 2 + 4 + (Self.bucketCount + 1) * 4
        else { return nil }

        guard Array(data.prefix(8)) == Self.magic else { return nil }

        var cursor = 8
        let version = data.readUInt16(at: cursor); cursor += 2
        guard version == 1 else { return nil }
        self.count = Int(data.readUInt32(at: cursor)); cursor += 4

        var offsets: [Int] = []
        offsets.reserveCapacity(Self.bucketCount + 1)
        for _ in 0...Self.bucketCount {
            offsets.append(Int(data.readUInt32(at: cursor)))
            cursor += 4
        }
        self.offsets = offsets
        self.blobStart = cursor
        self.data = data
    }

    /// Walks one bucket. The entries are already ordered by descending
    /// frequency, so a caller that wants to stop early may.
    func forEachEntry(first: Int, last: Int, _ body: (Entry) -> Void) {
        guard first >= 0, first < 26, last >= 0, last < 26 else { return }
        let bucket = first * 26 + last
        let start = blobStart + offsets[bucket]
        let end = blobStart + offsets[bucket + 1]
        guard end > start, end <= data.count else { return }

        data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            guard let base = raw.baseAddress else { return }
            let bytes = base.assumingMemoryBound(to: UInt8.self)
            var p = start
            while p + 3 <= end {
                let freq = bytes[p]
                let keyLen = Int(bytes[p + 1])
                let wordLen = Int(bytes[p + 2])
                let keysAt = p + 3
                let wordAt = keysAt + keyLen
                let next = wordAt + wordLen
                if next > end { break }
                body(Entry(freq: freq,
                           keys: UnsafeBufferPointer(start: bytes + keysAt, count: keyLen),
                           wordOffset: wordAt,
                           wordLength: wordLen))
                p = next
            }
        }
    }

    /// The most frequent words starting with `prefix` (folded key indices),
    /// for the bar while typing by taps. Scans the 26 buckets of the first
    /// letter - a few thousand entries, well under a frame.
    func completions(prefix: [Int], limit: Int) -> [(freq: UInt8, word: String)] {
        guard let first = prefix.first else { return [] }
        var best: [(freq: UInt8, offset: Int, length: Int)] = []
        for last in 0..<26 {
            forEachEntry(first: first, last: last) { e in
                guard e.keys.count >= prefix.count else { return }
                for i in 1..<prefix.count where Int(e.keys[i]) != prefix[i] { return }
                if best.count >= limit * 4, let worst = best.last, e.freq <= worst.freq { return }
                best.append((e.freq, e.wordOffset, e.wordLength))
                best.sort { $0.freq > $1.freq }
                if best.count > limit * 4 { best.removeLast() }
            }
        }
        return best.map { ($0.freq, word(at: $0.offset, length: $0.length)) }
    }

    /// Folded key indices of a word, or nil if it has a letter no key types.
    static func keys(of word: String) -> [Int]? {
        let folded = word.lowercased().folding(options: .diacriticInsensitive, locale: nil)
        let keys = folded.unicodeScalars.compactMap { u -> Int? in
            let v = Int(u.value) - 97
            return (0..<26).contains(v) ? v : nil
        }
        return keys.count == folded.unicodeScalars.count && !keys.isEmpty ? keys : nil
    }

    /// Whether this exact form (ignoring case) is in the wordlist.
    func contains(_ word: String) -> Bool {
        guard let k = Self.keys(of: word), let first = k.first, let last = k.last else { return false }
        let lower = word.lowercased()
        var found = false
        forEachEntry(first: first, last: last) { e in
            guard !found, e.keys.count == k.count else { return }
            for i in 0..<k.count where Int(e.keys[i]) != k[i] { return }
            if self.word(at: e.wordOffset, length: e.wordLength).lowercased() == lower { found = true }
        }
        return found
    }

    func word(at offset: Int, length: Int) -> String {
        guard offset >= 0, length > 0, offset + length <= data.count else { return "" }
        return String(decoding: data[offset..<(offset + length)], as: UTF8.self)
    }
}

private extension Data {
    func readUInt16(at index: Int) -> UInt16 {
        UInt16(self[index]) | UInt16(self[index + 1]) << 8
    }

    func readUInt32(at index: Int) -> UInt32 {
        UInt32(self[index]) | UInt32(self[index + 1]) << 8
            | UInt32(self[index + 2]) << 16 | UInt32(self[index + 3]) << 24
    }
}
