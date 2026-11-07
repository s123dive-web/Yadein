import Foundation

public enum RecapTheme: String, Codable, CaseIterable, Sendable { case WARM, MINIMAL, FESTIVE, CLASSIC_FILM }

/// Target length presets; the planner trims or pads segment durations to land near the target.
public enum RecapLength: String, Codable, CaseIterable, Sendable {
    case SHORT, MEDIUM, LONG

    public var targetSeconds: Int {
        switch self {
        case .SHORT: return 30
        case .MEDIUM: return 60
        case .LONG: return 120
        }
    }
}

public let reelWidth = 1080
public let reelHeight = 1920

/// Normalised point (0..1) of the source photo at the centre of the frame, and zoom.
public struct Framing: Hashable, Codable, Sendable {
    public let cx: Float
    public let cy: Float
    public let zoom: Float

    public init(cx: Float, cy: Float, zoom: Float) {
        self.cx = cx
        self.cy = cy
        self.zoom = zoom
    }
}

public enum RecapSegment: Hashable, Codable, Sendable {
    case titleCard(title: String, subtitle: String, durationMs: Int64, isOutro: Bool)
    case photo(mediaId: Int64, durationMs: Int64, start: Framing, end: Framing, caption: String?, dateLabel: String?)
    case clip(mediaId: Int64, startMs: Int64, endMs: Int64, caption: String?, dateLabel: String?)

    public var durationMs: Int64 {
        switch self {
        case let .titleCard(_, _, d, _): return d
        case let .photo(_, d, _, _, _, _): return d
        case let .clip(_, s, e, _, _): return e - s
        }
    }

    public var mediaId: Int64? {
        switch self {
        case .titleCard: return nil
        case let .photo(id, _, _, _, _, _): return id
        case let .clip(id, _, _, _, _): return id
        }
    }
}

public struct RecapPlan: Hashable, Codable, Sendable {
    public let theme: RecapTheme
    public let segments: [RecapSegment]
    /// Vertical 9:16 reel (Instagram Reels / YouTube Shorts / WhatsApp status size).
    public var width: Int = reelWidth
    public var height: Int = reelHeight

    public var totalDurationMs: Int64 { segments.reduce(0) { $0 + $1.durationMs } }
    public var mediaCount: Int { segments.filter { $0.mediaId != nil }.count }
}

/// Deterministic storyboard builder (RecapPlanner.kt): chronological order, Ken Burns pan/zoom on photos, trimmed clips
/// from videos, a caption on the first item of each memory, intro and outro cards.
public enum RecapPlanner {
    public static let introMs: Int64 = 3_000
    public static let outroMs: Int64 = 3_500
    public static let textCardMs: Int64 = 2_500
    public static let minPhotoMs: Int64 = 1_500
    public static let maxPhotoMs: Int64 = 4_500
    public static let defaultPhotoMs: Int64 = 3_000
    public static let maxClipMs: Int64 = 6_000
    public static let minClipMs: Int64 = 2_000
    /// Most photos and clips one reel can hold.
    public static let maxSelectedItems = 150

    public static func defaultItemCount(_ length: RecapLength) -> Int {
        switch length {
        case .SHORT: return 20
        case .MEDIUM: return 40
        case .LONG: return 80
        }
    }

    /// Media of `memory` that can go into a reel, in album order.
    public static func usableMedia(_ memory: MemoryDetail, includeVideos: Bool) -> [Media] {
        memory.media.filter { includeVideos || $0.kind == .PHOTO }
            .stableSorted { ($0.position, $0.id) < ($1.position, $1.id) }
    }

    private static func ordered(_ memories: [MemoryDetail]) -> [MemoryDetail] {
        memories.stableSorted { ($0.memory.date.startEpochDay, $0.memory.id) < ($1.memory.date.startEpochDay, $1.memory.id) }
    }

    /// Picks up to `maxItems` photos/clips across `memories` fairly (water-filling), evenly spaced within an album.
    /// Returned in reel order.
    public static func defaultSelection(_ memories: [MemoryDetail], includeVideos: Bool, maxItems: Int) -> [Int64] {
        let albums = ordered(memories).map { usableMedia($0, includeVideos: includeVideos) }.filter { !$0.isEmpty }
        let total = albums.reduce(0) { $0 + $1.count }
        var picked: [Int64] = []
        var seen = Set<Int64>()
        func add(_ id: Int64) { if seen.insert(id).inserted { picked.append(id) } }
        if total <= maxItems {
            albums.joined().forEach { add($0.id) }
            return picked
        }
        var quota = [Int](repeating: 0, count: albums.count)
        var left = maxItems
        while left > 0 {
            var gave = false
            for i in albums.indices {
                if left == 0 { break }
                if quota[i] < albums[i].count {
                    quota[i] += 1
                    left -= 1
                    gave = true
                }
            }
            if !gave { break }
        }
        for (i, album) in albums.enumerated() { evenlySpaced(album, quota[i]).forEach { add($0.id) } }
        return picked
    }

