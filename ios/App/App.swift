import SwiftUI
import MessageUI

/// Container app. On iOS a keyboard cannot ship on its own - it needs a host
/// app - so this exists mostly to carry the extension and tell the user how to
/// turn it on. That is also why it stays deliberately small.
@main
struct InterslavicKeyboardApp: App {
    var body: some Scene {
        WindowGroup { SetupView() }
    }
}

struct SetupView: View {
    @State private var sample = ""
    @FocusState private var typing: Bool
    @State private var words: [String: [String]] = [:]
    @State private var theme = Shared.theme
    @State private var script = Shared.store.string(forKey: "script") ?? "latin"
    @Environment(\.scenePhase) private var phase
    @State private var collectNew = Shared.collectNewOn
    @State private var collectUsage = Shared.collectUsageOn
    @State private var newCount = 0
    @State private var usageCount = 0
    @State private var logOn = Shared.gestureLogOn
    @State private var showLog = false
    @State private var versionTaps = 0
    @State private var logCount = 0
    /// The file being sent. A sheet bound to an ITEM opens only once the file
    /// exists; `isPresented` + a separate optional opened before the optional
    /// reached the sheet and showed an empty page (iPhone 13, 2026-11-13).
    @State private var mailFile: Outgoing?
    @State private var shareFile: Outgoing?

    /// The keyboards this app carries, in the order of the list.
    private let languages = [("isv", "Medžuslovjansky"), ("pl", "Polski"), ("cs", "češsky"),
                             ("sk", "slovačsky"), ("sl", "slovenečsky"),
                             ("hr", "hrvatsky"), ("sr", "srbsky"), ("bs", "bosnijsky"),
                             ("mk", "makedonsky"), ("ru", "russky"), ("uk", "ukrajinsky"), ("bg", "bulgarsky"), ("be", "bělorussky"), ("sla", "praslovjansky"),
                             ("nb", "norvežsky"), ("en", "English")]

    private func reload() {
        words = Dictionary(uniqueKeysWithValues: languages.map { ($0.0, Shared.userWords($0.0)) })
        logCount = Shared.gestureLines().count
        newCount = Shared.counts(Shared.newWordsKey).count
        usageCount = Shared.counts(Shared.usageKey).count
    }

    /// Android's status line: `New words: on · queued: 3 · ranking: on (12) · id: kbd-…`.
    private var status: String {
        let on = Ui.t("st_on"), off = Ui.t("st_off")
        return "\(Ui.t("st_new")): \(collectNew ? on : off) · \(Ui.t("st_queue")): \(newCount) · "
            + "\(Ui.t("st_rank")): \(collectUsage ? "\(on) (\(usageCount))" : off) · id: \(Shared.contributorId)"
    }

