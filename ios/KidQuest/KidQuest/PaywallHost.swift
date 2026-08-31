import SwiftUI
import RevenueCat

/// Fyller [PaywallView] med butikens svar och utför köpet.
///
/// Vyn är medvetet ren presentation och vet ingenting om RevenueCat. Det är den här som
/// hämtar erbjudanden, formaterar priset och gör köpet -- så att betalväggen går att
/// titta på i harnesket utan att någon butik finns.
struct PaywallHost: View {

    var onDismiss: () -> Void

    @State private var price: String?
    @State private var monthly: Package?
    @State private var isWorking = false
    @State private var message: String?

    var body: some View {
        PaywallView(
            formattedMonthlyPrice: price,
            // Nil när det inte finns något att köpa. Vyn döljer då knappen i stället
            // för att erbjuda ett tryck som inte kan leda någonstans.
            onPurchase: monthly == nil ? nil : { Task { await purchase() } },
            onRestore: BillingConfig.isConfigured ? { Task { await restore() } } : nil,
            onDismiss: onDismiss,
            initialMessage: message,
            isWorking: isWorking
        )
        .task { await loadOfferings() }
    }

    // MARK: - Butiken

    private func loadOfferings() async {
        // Ingen egen text här: utan pris säger PaywallView redan varför, och den
        // formuleringen ska finnas på ett ställe.
        guard BillingConfig.isConfigured, Purchases.isConfigured else { return }
        // SDK:ns eget fel kan dröja närmare en minut. Mätt på Android, men samma
        // mekanism: erbjudandeanropet svarar snabbt, sedan försöker SDK:n återansluta
        // till butiken i fyrtio sekunder innan den ger upp. Ingen tittar på en tom
        // knapp så länge, så vi säger något efter tio. Vilket svar som än landar sist
        // vinner, och båda är sanna.
        let deadline = Task {
            try? await Task.sleep(for: .seconds(10))
            if price == nil, !Task.isCancelled {
                message = "Butiken svarar inte just nu. Försök igen om en stund."
            }
        }
        defer { deadline.cancel() }

        do {
            let offerings = try await Purchases.shared.offerings()
            guard let package = offerings.current?.monthly
                ?? offerings.current?.availablePackages.first else {
                message = "Ingen prenumeration är upplagd i butiken än."
                return
            }
            monthly = package
            // Butikens egen formaterade sträng, så att valuta och avgränsare är det som
            // är rätt där föräldern faktiskt är. Priset gissas aldrig.
            price = package.storeProduct.localizedPriceString
            message = nil
        } catch {
            message = "Kunde inte hämta priset. Försök igen om en stund."
        }
    }

    private func purchase() async {
        guard let monthly else { return }
        isWorking = true
        message = nil
        do {
            let result = try await Purchases.shared.purchase(package: monthly)
            isWorking = false
            if result.userCancelled {
                // En förälder som backade ur har inte stött på ett problem, så säg inget.
                return
            }
            // Servern är den som avgör berättigandet, och den får veta via RevenueCats
            // webhook -- vilket kan dröja några sekunder. Att stänga betalväggen och
            // låta översikten hämta om sig är därför ärligare än att påstå något här.
            onDismiss()
        } catch {
            isWorking = false
            message = "Köpet gick inte igenom. Ingenting har debiterats."
        }
    }

    private func restore() async {
        isWorking = true
        message = nil
        do {
            let info = try await Purchases.shared.restorePurchases()
            isWorking = false
            if info.entitlements[BillingConfig.entitlementPro]?.isActive == true {
                onDismiss()
            } else {
                message = "Vi hittade inget köp att återställa på det här Apple-kontot."
            }
        } catch {
            isWorking = false
            message = "Kunde inte återställa köp just nu."
        }
    }
}
