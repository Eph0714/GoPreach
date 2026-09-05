package com.emfitsolutions.gopreach.domain

/**
 * "Settings -> Language" — the three interface languages GoPreach supports,
 * distinct from [com.emfitsolutions.gopreach.data.model.Person
 * .preferredBibleLanguageId] (which only picks a Bible Text Record's default
 * language, never the UI itself).
 *
 * [code] is what's stored on [com.emfitsolutions.gopreach.data.model.Person
 * .language] and synced through Firestore like any other profile field;
 * [localeTag] is the BCP-47 tag handed to `AppCompatDelegate
 * .setApplicationLocales()` (see [com.emfitsolutions.gopreach.data.repository
 * .AppLanguageRepository]) and doubles as the `res/values-<tag>/` resource
 * folder Android resolves strings from. They're kept as the same value here
 * deliberately — one fewer place for the two to drift apart — except Iloko,
 * which has no ISO 639-1 two-letter code at all: Android resolves it from
 * `res/values-b+ilo/` (the BCP-47 extension qualifier syntax for a
 * language with only a 3-letter tag), and `LocaleListCompat.forLanguageTags`
 * accepts the plain 3-letter tag the same way.
 *
 * [confirmationMessage] is hardcoded per-language here, not read from
 * `strings.xml` — the message has to appear in the *newly* selected
 * language the instant it's shown, and that can land either just before or
 * just after the locale-change-triggered Activity recreation depending on
 * timing; sourcing it from this enum instead of a resource lookup avoids
 * that race entirely.
 */
enum class AppLanguage(
    val code: String,
    val localeTag: String,
    val displayLabel: String,
    val confirmationMessage: String,
) {
    ENGLISH("en", "en", "English", "Language successfully changed."),
    FILIPINO("tl", "tl", "Filipino / Tagalog", "Matagumpay na naibago ang wika."),
    ILOKO("ilo", "ilo", "Iloko / Ilocano", "Balligi a naisukat ti lengguahe.");

    companion object {
        /** Spec: "If a user has not selected a language, automatically use
         * English" — also the fallback for a code this build doesn't
         * recognize (e.g. a future language removed again, or a stray
         * value). Never null, never blank. */
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}
