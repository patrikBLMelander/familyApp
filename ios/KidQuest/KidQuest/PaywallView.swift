import SwiftUI

/// Där en förälder bestämmer sig för om KidQuest är värt att fortsätta betala för.
///
/// A port of Android's PaywallScreen, and the copy is carried over word for word rather
/// than rewritten. It is deliberately a person talking rather than a feature comparison:
/// KidQuest is one parent's app, built for his own children, and that is the most
/// persuasive true thing about it -- more than any list of features would be. What it is
/// not is a request for a donation: after the trial a parent genuinely loses the ability
/// to manage chores, so the price, the renewal and how to cancel are all stated plainly
/// rather than softened. Apple rejects paywalls that blur those terms, and rightly.
///
/// The price is never hardcoded. On Android it is the store's own formatted string; here
/// it is injected, because iOS has no store integration yet. A hardcoded "29 kr" would be
/// wrong for anyone billed in another currency and is exactly the kind of thing that gets
/// a submission rejected -- so when the price is unknown the screen says so instead of
/// inventing one, and the whole price block goes rather than leaving an empty row behind.
struct PaywallView: View {

    /// Butikens egen formaterade prissträng, till exempel "29,00 kr".
    ///
    /// Injected and optional on purpose. Nothing in this project may format this itself:
    /// currency, separators and placement all belong to the store and to wherever the
    /// parent actually is. Nil is a real state -- today it is the *only* state, since no
    /// store SDK is wired up -- and it renders the honest failure, not a guess.
    var formattedMonthlyPrice: String?

    /// Kör själva köpet. Saknas tills StoreKit/RevenueCat finns i projektet.
    ///
    /// An absent closure disables the button rather than offering a tap that goes
    /// nowhere: the same reasoning as Android gating the paywall entry on whether
    /// billing is configured at all.
    var onPurchase: (() -> Void)?

    /// Återställer ett köp som redan gjorts. Saknas av samma skäl som `onPurchase`.
    var onRestore: (() -> Void)?

    var onDismiss: () -> Void = {}

    /// Sätts av anroparen för att visa ett fel köpflödet redan känner till.
    var initialMessage: String?

    /// True medan ett köp eller en återställning pågår.
    var isWorking: Bool = false

    @Environment(\.seasonPalette) private var palette
    @Environment(\.openURL) private var openURL

    @State private var message: String?

    /// Vad som sägs när priset inte går att läsa. Ordagrant Androids formulering för
    /// samma läge: butiken svarade inte, så det finns inget pris att visa.
    private static let unavailable = "Prenumerationen är inte tillgänglig just nu. Försök igen senare."

    private var canBuy: Bool {
        formattedMonthlyPrice != nil && onPurchase != nil && !isWorking
    }

    var body: some View {
        VStack(spacing: 0) {
            closeRow
            scrollingBody
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            // The season's own ground rather than Android's fixed blue wash: every other
            // screen in this app follows the calendar, and a paywall that did not would
            // read as a page from a different app at the moment it most needs to be
            // trusted.
            LinearGradient(
                colors: [palette.pageBg, palette.calBg],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .onAppear {
            // The price is the one thing the screen cannot do without, so its absence
            // is announced immediately rather than only when the button is pressed.
            message = initialMessage ?? (formattedMonthlyPrice == nil ? Self.unavailable : nil)
        }
    }

    // MARK: - Chrome

    private var closeRow: some View {
        HStack {
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.inkSoft)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stäng")
        }
        .padding(.trailing, 6)
    }

    private var scrollingBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                petTrio

                Spacer().frame(height: 22)

                Text("Tack för att ni använder KidQuest")
                    .font(.system(size: 23, weight: .bold))
                    .foregroundStyle(palette.ink)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: 14)

                paragraph(
                    "Jag är en pappa som byggde KidQuest till mina egna barn. Det började "
                    + "som ett sätt att slippa tjata om tandborstning varje morgon. Nu används "
                    + "appen hemma hos er också, och det betyder mycket för mig."
                )

                Spacer().frame(height: 12)

                paragraph(
                    "Jag utvecklar appen själv, på kvällar och helger. Servern och allt runt "
                    + "omkring kostar pengar varje månad, och 29 kronor per familj är vad som gör "
                    + "att jag kan fortsätta."
                )

                Spacer().frame(height: 12)

                Text("— Patrik")
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(palette.ink)

                Spacer().frame(height: 20)

                continuesAsBefore

                Spacer().frame(height: 18)

                priceBlock

