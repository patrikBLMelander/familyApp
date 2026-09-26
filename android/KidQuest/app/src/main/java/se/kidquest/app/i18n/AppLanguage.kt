package se.kidquest.app.i18n

import se.kidquest.app.network.ApiClient
import se.kidquest.app.network.FamilyMemberResponse
import se.kidquest.app.network.UpdateLanguageRequest

/**
 * Keeps the in-app language and the member's saved language in step.
 *
 * The server copy is what lets a choice follow a person to a new phone. Only a choice
 * that is actually set is pulled down: a null on the server means "never chosen" as
 * often as "follow the phone", and must not undo a choice made on this device.
 */
object AppLanguage {

    /** Applies the member's saved language if there is one and it differs from the app's. */
    fun syncFrom(member: FamilyMemberResponse?) {
        val saved = member?.language ?: return
        if (saved in L10n.SUPPORTED && saved != L10n.chosenLanguage()) {
            L10n.apply(saved)
        }
    }

    /**
     * Saves the choice for [memberId] and switches the UI. The save goes first: switching
     * recreates the activity, which cancels whatever coroutine is still running.
     */
    suspend fun choose(memberId: String?, language: String?) {
        if (memberId != null) {
            runCatching { ApiClient.familyMembersApi.updateLanguage(memberId, UpdateLanguageRequest(language)) }
        }
        L10n.apply(language)
    }
}
