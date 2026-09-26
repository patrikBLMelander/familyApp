package se.kidquest.app

import android.app.Application
import se.kidquest.app.billing.Billing
import se.kidquest.app.i18n.L10n

class KidQuestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        Billing.configure(this)
    }
}
