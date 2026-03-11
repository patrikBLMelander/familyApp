import SwiftUI
import CoreImage.CIFilterBuiltins

struct ChildInviteSheet: View {
    let childName: String
    let memberId: String

    @Environment(\.dismiss) private var dismiss

    @State private var inviteToken: String?
    @State private var errorMessage: String?
    @State private var isCopied: Bool = false

    private let context = CIContext()
    private let filter = CIFilter.qrCodeGenerator()

    var body: some View {
        NavigationStack {
            Group {
                if let errorMessage {
                    VStack(spacing: 12) {
                        Text(errorMessage)
                            .foregroundColor(.red)
                        Button("Stäng") { dismiss() }
                    }
                    .padding()
                } else if inviteToken == nil {
                    ProgressView("Genererar inbjudningskod…")
                } else {
                    content
                }
            }
            .padding()
            .navigationTitle("Bjud in \(childName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Stäng") { dismiss() }
                }
            }
        }
        .task {
            await loadInvite()
        }
    }

    private var content: some View {
        VStack(spacing: 16) {
            if let token = inviteToken {
                Text("Låt \(childName) skanna QR-koden eller ange koden i appen för att koppla sin telefon.")
                    .multilineTextAlignment(.center)

                if let image = generateQRCode(from: token) {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                }

                Text(token)
                    .font(.title3.monospaced())
                    .foregroundColor(.blue)

                Button(isCopied ? "Kopierad!" : "Kopiera kod") {
                    UIPasteboard.general.string = token
                    isCopied = true
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func loadInvite() async {
        do {
            let token = try await FamilyRepository.generateInviteToken(forMemberId: memberId)
            await MainActor.run {
                self.inviteToken = token
            }
        } catch {
            await MainActor.run {
                self.errorMessage = "Kunde inte generera inbjudningskod."
            }
        }
    }

    private func generateQRCode(from string: String) -> UIImage? {
        let data = Data(string.utf8)
        filter.setValue(data, forKey: "InputMessage")

        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        if let cgImage = context.createCGImage(scaled, from: scaled.extent) {
            return UIImage(cgImage: cgImage)
        }
        return nil
    }
}

