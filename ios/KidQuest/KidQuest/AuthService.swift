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
