package se.kidquest.app.network

object ApiConfig {
    // Prod-backend (Railway)
    const val BASE_URL: String = "https://backend-production-5c57.up.railway.app/api/v1/"
    // Lokal backend via docker (adventures-utveckling): "http://10.0.2.2:8080/api/v1/"
    // 10.0.2.2 = värddatorn sett från Android-emulatorn.
}
