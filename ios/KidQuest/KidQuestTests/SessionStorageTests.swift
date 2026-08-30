import Foundation
import Testing
@testable import KidQuest

/// Kör mot den riktiga nyckelringen i simulatorn -- poängen är den faktiska lagringen,
/// inte en attrapp av den.
///
/// Två saker försvaras. Att en familj som redan är inloggad förblir inloggad över
/// uppgraderingen, och att token efter flytten faktiskt inte längre går att läsa i
/// UserDefaults, vilket är hela skälet till ändringen.
///
/// Testerna delar en singleton och en riktig nyckelring, så de körs i serie.
@Suite(.serialized)
struct SessionStorageTests {

    private let store = TokenStoreIOS.shared

    private func freshInstall() {
        store.resetForTesting()
    }

    private func upgradeOfExistingInstall() {
        store.resetForTesting()
        store.markInstalledForTesting()
    }

    @Test func sessionSurvivesAppLaunchAndIsNotInUserDefaults() {
        freshInstall()
        store.setSession(
            deviceToken: "keychain-token-abc123",
            memberId: "member-1",
            memberName: "Foralder",
            role: "PARENT",
            familyId: "family-1"
        )

        #expect(UserDefaults.standard.string(forKey: "kidquest_device_token") == nil)
        #expect(store.hasLegacyPlaintextForTesting == false)

        // Det uppstarten gör vid nästa lansering.
        store.forgetCachedSessionForTesting()
        store.load()

        #expect(store.getToken() == "keychain-token-abc123")
        #expect(store.getSession()?.role == "PARENT")
        #expect(store.getSession()?.isChild == false)
    }

    @Test func legacyUserDefaultsSessionIsCarriedOverAndErased() {
        upgradeOfExistingInstall()
        let legacy = Session(
            deviceToken: "legacy-token-abc123",
            memberId: "member-77",
            memberName: "Testbarnet",
            role: "CHILD",
            familyId: "family-9"
        )
        store.writeLegacyPlaintextSessionForTesting(legacy)
        #expect(
            UserDefaults.standard.string(forKey: "kidquest_device_token") == "legacy-token-abc123",
            "testet sager ingenting om inte det gamla formatet verkligen var lasbart"
        )

        store.load()

        #expect(store.getSession() == legacy, "den inloggade familjen kastades ut av migreringen")
        #expect(store.getSession()?.isChild == true)
        #expect(store.hasLegacyPlaintextForTesting == false, "klartexten lag kvar i UserDefaults")
    }

    @Test func reinstallDoesNotResurrectTheOldSession() {
        upgradeOfExistingInstall()
        store.setSession(
            deviceToken: "token-from-previous-install",
            memberId: "m", memberName: "n", role: "CHILD", familyId: "f"
        )

        // Nyckelringen overlever att appen raderas, UserDefaults gor det inte:
        // exakt det laget en ominstallation lamnar efter sig.
        UserDefaults.standard.removeObject(forKey: "kidquest_keychain_install_marker")
        store.forgetCachedSessionForTesting()

        store.load()

        #expect(store.getSession() == nil, "en raderad app lamnade kvar en fungerande inloggning")
    }

    @Test func signingOutLeavesNothingBehind() {
        upgradeOfExistingInstall()
        store.setSession(
            deviceToken: "token-to-forget",
            memberId: "m", memberName: "n", role: "PARENT", familyId: "f"
        )

        store.clearToken()

        #expect(store.getSession() == nil)
        store.load()
        #expect(store.getSession() == nil, "utloggningen nadde aldrig lagringen")
        #expect(store.hasLegacyPlaintextForTesting == false)
    }

    @Test func unreadableStoredValueSignsOutInsteadOfCrashing() {
        upgradeOfExistingInstall()
        KeychainSessionStore.write(Data("inte json".utf8))
        store.forgetCachedSessionForTesting()

        store.load()

        #expect(store.getSession() == nil)
        #expect(KeychainSessionStore.read() == nil, "det olasbara vardet lag kvar")
    }
}
