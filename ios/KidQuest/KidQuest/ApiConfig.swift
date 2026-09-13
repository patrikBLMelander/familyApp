import Foundation

enum ApiConfig {
    // Samma backend som Android-appen använder
    static let baseURL = URL(string: "https://backend-production-5c57.up.railway.app/api/v1/")!
    // Lokal backend via docker (adventures-utveckling):
    // Simulatorn når värddatorn på localhost; ATS undantar localhost så http går bra.
    // static let baseURL = URL(string: "http://localhost:8080/api/v1/")!
}
