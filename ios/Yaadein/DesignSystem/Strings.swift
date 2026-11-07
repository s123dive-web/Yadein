import Foundation
import YaadeinDomain

/// Localised text from the tables generated from Android's strings.xml (same keys as `R.string.*`).
/// `L("lock_title")`, `L("faces_is_this", name)`.
func L(_ key: String, _ args: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    if args.isEmpty { return format.replacingOccurrences(of: "%%", with: "%") }
    return String(format: format, locale: Locale.current, arguments: args)
}

/// Plurals (`R.plurals.*`): the count selects the form and is also argument 1.
func LP(_ key: String, _ count: Int, _ extra: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    var all: [CVarArg] = [count]
    all.append(contentsOf: extra)
    return String(format: format, locale: Locale.current, arguments: all)
}

/// One item of a `<string-array>` (`prompts_family_0`...).
func LA(_ key: String, _ index: Int, _ args: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: "\(key)_\(index)", value: nil, table: nil)
    if args.isEmpty { return format }
    return String(format: format, locale: Locale.current, arguments: args)
}

/// Localised, fuzzy-aware date text (memoryDateText on Android): "12 Mar 2024", "December 2024",
/// "Sometime in 2015", "Summer 2015".
func memoryDateText(_ date: MemoryDate) -> String {
    switch Dates.label(date, locale: .current) {
    case let .exact(t), let .month(t), let .fuzzy(t): return t
    case let .year(t): return L("date_sometime_in", t)
    }
}

func formatBytes(_ bytes: Int64) -> String {
    let nf = NumberFormatter()
    nf.maximumFractionDigits = 1
    let kb = 1024.0, value = Double(bytes)
    if value >= kb * kb * kb { return "\(nf.string(from: NSNumber(value: value / (kb * kb * kb))) ?? "") GB" }
    if value >= kb * kb { return "\(nf.string(from: NSNumber(value: value / (kb * kb))) ?? "") MB" }
    if value >= kb { return "\(nf.string(from: NSNumber(value: value / kb)) ?? "") KB" }
    return "\(bytes) B"
}
