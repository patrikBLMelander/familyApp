package se.kidquest.app

import android.app.Application
import se.kidquest.app.billing.Billing

class KidQuestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Billing.configure(this)
    }
}
