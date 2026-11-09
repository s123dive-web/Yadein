import Foundation

public struct PolishResult: Hashable, Sendable {
    public let title: String
    public let description: String
    public let date: MemoryDate
    public let coverStyle: String
    public let place: String
    public let mood: Mood?
    /// True when a date was found in the text (otherwise `date` is today).
    public let dateDetected: Bool
    public let category: String
}

/// Rule-based, deterministic, 100% offline "Polish" (PolishEngine.kt, ported rule for rule): turns one line such as
/// "goa trip 2024" or "diwali ghar pe 2019" into a titled, dated, illustrated memory card. The same text gives the same
/// card on Android and iPhone.
public enum PolishEngine {
    private struct Category {
        let key: String
        let mood: Mood?
        let keywords: [String]
        let descriptions: [String]
    }

    private static let categories: [Category] = [
        Category(key: "beach", mood: .JOYFUL, keywords: ["beach", "goa", "sea", "samundar", "alibaug", "kerala", "andaman", "coast", "shack", "sunburn", "waves", "ocean", "island"],
                 descriptions: ["Sun, sand and salty hair. %s is the kind of day that keeps the whole year warm.", "Waves, long lunches and nobody checking the time. %s, kept here forever."]),
        Category(key: "mountain", mood: .CALM, keywords: ["trek", "mountain", "hill", "hills", "ghat", "pahad", "lonavala", "mahabaleshwar", "himalaya", "camp", "camping", "valley", "fort", "sinhagad", "rajmachi", "peak"],
                 descriptions: ["Thin air, sore legs and a view that made everyone go quiet. %s, step by step.", "Up early, up high, and every ache worth it at the top. %s stays with you."]),
        Category(key: "travel", mood: .JOYFUL, keywords: ["trip", "travel", "tour", "vacation", "holiday", "safar", "yatra", "picnic", "outing", "road", "roadtrip", "journey", "weekend", "getaway", "visit", "ghoomna"],
                 descriptions: ["New roads, old jokes and too many snacks. %s was a journey worth every kilometre.", "Bags packed, playlist ready, no plan that survived the first hour. %s, happily."]),
        Category(key: "wedding", mood: .CELEBRATION, keywords: ["wedding", "shaadi", "shadi", "lagna", "lagn", "sangeet", "haldi", "mehndi", "mehendi", "engagement", "sagai", "reception", "baraat", "marriage", "nikah", "bride", "groom", "vivah"],
                 descriptions: ["Flowers, music and happy tears. %s — the day two families became one big one.", "Three days of colour and laughter in one memory. %s, with everyone who matters."]),
        Category(key: "birthday", mood: .CELEBRATION, keywords: ["birthday", "bday", "janmdin", "janamdin", "vadhdivas", "cake", "turned", "turns", "candles"],
                 descriptions: ["Cake, candles and a wish nobody said out loud. %s, celebrated the right way.", "Another year, another round of singing badly on purpose. %s is one to keep."]),
        Category(key: "diwali", mood: .CELEBRATION, keywords: ["diwali", "deepavali", "dipavali", "diya", "diyas", "lakshmi", "faral", "phuljhadi", "rangoli", "laxmi", "padwa", "bhaubeej", "bhai dooj"],
                 descriptions: ["Diyas on every step, faral on every plate and the whole house glowing. %s, bright as ever.", "Lights, sweets and new clothes that still smelled of the shop. %s at home."]),
        Category(key: "holi", mood: .FUNNY, keywords: ["holi", "rang", "colours", "colors", "gulal", "pichkari", "dhuleti", "rangpanchami"],
                 descriptions: ["Nobody recognised anybody by noon. %s, in every colour we had.", "Colour in our hair for a week and no regrets. %s, loud and happy."]),
        Category(key: "festival", mood: .CELEBRATION, keywords: ["ganpati", "ganesh", "ganeshotsav", "navratri", "dussehra", "dasara", "durga", "puja", "pooja", "eid", "christmas", "xmas", "pongal", "onam", "gudi", "padwa", "sankranti", "makar", "rakhi", "raksha", "janmashtami", "dahi", "handi", "festival", "tyohar", "sanmelan", "garba", "dandiya", "aarti", "mela"],
                 descriptions: ["Drums, sweets and the whole family in new clothes. %s felt like it always did.", "The streets were loud and the house was louder. %s, celebrated together."]),
        Category(key: "school", mood: .NOSTALGIC, keywords: ["school", "shala", "class", "exam", "exams", "teacher", "10th", "12th", "ssc", "hsc", "board", "result", "results", "concert", "annual day", "sports day", "tuition", "uniform"],
                 descriptions: ["Ink-stained fingers and a bell that ruled the day. %s, from the school years.", "Friends on the last bench and a teacher who saw everything. %s is still clear."]),
        Category(key: "college", mood: .NOSTALGIC, keywords: ["college", "campus", "hostel", "fest", "farewell", "graduation", "graduated", "convocation", "degree", "semester", "placement", "canteen", "university", "engineering", "mba"],
                 descriptions: ["Borrowed notes, canteen chai and plans for the whole world. %s, from the college days.", "The years that felt like they would never end, and then did. %s, remembered."]),
        Category(key: "home", mood: .CALM, keywords: ["home", "ghar", "house", "terrace", "balcony", "sunday", "evening", "morning", "garden", "shifting", "moved", "new house", "gruhapravesh", "griha", "vastu", "family"],
                 descriptions: ["Nothing big happened and that was the point. %s, at home, together.", "The sounds of the house doing ordinary things. %s, exactly as it should be."]),
        Category(key: "food", mood: .JOYFUL, keywords: ["food", "dinner", "lunch", "breakfast", "chai", "coffee", "biryani", "restaurant", "cafe", "dhaba", "modak", "puran poli", "thali", "pav bhaji", "vada pav", "misal", "recipe", "cooking", "baked", "bake", "cake", "khana", "jevan", "sweets", "mithai"],
                 descriptions: ["Too much on the plate and still room for one more bite. %s, made with love.", "Recipes nobody wrote down, served on plates nobody matched. %s, delicious."]),
        Category(key: "baby", mood: .PROUD, keywords: ["baby", "born", "newborn", "hospital", "first word", "first step", "first steps", "naming", "barsa", "naamkaran", "pregnant", "delivery", "toddler", "walked", "crawled"],
                 descriptions: ["Tiny fingers, big yawns and a house that changed overnight. %s, the beginning.", "Everyone whispered and nobody slept. %s, the smallest big day."]),
        Category(key: "music", mood: .JOYFUL, keywords: ["music", "concert", "song", "songs", "gaana", "dance", "dancing", "garba", "band", "guitar", "tabla", "singing", "karaoke", "playlist", "performance", "stage"],
                 descriptions: ["Loud, off-key and perfect. %s, with the volume all the way up.", "The song that still brings the day back. %s, on repeat."]),
        Category(key: "sports", mood: .PROUD, keywords: ["cricket", "match", "football", "badminton", "kabaddi", "marathon", "run", "race", "tournament", "ipl", "world cup", "gym", "cycling", "swim", "swimming", "khel", "won", "winner", "medal", "trophy"],
                 descriptions: ["Sweat, shouting and a scoreline nobody will forget. %s, played like it mattered.", "One more over, one more lap, one more go. %s, for the win."]),
        Category(key: "rain", mood: .CALM, keywords: ["rain", "rains", "monsoon", "barish", "paus", "pavsala", "bhajiya", "bhajji", "pakoda", "umbrella", "flood", "cloudy", "storm"],
                 descriptions: ["Grey skies, hot bhajiyas and nowhere to be. %s, the monsoon way.", "The smell of first rain and everyone at the window. %s, soaked and smiling."]),
        Category(key: "winter", mood: .CALM, keywords: ["snow", "winter", "manali", "kashmir", "shimla", "ladakh", "cold", "sweater", "thandi", "fog", "december", "bonfire", "campfire"],
                 descriptions: ["Cold hands, warm cups and breath in the air. %s, bundled up together.", "A bonfire, a blanket and stories that got longer every year. %s, in the cold."]),
    ]

