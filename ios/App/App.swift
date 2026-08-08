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
                }
                Section("Litery · The letters") {
                    ForEach([("c", "č"), ("s", "š"), ("z", "ž"), ("e", "ě")], id: \.0) {
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
                Section("Privatnost · Privacy") {
                    Label("Ne prosi „Full Access\" — ne može ničto vyslati.",
                          systemImage: "lock.shield")
                    Text("This keyboard never requests Full Access, so it is not able to send anything anywhere.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Medžuslovjansky")
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