    private static func evenlySpaced<T: Hashable>(_ items: [T], _ count: Int) -> [T] {
        if count <= 0 { return [] }
        if count >= items.count { return items }
        if count == 1 { return [items[items.count / 2]] }
        var seen = Set<T>()
        return (0..<count).map { i in items[Int(Int64(i) * Int64(items.count - 1) / Int64(count - 1))] }
            .filter { seen.insert($0).inserted }
    }

    /// Rounds a photo duration to whole beats (at least two) so cuts fall on the music; unchanged without a beat.
    public static func snapToBeat(_ ms: Int64, beatMs: Int64?) -> Int64 {
        guard let beatMs, beatMs > 0, ms > 0 else { return ms }
        let beats = max((ms + beatMs / 2) / beatMs, 2)
        return beats * beatMs
    }

    /// - Parameter selectedMediaIds: the photos and clips to use; nil picks them with `defaultSelection`.
    ///   A memory with media but nothing selected is left out; memories with no media become text cards.
    public static func plan(_ memories: [MemoryDetail], theme: RecapTheme, length: RecapLength, title: String,
                            subtitle: String, outroText: String, dateLabel: (MemoryDetail) -> String,
                            includeVideos: Bool = true, selectedMediaIds: Set<Int64>? = nil,
                            outroSubtitle: String = "", beatMs: Int64? = nil) -> RecapPlan {
        let ordered = ordered(memories)
        let selection = selectedMediaIds ?? Set(defaultSelection(ordered, includeVideos: includeVideos, maxItems: defaultItemCount(length)))
        var items: [RecapSegment] = []
        var seed: Int64 = 17
        func nextRandom() -> Float {
            seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
            return Float((UInt64(bitPattern: seed) >> 33) % 1000) / 1000
        }

        var merged: [(MemoryDetail, Media?)] = []
        for m in ordered {
            if m.media.isEmpty {
                merged.append((m, nil))
                continue
            }
            usableMedia(m, includeVideos: includeVideos).filter { selection.contains($0.id) }.forEach { merged.append((m, $0)) }
        }
        let textCards = Int64(merged.filter { $0.1 == nil }.count)
        let clipCount = Int64(merged.filter { $0.1?.kind == .VIDEO }.count)
        let photoCount = Int64(merged.filter { $0.1?.kind == .PHOTO }.count)

        let targetMs = Int64(length.targetSeconds) * 1000 - introMs - outroMs
        let mediaBudget = max(0, targetMs - textCards * textCardMs)
        let clipMs: Int64 = clipCount == 0 ? 0 : min(maxClipMs, max(minClipMs, mediaBudget / max(1, photoCount + clipCount * 2) * 2))
        let rawPhotoMs: Int64 = photoCount == 0 ? 0 : min(max((mediaBudget - clipMs * clipCount) / photoCount, minPhotoMs), maxPhotoMs)
        let photoMs = snapToBeat(rawPhotoMs, beatMs: beatMs)

        items.append(.titleCard(title: title, subtitle: subtitle, durationMs: introMs, isOutro: false))
        var lastMemoryId: Int64 = -1
        for (m, item) in merged {
            let firstOfMemory = m.memory.id != lastMemoryId
            lastMemoryId = m.memory.id
            let caption = firstOfMemory ? m.memory.title : nil
            let date = firstOfMemory ? dateLabel(m) : nil
            guard let item else {
                items.append(.titleCard(title: m.memory.title, subtitle: dateLabel(m), durationMs: textCardMs, isOutro: false))
                continue
            }
            if item.kind == .VIDEO {
                let total = item.durationMs ?? maxClipMs
                let len = min(total, max(clipMs, minClipMs))
                // Start a little in, so we skip the shaky first frames of a clip.
                let start: Int64 = total > len + 1_000 ? 500 : 0
                items.append(.clip(mediaId: item.id, startMs: start, endMs: start + len, caption: caption, dateLabel: date))
            } else {
                let zoomIn = nextRandom() > 0.5
                let cx = 0.5 + (nextRandom() - 0.5) * 0.2
                let cy = 0.5 + (nextRandom() - 0.5) * 0.2
                let a = Framing(cx: cx, cy: cy, zoom: zoomIn ? 1.0 : 1.18)
                let b = Framing(cx: 1 - cx, cy: 1 - cy, zoom: zoomIn ? 1.18 : 1.0)
                items.append(.photo(mediaId: item.id, durationMs: photoMs == 0 ? defaultPhotoMs : photoMs, start: a, end: b,
                                    caption: caption, dateLabel: date))
            }
        }
        items.append(.titleCard(title: outroText, subtitle: outroSubtitle, durationMs: outroMs, isOutro: true))
        return RecapPlan(theme: theme, segments: items)
    }
}