    private static let allKeywords: Set<String> = Set(categories.flatMap(\.keywords))

    private static let monthNames: [String: Int] = {
        var m: [String: Int] = [:]
        for i in 0..<12 {
            m[Months.full[i].lowercased()] = i + 1
            m[Months.short[i].lowercased()] = i + 1
        }
        m["sept"] = 9
        return m
    }()

    private static let seasons: [String: (month: Int, label: String)] = [
        "summer": (5, "Summer"), "garmi": (5, "Summer"), "unhala": (5, "Summer"),
        "winter": (12, "Winter"), "thandi": (12, "Winter"), "hivala": (12, "Winter"),
        "monsoon": (7, "Monsoon"), "rains": (7, "Monsoon"), "barish": (7, "Monsoon"), "pavsala": (7, "Monsoon"),
        "spring": (3, "Spring"), "autumn": (10, "Autumn"),
    ]

    private static let knownPlaces = [
        "goa", "pune", "mumbai", "nashik", "nagpur", "delhi", "bangalore", "bengaluru", "hyderabad", "chennai", "kolkata", "jaipur", "udaipur",
        "lonavala", "mahabaleshwar", "alibaug", "kerala", "manali", "shimla", "kashmir", "ladakh", "rishikesh", "varanasi", "kashi", "agra",
        "ooty", "munnar", "coorg", "hampi", "mysore", "mysuru", "kolhapur", "satara", "ratnagiri", "ganpatipule", "konkan", "shirdi", "lavasa",
        "matheran", "panchgani", "andaman", "dubai", "london", "singapore", "thailand", "bali", "paris", "sinhagad", "rajmachi", "tarkarli",
    ]

