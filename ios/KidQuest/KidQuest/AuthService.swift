import Foundation

/// Inloggning mot backend. Varje lyckat anrop skriver hela sessionen, inte bara
/// token, så att uppstarten kan routa på rollen utan att fråga servern igen.
enum AuthService {

    @discardableResult
    static func loginByEmail(email: String, password: String) async throws -> FamilyMemberResponseDTO {
        let body = EmailLoginRequestDTO(email: email, password: password)
        let response = try await ApiClient.shared.send(
            EmailLoginResponseDTO.self,
            path: "families/login-by-email",
            method: "POST",
            body: body
        )
        TokenStoreIOS.shared.setSession(
            deviceToken: response.deviceToken,
            memberId: response.member.id,
            memberName: response.member.name,
            role: response.member.role,
            familyId: response.member.familyId
        )
        return response.member
    }

    @discardableResult
    static func register(
        familyName: String,
        adminName: String,
        email: String,
        password: String
    ) async throws -> FamilyRegistrationResponseDTO {
        let body = RegisterFamilyRequestDTO(
            familyName: familyName,
            adminName: adminName,
            adminEmail: email,
            password: password
        )
        let response = try await ApiClient.shared.send(
            FamilyRegistrationResponseDTO.self,
            path: "families/register",
            method: "POST",
            body: body
        )
        // Familjens id kommer från familjen här; medlemssvaret vid registrering bär
        // det inte alltid med sig.
        TokenStoreIOS.shared.setSession(
            deviceToken: response.deviceToken,
            memberId: response.admin.id,
            memberName: response.admin.name,
            role: response.admin.role,
            familyId: response.family.id
        )
        return response
    }

    /// Ber servern skicka en återställningslänk till adressen.
    ///
    /// Says nothing about whether the address has an account, because the server says
    /// nothing either: it answers 200 either way. Anything the app added on top of that
    /// -- a "no such account" message, or even a different error for one address than
    /// another -- would turn the form into a way of asking which families use KidQuest,
    /// and the answer would be a list of parents.
    ///
    /// That is why a non-2xx status is swallowed here rather than surfaced. The reply
    /// carries no per-address information by design, so the only thing a status code
    /// can do at this point is leak a difference the server took care not to make.
    /// A transport failure still throws: the request never left the phone, and the
    /// parent needs to know it has to be sent again.
    static func requestPasswordReset(email: String) async throws {
        let body = PasswordResetRequestDTO(email: email)
        do {
            try await ApiClient.shared.sendWithoutResponse(
                path: "families/password-reset/request",
                method: "POST",
                body: body
            )
        } catch ApiError.httpError(let status, _) where (400..<500).contains(status) {
            // Svälj bara klientfel. Det är där ett läckage skulle sitta om servern en
            // dag "hjälpsamt" började svara 404 på en adress utan konto -- då blir
            // statuskoden en skillnad som avslöjar vem som använder appen, och den
            // skillnaden får inte nå skärmen.
            //
            // Serverfel går vidare med flit. Servern svarar 200 på varje adress idag,
            // så en 5xx betyder att den ligger nere -- och att då säga "kolla din
            // mejl" skickar en förälder att vänta på ett mejl som aldrig kommer.
        }
    }

    /// Tar reda på vem en sparad token tillhör. Behövs bara för sessioner som skrevs
    /// innan rollen sparades lokalt; nya inloggningar anropar den aldrig.
    /// TODO: byt till ett header-baserat /family-members/me så att token slutar resa
    /// i en URL-path, där proxyservrar loggar den.
    static func memberByDeviceToken(_ deviceToken: String) async throws -> FamilyMemberResponseDTO {
        // ApiClient bygger URL:en med appendingPathComponent, som escapar token åt oss.
        try await ApiClient.shared.send(
            FamilyMemberResponseDTO.self,
            path: "family-members/by-device-token/\(deviceToken)",
            method: "GET"
        )
    }
}
