import SwiftUI

extension View {
    /// Each tab's name as the bar's title where "Interslove" stood, the same
    /// size (owner 261118: not a large title); the app's icon with a small "Interslove" in the corner
    /// above it (owner 261118: "nazwy paneli zamiast interslove (...), a
    /// interslove mniejszymi literami z boku u góry").
    func brandTitle(_ name: String) -> some View {
        navigationTitle(name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    HStack(spacing: 8) {
                        Image("Logo").resizable().scaledToFit()
                            .frame(width: 40, height: 40)
                            .clipShape(RoundedRectangle(cornerRadius: 9.5, style: .continuous))
                        Text("Interslove").font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("Interslove")
                }
                // The app's own light / dark, opposite the icon (owner 261121).
                ToolbarItem(placement: .navigationBarTrailing) { AppThemeButton() }
            }
    }
}

/// Buttons side by side while their labels fit on one line, else one under
/// the other: at the largest text sizes the words were split with hyphens
/// ("Po-wta-rzaj", owner 261121).
struct FitRow<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack { content() }
            VStack { content() }
        }
    }
}

/// The app's look: as the phone, light or dark - one tap goes round the three.
/// The keyboard's theme stays its own setting in ⚙.
enum AppTheme: String, CaseIterable {
    case auto, light, dark
    static let key = "app_theme"
    var scheme: ColorScheme? { self == .light ? .light : self == .dark ? .dark : nil }
    var symbol: String {
        switch self {
        case .auto: return "circle.lefthalf.filled"
        case .light: return "sun.max"
        case .dark: return "moon"
        }
    }
}

struct AppThemeButton: View {
    @AppStorage(AppTheme.key) private var raw = AppTheme.auto.rawValue
    var body: some View {
        let t = AppTheme(rawValue: raw) ?? .auto
        Button {
            let all = AppTheme.allCases
            raw = all[(all.firstIndex(of: t)! + 1) % all.count].rawValue
        } label: {
            Image(systemName: t.symbol).font(.title3)
        }
        .accessibilityLabel(Ui.t("theme_\(t.rawValue)"))
    }
}