/// The three reels Yaadein keeps ready for the user (AutoReelPicker.kt).
public enum AutoReelSlot: String, Codable, CaseIterable, Sendable {
    case RECENT, PERSON, THROWBACK
    public var key: String { rawValue.lowercased() }
}

public enum AutoReelVariant: String, Codable, Sendable {
    case RECENT_DAYS, LATEST, PERSON, EVERYTHING, THIS_MONTH_YEARS_AGO, THROUGH_THE_YEARS
}

public struct AutoReelPick: Hashable, Sendable {
    public let slot: AutoReelSlot
    public let variant: AutoReelVariant
    public let memories: [MemoryDetail]
    public let mediaIds: Set<Int64>
    public let person: Person?
    public let theme: RecapTheme

    /// Changes only when the photos (or the reel's kind) change, so an unchanged reel is not rendered again.
    public var signature: String {
        let s = "\(variant.rawValue):\(person?.id ?? 0):\(mediaIds.sorted().map(String.init).joined(separator: ","))"
        return String(UInt32(bitPattern: s.javaHashCode), radix: 16)
    }
}

public enum AutoReelPicker {
    public static let recentDays: Int64 = 60
    /// Photos per automatic reel: a short (~30 s) reel.
    public static let itemsPerReel = 15
    private static let latestMemories = 10
    private static let maxYears = 12

    public static func pick(_ memories: [MemoryDetail], today: LocalDate) -> [AutoReelPick] {
        let withPhotos = memories.filter { m in m.media.contains { $0.kind == .PHOTO } }
            .stableSorted { ($0.memory.date.startEpochDay, $0.memory.id) < ($1.memory.date.startEpochDay, $1.memory.id) }
        if withPhotos.isEmpty { return [] }
        let todayDay = today.epochDay
        var picks: [AutoReelPick] = []

        @discardableResult
        func add(_ slot: AutoReelSlot, _ variant: AutoReelVariant, _ chosen: [MemoryDetail], _ theme: RecapTheme,
                 person: Person? = nil, seed: Int64 = 0) -> Bool {
            if chosen.isEmpty { return false }
            // A seed picks a different (but repeatable) set of photos from big albums, so overlapping reels differ.
            let pool = seed == 0 ? chosen : chosen.map { m -> MemoryDetail in
                var copy = m
                copy.media = m.media.kotlinShuffled(seed: seed &* 31 &+ m.memory.id).enumerated().map { i, md in
                    var c = md
                    c.position = i
                    return c
                }
                return copy
            }
            let ids = Set(RecapPlanner.defaultSelection(pool, includeVideos: false, maxItems: itemsPerReel))
            if ids.isEmpty || picks.contains(where: { $0.mediaIds == ids }) { return false }
            picks.append(AutoReelPick(slot: slot, variant: variant, memories: chosen, mediaIds: ids, person: person, theme: theme))
            return true
        }

        // 1. Recent moments.
        let recent = withPhotos.filter { (0...recentDays).contains(todayDay - $0.memory.date.startEpochDay) }
        if recent.count < 2 || !add(.RECENT, .RECENT_DAYS, recent, .WARM) {
            add(.RECENT, .LATEST, Array(withPhotos.suffix(latestMemories)), .WARM)
        }

        // 2. The person who appears most, else a sample of everything.
        var counts: [Int64: Int] = [:]
        for m in withPhotos { for p in m.people { counts[p.id, default: 0] += 1 } }
        let top = counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }.first
        let person: Person? = top.flatMap { e in withPhotos.lazy.compactMap { m in m.people.first { $0.id == e.key } }.first }
        var personDone = false
        if let person {
            personDone = add(.PERSON, .PERSON, withPhotos.filter { m in m.people.contains { $0.id == person.id } }, .FESTIVE, person: person)
        }
        if !personDone { add(.PERSON, .EVERYTHING, withPhotos, .FESTIVE, seed: 1) }

        // 3. Throwback: this month in earlier years, else one memory from each year.
        let dated = withPhotos.filter { $0.memory.date.kind == .EXACT || $0.memory.date.kind == .MONTH }
        let thisMonth = dated.filter {
            let d = LocalDate(epochDay: $0.memory.date.startEpochDay)
            return d.month == today.month && d.year < today.year
        }
        if thisMonth.isEmpty || !add(.THROWBACK, .THIS_MONTH_YEARS_AGO, thisMonth, .CLASSIC_FILM) {
            var firstPerYear: [Int: MemoryDetail] = [:]
            for m in withPhotos {
                let y = LocalDate(epochDay: m.memory.date.startEpochDay).year
                if firstPerYear[y] == nil { firstPerYear[y] = m }
            }
            let perYear = Array(firstPerYear.keys.sorted().map { firstPerYear[$0]! }.suffix(maxYears))
            if perYear.count >= 2 { add(.THROWBACK, .THROUGH_THE_YEARS, perYear, .CLASSIC_FILM, seed: 2) }
        }
        return picks
    }
}
