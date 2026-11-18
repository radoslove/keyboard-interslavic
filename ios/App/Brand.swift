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
            }
    }
}