    private static let stopWords: Set<String> = ["ka", "ki", "ke", "pe", "par", "mein", "me", "se", "aur", "and", "the", "a", "an", "at", "in", "on", "of", "with", "wala", "wali", "vala", "cha", "chi", "che", "la", "madhe", "ani", "to", "for", "our", "my", "humara", "hamara", "apna", "sath", "saath"]

    private static let whitespace = Rx("\\s+")
    private static let tokenSplit = Rx("[^\\p{L}\\p{N}']+")
    private static let getaway = Rx("\\b(Trip|Tour|Vacation|Holiday|Safar|Yatra)\\b")
    private static let numericDate = Rx("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b")
    private static let placeAfter = Rx("\\b(?:in|at|near|@)\\s+([\\p{L}]{3,}(?:\\s[\\p{L}]{3,})?)")

    public static func polish(_ input: String, today: LocalDate = .today(), locale: Locale = .current) -> PolishResult {
        let raw = whitespace.replaceAll(input.trimmingCharacters(in: .whitespacesAndNewlines), with: " ")
        let lower = raw.lowercased(with: locale)
        let tokens = tokenSplit.split(lower).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

        // ---- date ----
        let parsed = parseDate(tokens: tokens, lower: lower, today: today)
        let date = parsed.date
        let detected = parsed.detected

        // ---- category: the first category with the most keyword hits ----
        var category: Category?
        var best = 0
        for c in categories {
            let hits = c.keywords.filter { lower.contains($0) }.count
            if hits > best {
                best = hits
                category = c
            }
        }
        let hash = lower.javaHashCode
        let plains = ["plum", "amber", "teal", "rose", "indigo"]
        let categoryKey = category?.key ?? "plain_\(plains[kotlinAbsMod(hash, 5)])"

        // ---- place ----
        let place = detectPlace(lower: lower, tokens: tokens)

        // ---- title ----
        let coreTokens = whitespace.split(raw).filter { tok in
            let t = trimNonAlnum(tok.lowercased(with: locale))
            return !t.isEmpty && !parsed.consumed.contains(t) && !stopWords.contains(t)
        }
        var core = trimChars(coreTokens.map(titleCase).joined(separator: " "), [",", " ", "-"])
        if core.trimmingCharacters(in: .whitespaces).isEmpty {
            core = category.map { titleCase($0.key) } ?? "A Day To Remember"
        }
        if let key = category?.key, key == "beach" || key == "travel" {
            core = getaway.replaceAll(core, with: "Getaway")
        }
        let title = detected ? "\(core), \(datePhrase(date, locale))" : core

        // ---- description ----
        let beforeComma = core.components(separatedBy: ",").first ?? core
        let subject = detected ? "\(beforeComma) in \(datePhrase(date, locale))" : beforeComma
        let descriptions = category?.descriptions ?? [
            "A small moment, written down so it doesn't slip away. %s, kept safe.",
            "Not every memory needs a photo. %s, in a few honest words.",
        ]
        let description = descriptions[kotlinAbsMod(hash, descriptions.count)].replacingOccurrences(of: "%s", with: subject)

        return PolishResult(title: title, description: description, date: date, coverStyle: categoryKey, place: place,
                            mood: category?.mood, dateDetected: detected, category: category?.key ?? "plain")
    }

    private static func datePhrase(_ date: MemoryDate, _ locale: Locale) -> String {
        switch date.kind {
        case .EXACT, .MONTH: return Dates.label(date, locale: locale).text
        case .YEAR: return String(Dates.start(date).year)
        case .FUZZY: return date.label ?? String(Dates.start(date).year)
        }
    }

    static func titleCase(_ word: String) -> String {
        if word.count <= 2 && word.allSatisfy(\.isLetter) && word.uppercased() == word { return word }
        let lower = word.lowercased()
        guard let first = lower.first else { return lower }
        return String(first).uppercased() + lower.dropFirst()
    }

