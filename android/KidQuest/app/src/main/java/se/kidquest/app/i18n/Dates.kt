package se.kidquest.app.i18n

import java.text.DateFormatSymbols

/** Weekday and month names in the UI language, without hand-written tables. */
object Dates {

    /** Short weekday ("Mån", "Mon", "Mo.", "lun."), ISO numbering: 1 = Monday ... 7 = Sunday. */
    fun weekdayShort(isoDay: Int): String = capitalized(symbols().shortWeekdays[toCalendarDay(isoDay)])

    /** Full weekday ("måndag", "Monday"), as the language writes it mid-sentence. */
    fun weekdayFull(isoDay: Int): String = symbols().weekdays[toCalendarDay(isoDay)]

    /** Full weekday with a capital first letter, for headings. */
    fun weekdayFullCapitalized(isoDay: Int): String = capitalized(weekdayFull(isoDay))

    /** Month name 1-12 as written mid-sentence. */
    fun month(month: Int): String = if (month in 1..12) symbols().months[month - 1] else ""

    /**
     * A day of the month as an ordinal: 1:a, 2:a, 3:e (sv), 1st, 2nd (en), 1. (de), and a
     * bare number in Spanish, where "el día 1" needs none.
     */
    fun dayOrdinal(day: Int): String = when (L10n.language()) {
        "sv" -> day.toString() + if (day % 10 in 1..2 && day != 11 && day != 12) ":a" else ":e"
        "en" -> day.toString() + when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        "de" -> "$day."
        else -> "$day"
    }

    fun capitalized(s: String): String = s.replaceFirstChar { it.titlecase(L10n.locale()) }

    private fun symbols(): DateFormatSymbols = DateFormatSymbols.getInstance(L10n.locale())

    /** java.util.Calendar: Sunday = 1 ... Saturday = 7. */
    private fun toCalendarDay(isoDay: Int): Int = if (isoDay == 7) 1 else isoDay + 1
}
