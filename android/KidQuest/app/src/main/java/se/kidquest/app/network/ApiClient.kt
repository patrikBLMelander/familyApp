package se.kidquest.app.network

import se.kidquest.app.session.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {

    private val authInterceptor = Interceptor { chain ->
        val token = TokenStore.getToken()
        val request = chain.request()
        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("X-Device-Token", token)
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
    }

    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    val familyMembersApi: FamilyMembersApi by lazy {
        retrofit.create(FamilyMembersApi::class.java)
    }

    val walletApi: WalletApi by lazy {
        retrofit.create(WalletApi::class.java)
    }

    val petsApi: PetsApi by lazy {
        retrofit.create(PetsApi::class.java)
    }

    val calendarApi: CalendarApi by lazy {
        retrofit.create(CalendarApi::class.java)
    }

    val xpApi: XpApi by lazy {
        retrofit.create(XpApi::class.java)
    }

    val subscriptionApi: SubscriptionApi by lazy {
        retrofit.create(SubscriptionApi::class.java)
    }

    val dailyChoreApi: DailyChoreApi by lazy {
        retrofit.create(DailyChoreApi::class.java)
    }
}