                if let message {
                    Spacer().frame(height: 12)
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(palette.danger)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer().frame(height: 16)

                continueButton

                Spacer().frame(height: 14)

                footerLinks

                Spacer().frame(height: 24)
            }
            .padding(.horizontal, 20)
        }
    }

    private func paragraph(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .lineSpacing(7)
            .foregroundStyle(palette.inkSoft)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Price

    @ViewBuilder
    private var priceBlock: some View {
        // No fallback string: the price must never be guessed, and the whole block goes
        // rather than leaving an empty row behind.
        if let price = formattedMonthlyPrice {
            HStack(alignment: .lastTextBaseline, spacing: 6) {
                Text(price)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(palette.accent)
                Text("per månad, för hela familjen")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(palette.inkSoft)
            }

            Spacer().frame(height: 8)

            // Android names Google Play here. The store a parent on this platform would
            // actually go to is the App Store, and telling them otherwise would be both
            // wrong and grounds for rejection -- so the store's name is the one word of
            // this copy that changes between the two apps.
            Text(
                "Förnyas automatiskt tills du avslutar. Du avslutar när du vill i "
                + "App Store och behåller tiden du redan betalat för."
            )
            .font(.system(size: 11.5))
            .lineSpacing(4)
            .foregroundStyle(palette.inkFaint)
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var continueButton: some View {
        Button {
            guard let onPurchase else {
                message = Self.unavailable
                return
            }
            message = nil
            onPurchase()
        } label: {
            ZStack {
                if isWorking {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(palette.onAccent)
                } else if let price = formattedMonthlyPrice {
                    Text("Fortsätt för \(price)/mån")
                        .font(.system(size: 15.5, weight: .semibold))
                } else {
                    Text("Fortsätt")
                        .font(.system(size: 15.5, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .foregroundStyle(palette.onAccent)
            .background(palette.accent.opacity(canBuy ? 1 : 0.45), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!canBuy)
    }

    // MARK: - Footer

    private var footerLinks: some View {
        HStack(spacing: 0) {
            Spacer()
            // Apple requires a restore path: a parent who reinstalls, or switches phone,
            // must be able to get back what they already paid for. It is shown even
            // before there is a store to restore from -- tapping it then explains why
            // nothing happened rather than doing nothing at all.
            footerButton("Återställ köp") {
                guard let onRestore else {
                    message = Self.unavailable
                    return
                }
                message = nil
                onRestore()
            }
            dot
            footerButton("Villkor") { open(LegalLinks.terms) }
            dot
            footerButton("Integritetspolicy") { open(LegalLinks.privacy) }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func footerButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12))
                .underline()
                .foregroundStyle(palette.accent)
                .padding(.horizontal, 4)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var dot: some View {
        Text("·")
            .font(.system(size: 12))
            .foregroundStyle(palette.inkFaint)
    }

    /// Optional URL rather than a force unwrap, and a silent no-op if it is nil: a
    /// malformed link should not be able to take down the screen a parent is on.
    private func open(_ url: URL?) {
        guard let url else { return }
        openURL(url)
    }

    // MARK: - Decoration

    /// Tre av djuren, ritade genom PetVisual så att de sitter på årstidens bakgrund
    /// precis som överallt annars i appen.
    ///
    /// Fixed species rather than the family's own pets: this screen has no child in
    /// scope, and fetching three more things to decorate a paywall is not worth the
    /// latency.
    private var petTrio: some View {
        HStack(spacing: -12) {
            petCircle("cat", stage: 2, size: 64)
            petCircle("dragon", stage: 3, size: 76)
            petCircle("lion", stage: 4, size: 64)
        }
        .frame(maxWidth: .infinity)
    }

    private func petCircle(_ petType: String, stage: Int, size: CGFloat) -> some View {
        PetVisual(
            petType: petType,
            growthStage: stage,
            cornerRadius: size / 2,
            alignment: .bottom
        )
        .frame(width: size - 5, height: size - 5)
        .padding(2.5)
        .background(palette.surface)
        .clipShape(Circle())
    }

    /// Vad en utgången provperiod *inte* tar bort. Trygghet, inte en funktionslista.
    private var continuesAsBefore: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("DET HÄR FORTSÄTTER SOM VANLIGT")
                .font(.system(size: 10.5, weight: .bold))
                .kerning(1.2)
                .foregroundStyle(palette.inkFaint)

            Spacer().frame(height: 10)

            VStack(alignment: .leading, spacing: 7) {
                ForEach(Self.reassurances, id: \.self) { line in
                    HStack(alignment: .firstTextBaseline, spacing: 9) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(palette.goodInk)
                        Text(line)
                            .font(.system(size: 13.5))
                            .foregroundStyle(palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 14))
    }

    private static let reassurances = [
        "Obegränsat antal barn och sysslor",
        "Ett nytt djur att ta hand om varje månad",
        "Plånbok med sparmål",
        "Hela familjen, på alla telefoner",
    ]
}

// MARK: - Fixture

#if DEBUG
extension PaywallView {

    /// Betalväggen med ett exempelpris, så att den går att titta på.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login and an overflow menu cannot be reached at all -- see ScreenHarness in
    /// KidQuestApp.swift. The price here is a *sample* string standing in for what a
    /// store would return; nothing in the app may ever format one itself.
    static func fixture(price: String? = "29,00 kr") -> PaywallView {
        PaywallView(
            formattedMonthlyPrice: price,
            // Wired to no-ops so the button and the restore link photograph as they
            // will once a store is behind them. In the app both are nil today.
            onPurchase: price == nil ? nil : {},
            onRestore: {}
        )
    }

    /// Samma skärm när priset inte gick att läsa -- vilket är exakt vad en förälder ser
    /// idag, eftersom det inte finns någon butik inkopplad än.
    static func fixtureWithoutPrice() -> PaywallView {
        fixture(price: nil)
    }
}

#Preview("Betalvägg") {
    PaywallView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Betalvägg utan pris") {
    PaywallView.fixtureWithoutPrice()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Betalvägg mörk") {
    PaywallView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif
