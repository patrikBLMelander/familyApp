import Foundation

enum ApiError: Error {
    case invalidURL
    case invalidResponse
    case httpError(Int, Data?)
    case decodingError
}

final class ApiClient {
    static let shared = ApiClient()

    private let session: URLSession

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        session = URLSession(configuration: configuration)
    }

    private func makeRequest(
        path: String,
        method: String,
        queryItems: [URLQueryItem]? = nil,
        body: Encodable? = nil
    ) throws -> URLRequest {
        guard var components = URLComponents(
            url: ApiConfig.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        ) else {
            throw ApiError.invalidURL
        }
        if let queryItems {
            components.queryItems = queryItems
        }
        guard let url = components.url else { throw ApiError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let token = TokenStoreIOS.shared.getToken() {
            request.setValue(token, forHTTPHeaderField: "X-Device-Token")
        }

        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let encoder = JSONEncoder()
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }

        return request
    }

    func send<T: Decodable>(
        _ type: T.Type,
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem]? = nil,
        body: Encodable? = nil
    ) async throws -> T {
        let request = try makeRequest(path: path, method: method, queryItems: queryItems, body: body)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw ApiError.httpError(http.statusCode, data)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .useDefaultKeys
        return try decoder.decode(T.self, from: data)
    }

    func sendWithoutResponse(
        path: String,
        method: String = "POST",
        queryItems: [URLQueryItem]? = nil,
        body: Encodable? = nil
    ) async throws {
        let request = try makeRequest(path: path, method: method, queryItems: queryItems, body: body)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw ApiError.httpError(http.statusCode, data)
        }
    }
}

/// Hjälper oss att skicka vilken Encodable som helst som body.
private struct AnyEncodable: Encodable {
    private let encodeFunc: (Encoder) throws -> Void

    init(_ value: Encodable) {
        self.encodeFunc = value.encode
    }

    func encode(to encoder: Encoder) throws {
        try encodeFunc(encoder)
    }
}