    private func write(_ data: Data, _ name: String) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        return (try? data.write(to: url)) != nil ? url : nil
    }

    /// The word batch under the name the server reads
    /// (`interslove_<contributor>_<RRMMDD_HHMM>.json`, as Android).
    private func sendBatch() {
        guard let data = Shared.batchJSON(),
              let file = write(data, "interslove_\(Shared.contributorId)_\(Self.stamp()).json") else { return }
        send(file, subject: "\(Ui.t("sub_batch")) · \(Shared.contributorId) · \(Self.stamp())")
    }

    /// The log as ONE JSON file for the mail: `interslove-gesty_<RRMMDD_HHMM>.json`
    /// (a hyphen after `interslove`, so the word-batch reader on the server
    /// does not take it for a batch of words).
    private func sendLog() {
        let rows = Shared.gestureLines().compactMap { try? JSONSerialization.jsonObject(with: $0) }
        guard !rows.isEmpty else { return }
        var sys = utsname(); uname(&sys)
        let model = withUnsafeBytes(of: &sys.machine) { String(decoding: $0.prefix(while: { $0 != 0 }), as: UTF8.self) }
        let batch: [String: Any] = [
            "kind": "gestures", "platform": "ios", "version": appVersion,
            "device": model, "system": UIDevice.current.systemVersion,
            "contributor": Shared.contributorId, "rows": rows,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: batch),
              let file = write(data, "interslove-gesty_\(Self.stamp()).json") else { return }
        send(file, subject: "Interslove iOS · \(Ui.t("log_subject")) · \(Self.stamp())")
    }

    private func send(_ file: URL, subject: String) {
        let out = Outgoing(url: file, subject: subject)
        if MFMailComposeViewController.canSendMail() { mailFile = out } else { shareFile = out }
    }

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    private static func stamp() -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyMMdd_HHmm"
        return f.string(from: Date())
    }

    /// A footnote paragraph (with its English gloss in Interslavic mode).
    private func note(_ key: String) -> some View {
        Text(Ui.body(key)).font(.footnote).foregroundStyle(.secondary)
    }

    var body: some View {
        NavigationStack {
            List {
                // Test box. The same trick Keyman uses: it separates "the
                // keyboard is broken" from "the keyboard is not switched on",
                // which are indistinguishable from the outside.
                Section(Ui.head("try_head")) {
                    TextField(Ui.t("try_hint"), text: $sample, axis: .vertical)
                        .focused($typing)
                        .lineLimit(2...4)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                Section(Ui.head("on_head")) {
                    ForEach(1...4, id: \.self) { n in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(n). \(Ui.t("step\(n)"))")
                            if let g = Ui.gloss("step\(n)") {
                                Text(g).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Section(Ui.head("letters_head")) {
                    ForEach([("c", "č"), ("s", "š"), ("z", "ž"), ("e", "ě"),
                             ("d", "dž"), ("l", "lj"), ("n", "nj")], id: \.0) {
                        base, accent in
                        HStack {
                            Text("\(Ui.t("hold")) \(base)").monospaced()
                            Spacer()
                            Text(accent).font(.title2).bold()
                        }
                    }
                    note("capitals")
                }
                // Polish in Glagolitic (layout v04, owner 2026-11-14): Polish only,
                // shown when the app screen is Polish.
                if Ui.current == "pl" {
                    Section(Ui.head("glag_pl_head")) { note("glag_pl_body") }
                }
                Section {
                    Picker(Ui.t("script_pick"), selection: $script) {
                        Text(Ui.t("script_latin")).tag("latin")
                        Text(Ui.t("script_cyrillic")).tag("cyrillic")
                        Text(Ui.t("script_glagolitic")).tag("glagolitic")
                    }
                    .onChange(of: script) { Shared.store.set($0, forKey: "script") }
                    Picker(Ui.t("theme_pick"), selection: $theme) {
                        Text(Ui.t("theme_auto")).tag(Shared.Theme.auto)
                        Text(Ui.t("theme_light")).tag(Shared.Theme.light)
                        Text(Ui.t("theme_dark")).tag(Shared.Theme.dark)
                    }
                    .onChange(of: theme) { Shared.theme = $0 }
                } header: {
                    Text(Ui.head("settings_head"))
                } footer: {
                    if let a = Ui.gloss("script_pick"), let b = Ui.gloss("theme_pick") { Text("\(a) · \(b)") }
                }
                // Collecting words: the same two opt-in signals, texts and batch
                // as Android (`Collector`, `Popularity`, "Send to the database").
                Section {
                    Toggle(Ui.t("collect_sw"), isOn: $collectNew)
                        .onChange(of: collectNew) { Shared.collectNewOn = $0 }
                    note("collect_body")
                    Toggle(Ui.t("pop_sw"), isOn: $collectUsage)
                        .onChange(of: collectUsage) { Shared.collectUsageOn = $0 }
                    note("pop_body")
                    Text(status).font(.footnote).foregroundStyle(.secondary)
                    Button(Ui.t("btn_batch"), action: sendBatch)
                        .disabled(newCount == 0 && usageCount == 0)
                    Button(Ui.t("btn_clear"), role: .destructive) {
                        Shared.clearCounts(Shared.newWordsKey); reload()
                    }.disabled(newCount == 0)
                } header: {
                    Text(Ui.head("collect_head"))
                }
                // One section for every language's own words: only languages that
                // HAVE words get rows, one explanation for all (owner 2026-11-13:
                // sixteen empty sections were clutter).
                Section {
                    let filled = languages.filter { !(words[$0.0] ?? []).isEmpty }
                    if filled.isEmpty {
                        Text(Ui.head("words_none")).foregroundStyle(.secondary)
                    }
                    ForEach(filled, id: \.0) { code, name in
                        let list = words[code] ?? []
                        Text(name).font(.subheadline.bold()).foregroundStyle(.secondary)
                        ForEach(list, id: \.self) { Text($0) }
                            .onDelete { rows in
                                var l = list
                                l.remove(atOffsets: rows)
                                Shared.setUserWords(l, code)
                                reload()
                            }
                    }
                } header: {
                    Text(Ui.head("words_head"))
                } footer: {
                    Text(Ui.body("ios_words_body"))
                }
                Section(Ui.head("privacy_head")) {
                    note("privacy_body")
                }
                // The gesture log is for error reports, not for everyday use:
                // hidden until the version line is tapped five times (owner
                // 2026-11-13), and shown for good once recording is on.
                if showLog || logOn {
                    Section {
                        Toggle(Ui.t("log_toggle"), isOn: $logOn)
                            .onChange(of: logOn) { Shared.gestureLogOn = $0 }
                        Text(logCount == 0 ? Ui.t("log_empty") : "\(Ui.t("log_count")) \(logCount)")
                            .foregroundStyle(.secondary)
                        Button(Ui.t("log_send"), action: sendLog).disabled(logCount == 0)
                        Button(Ui.t("log_clear"), role: .destructive) {
                            Shared.clearGestures(); reload()
                        }.disabled(logCount == 0)
                    } header: {
                        Text(Ui.head("log_head"))
                    } footer: {
                        Text(Ui.body("log_body") + "\n\n" + Ui.body("log_privacy"))
                    }
                }
                Section {
                    Text("Interslove \(appVersion)")
                        .font(.footnote).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            versionTaps += 1
                            if versionTaps >= 5 { showLog = true }
                        }
                }
            }
            .navigationTitle("Interslove")
            .onAppear(perform: reload)
            .onChange(of: phase) { if $0 == .active { reload() } }
            .sheet(item: $mailFile) { f in
                MailView(file: f.url, subject: f.subject,
                         body: "Interslove \(appVersion) (iOS) · \(f.url.lastPathComponent)")
            }
            .sheet(item: $shareFile) { f in ShareSheet(items: [f.url]) }
        }
    }
}

/// The same mailbox as the Android "Send to the database" button.
private let logMailbox = "interslove.keyboard@gmail.com"

private struct MailView: UIViewControllerRepresentable {
    let file: URL, subject: String, body: String
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(dismiss: dismiss) }
    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let vc = MFMailComposeViewController()
        vc.mailComposeDelegate = context.coordinator
        vc.setToRecipients([logMailbox])
        vc.setSubject(subject)
        vc.setMessageBody(body, isHTML: false)
        if let data = try? Data(contentsOf: file) {
            vc.addAttachmentData(data, mimeType: "application/json", fileName: file.lastPathComponent)
        }
        return vc
    }
    func updateUIViewController(_ vc: MFMailComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let dismiss: DismissAction
        init(dismiss: DismissAction) { self.dismiss = dismiss }
        func mailComposeController(_ c: MFMailComposeViewController,
                                   didFinishWith result: MFMailComposeResult, error: Error?) { dismiss() }
    }
}

/// No mail account on the phone: the system share sheet with the same file.
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

private struct Outgoing: Identifiable {
    let url: URL
    var subject = ""
    var id: URL { url }
}