    private static func trimNonAlnum(_ s: String) -> String {
        var chars = Substring(s)
        while let f = chars.first, !(f.isLetter || f.isNumber) { chars = chars.dropFirst() }
        while let l = chars.last, !(l.isLetter || l.isNumber) { chars = chars.dropLast() }
        return String(chars)
    }

    private static func trimChars(_ s: String, _ set: Set<Character>) -> String {
        var chars = Substring(s)
        while let f = chars.first, set.contains(f) { chars = chars.dropFirst() }
        while let l = chars.last, set.contains(l) { chars = chars.dropLast() }
        return String(chars)
    }

    private struct ParsedDate {
        let date: MemoryDate
        let consumed: Set<String>
        let detected: Bool
    }

    private static func isDigits(_ s: String) -> Bool { !s.isEmpty && s.allSatisfy { $0.isASCII && $0.isNumber } }

    private static func parseDate(tokens: [String], lower: String, today: LocalDate) -> ParsedDate {
        var consumed = Set<String>()
        // dd/mm/yyyy or dd-mm-yyyy
        if let m = numericDate.find(lower), let d = Int(m[1]), let mo = Int(m[2]), let y = Int(m[3]),
           let ld = LocalDate(y, mo, d) {
            consumed.insert(m[0])
            return ParsedDate(date: Dates.exact(ld), consumed: consumed, detected: true)
        }
        if tokens.contains("today") || tokens.contains("aaj") {
            consumed.formUnion(["today", "aaj"])
            return ParsedDate(date: Dates.exact(today), consumed: consumed, detected: true)
        }
        if tokens.contains("yesterday") || tokens.contains("kal") {
            consumed.formUnion(["yesterday", "kal"])
            return ParsedDate(date: Dates.exact(today.minusDays(1)), consumed: consumed, detected: true)
        }
        let year = tokens.first { $0.count == 4 && isDigits($0) && Dates.isValidYear(Int($0)!) }.flatMap { Int($0) }
        let yearRelative: Int? = (lower.contains("last year") || lower.contains("pichle saal")) ? today.year - 1 : nil
        let y = year ?? yearRelative
        if let year { consumed.insert(String(year)) }
        if yearRelative != nil { consumed.formUnion(["last", "year", "pichle", "saal"]) }
        let monthToken = tokens.first { monthNames[$0] != nil }
        let month = monthToken.flatMap { monthNames[$0] }
        let seasonToken = tokens.first { seasons[$0] != nil }
        let dayNumber = tokens.first { $0.count <= 2 && isDigits($0) && (1...31).contains(Int($0)!) }.flatMap { Int($0) }

        if let y {
            if let month, let monthToken {
                consumed.insert(monthToken)
                if let dayNumber {
                    consumed.insert(String(dayNumber))
                    if let ld = LocalDate(y, month, dayNumber) {
                        return ParsedDate(date: Dates.exact(ld), consumed: consumed, detected: true)
                    }
                }
                return ParsedDate(date: Dates.month(YearMonth(year: y, month: month)), consumed: consumed, detected: true)
            }
            if let seasonToken, let season = seasons[seasonToken] {
                consumed.insert(seasonToken)
                return ParsedDate(date: Dates.fuzzy("\(season.label) \(y)", anchorYear: y, anchorMonth: season.month),
                                  consumed: consumed, detected: true)
            }
            return ParsedDate(date: Dates.year(y), consumed: consumed, detected: true)
        }
        if let month, let monthToken {
            // Month without a year: assume the most recent occurrence.
            consumed.insert(monthToken)
            var ym = YearMonth(year: today.year, month: month)
            if ym > today.yearMonth { ym = ym.minusYears(1) }
            if let dayNumber {
                consumed.insert(String(dayNumber))
                if let ld = LocalDate(ym.year, month, dayNumber) {
                    return ParsedDate(date: Dates.exact(ld), consumed: consumed, detected: true)
                }
            }
            return ParsedDate(date: Dates.month(ym), consumed: consumed, detected: true)
        }
        return ParsedDate(date: Dates.exact(today), consumed: consumed, detected: false)
    }

    private static func detectPlace(lower: String, tokens: [String]) -> String {
        if let p = knownPlaces.first(where: { tokens.contains($0) }) { return titleCase(p) }
        if let m = placeAfter.find(lower) {
            let words = m[1].split(separator: " ").map(String.init)
                .filter { !stopWords.contains($0) && monthNames[$0] == nil && seasons[$0] == nil }
            if !words.isEmpty && !words.contains(where: { allKeywords.contains($0) }) {
                return words.map(titleCase).joined(separator: " ")
            }
        }
        return ""
    }
}
