package se.kidquest.app.network

import se.kidquest.app.BuildConfig
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

    /**
     * Debug builds only, and never the body.
     *
     * This used to log at BODY in every build, which put two things into logcat that
     * have no business being there: the X-Device-Token header -- a bearer key, and a
     * child's entire login -- and the sign-in request body, which carries a parent's
     * password in the clear. Logcat is not private enough for either: it is read by
     * anyone with USB debugging, ends up in bug reports, and is collected by OEM
     * diagnostics.
     *
     * HEADERS keeps what is actually useful when debugging -- method, URL, status,
     * timing, content types -- and [HttpLoggingInterceptor.redactHeader] covers the
     * token even there, so a screenshot of a debug session is not a working login.
     */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("X-Device-Token")
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

    val familyApi: FamilyApi by lazy {
        retrofit.create(FamilyApi::class.java)
    }

    val subscriptionApi: SubscriptionApi by lazy {
        retrofit.create(SubscriptionApi::class.java)
    }

    val dailyChoreApi: DailyChoreApi by lazy {
        retrofit.create(DailyChoreApi::class.java)
    }

    val recurringAllowanceApi: RecurringAllowanceApi by lazy {
        retrofit.create(RecurringAllowanceApi::class.java)
    }
}

