import SwiftUI

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

    /// The keyboards this app carries, in the order of the list.
    private let languages = [("isv", "Medžuslovjansky"), ("pl", "Polski"), ("en", "English")]

    private func reload() {
        words = Dictionary(uniqueKeysWithValues: languages.map { ($0.0, Shared.userWords($0.0)) })
    }

    var body: some View {
        NavigationStack {
            List {
                // Test box. The same trick Keyman uses: it separates "the
                // keyboard is broken" from "the keyboard is not switched on",
                // which are indistinguishable from the outside.
                Section("Proba · Try it here") {
                    TextField("piši tu…", text: $sample, axis: .vertical)
                        .focused($typing)
                        .lineLimit(2...4)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }
                Section("Vklučenje · Turn it on") {
                    Step(1, "Nastrojenja → Osnovne → Tipkovnica → Tipkovnici",
                         "Settings → General → Keyboard → Keyboards")
                    Step(2, "Dodaj novu tipkovnicu → Medžuslovjansky",
                         "Add New Keyboard → Medžuslovjansky")
                    Step(3, "V tekstu tikni 🌐 i vyberi ju",
                         "In any text field tap 🌐 and pick it")
                    Step(4, "Medžuslovjansky → Full Access (opcionalno)",
                         "Medžuslovjansky → Allow Full Access (optional: My words, theme)")
                }
                Section("Litery · The letters") {
                    ForEach([("c", "č"), ("s", "š"), ("z", "ž"), ("e", "ě"),
                             ("d", "dž"), ("l", "lj"), ("n", "nj")], id: \.0) {
                        base, accent in
                        HStack {
                            Text("drži \(base)").monospaced()
                            Spacer()
                            Text(accent).font(.title2).bold()
                        }
                    }
                    Text("Velike litery: najprvo Shift, potom drži.\nFor capitals: Shift first, then hold.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                // Texts from the Android settings (Ui.kt), same keys.
                Section {
                    Picker("Latinica, kirilica ili glagolica", selection: $script) {
                        Text("Latinica").tag("latin")
                        Text("Kirilica").tag("cyrillic")
                        Text("Glagolica").tag("glagolitic")
                    }
                    .onChange(of: script) { Shared.store.set($0, forKey: "script") }
                    Picker("Tema klaviatury", selection: $theme) {
                        Text("Avtomatično, kako telefon").tag(Shared.Theme.auto)
                        Text("Světla").tag(Shared.Theme.light)
                        Text("Temna").tag(Shared.Theme.dark)
                    }
                    .onChange(of: theme) { Shared.theme = $0 }
                } header: {
                    Text("Nastrojenja · Settings")
                } footer: {
                    Text("Latin, Cyrillic or Glagolitic · Keyboard theme")
                }
                ForEach(languages, id: \.0) { code, name in
                    Section {
                        let list = words[code] ?? []
                        if list.isEmpty {
                            Text("Ješče ničto ne jest dodano. · No words added yet.")
                                .foregroundStyle(.secondary)
                        }
                        ForEach(list, id: \.self) { Text($0) }
                            .onDelete { rows in
                                var l = list
                                l.remove(atOffsets: rows)
                                Shared.setUserWords(l, code)
                                reload()
                            }
                    } header: {
                        Text("Moje slova · My words — \(name)")
                    } footer: {
                        Text("Slova dodane čipom ＋ v pasu prědloženij. Odstrani slovo i klaviatura go prěstane prědlagati.\nWords you added with the ＋ chip. Swipe one left to remove it.")
                    }
                }
                Section("Privatnost · Privacy") {
                    Label("Klaviatura ničego ne posylaje.", systemImage: "lock.shield")
                    Text("Full Access jest potrěbny jedino, da by aplikacija i klaviatura dělili tvoje slova i nastrojenja. Bez njego klaviatura dalje dobro pracuje.\nFull Access is only for sharing your words and settings between this app and the keyboard. The keyboard has no network code and works fully without it.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Medžuslovjansky")
            .onAppear(perform: reload)
            .onChange(of: phase) { if $0 == .active { reload() } }
        }
    }
}

private struct Step: View {
    let n: Int, ms: String, en: String
    init(_ n: Int, _ ms: String, _ en: String) { self.n = n; self.ms = ms; self.en = en }
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(n). \(ms)")
            Text(en).font(.footnote).foregroundStyle(.secondary)
        }
    }
}
