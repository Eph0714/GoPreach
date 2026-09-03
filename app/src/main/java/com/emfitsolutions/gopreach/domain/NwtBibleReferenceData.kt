package com.emfitsolutions.gopreach.domain

/** "My Bible Text Record" module spec §21 — [BibleVersion]/[BibleLanguage]/
 * [BibleBook] as a real relational shape, matching the spec's own table
 * definitions field-for-field, but held as **bundled static data** in this
 * file rather than a synced Firestore collection like every user-generated
 * collection elsewhere in this app.
 *
 * Why: this is reference/lookup metadata — book names, canonical order,
 * chapter counts — that is identical for every congregation, every device,
 * and never changes at runtime; it has none of the "written by one device,
 * needs to reach every other device" shape [OfflineFirestoreRepository]'s
 * whole sync pipeline exists for. Bundling it means correct data ships with
 * the app on day one with no admin seeding step, and "the language/book
 * list must not be hard-coded into the UI" (spec §3/§5) is satisfied by
 * having exactly one canonical source screens read from — just a Kotlin
 * object instead of a Firestore round-trip. [BibleTextRecord]/
 * [com.emfitsolutions.gopreach.data.model.BibleTextRecord] stores plain ids
 * (`bibleVersionId`/`languageId`/`bibleBookId`) that resolve through this
 * object today; moving this data to a real backend table later (spec §21's
 * "future Bible versions" hook) is a data-source swap behind the same
 * lookup functions below, not a schema change to the record itself.
 *
 * Content note (spec §32): only book *names*, canonical order, and chapter
 * *counts* live here — structural facts common to the standard 66-book
 * canon this app's New World Translation uses, not the licensed verse text
 * itself. The Filipino book titles below are transcribed from memory
 * against the published Bagong Sanlibutang Salin ng Banal na Kasulatan
 * edition and should be spot-checked against an actual jw.org/JW Library
 * copy before this ships to real congregations — a wrong book title is a
 * data-accuracy bug worth catching, even though it carries none of the
 * full-verse-text copyright risk spec §32 is actually guarding against.
 */
object NwtBibleReferenceData {

    /** Spec §2/§21 — one row today (`nwt`), but every lookup below is keyed
     * by [BibleVersion.id] rather than assuming NWT everywhere, so a second
     * version can be added as a second entry in [versions] without touching
     * any screen. */
    data class BibleVersion(val id: String, val name: String, val abbreviation: String, val isActive: Boolean = true)

    /** Spec §3/§4 — the dropdown source; never hard-code a language list in
     * a screen, read [languages] instead. */
    data class BibleLanguage(val id: String, val name: String, val code: String, val isActive: Boolean = true)

    /** Spec §5/§8 — one Bible book, scoped to a specific Version+Language
     * (spec §21's exact composite: "Bible Version + Language + Bible Book"),
     * carrying its own localized [name], canonical [order] (for sorting/
     * filter lists), [testament], and [chapterCount] (spec §8's chapter-
     * dropdown/validation source). */
    data class BibleBook(
        val id: String,
        val bibleVersionId: String,
        val languageId: String,
        val name: String,
        val order: Int,
        val testament: Testament,
        val chapterCount: Int,
    )

    enum class Testament { OLD, NEW }

    val versions: List<BibleVersion> = listOf(
        BibleVersion(id = "nwt", name = "New World Translation of the Holy Scriptures", abbreviation = "NWT"),
    )
    val defaultVersion: BibleVersion = versions.first()

    val languages: List<BibleLanguage> = listOf(
        BibleLanguage(id = "en", name = "English", code = "en"),
        BibleLanguage(id = "fil", name = "Filipino", code = "fil"),
    )

    fun language(id: String?): BibleLanguage? = languages.firstOrNull { it.id == id }
    fun version(id: String?): BibleVersion? = versions.firstOrNull { it.id == id }

    /** Spec §5-§8 — every book for [versionId]+[languageId], in canonical
     * ([BibleBook.order]) order; empty for a version/language combination
     * with no data yet rather than throwing, so an unrecognized/removed
     * language degrades to an empty book picker instead of crashing the
     * Add/Edit form. */
    fun booksFor(versionId: String, languageId: String): List<BibleBook> =
        booksByVersionAndLanguage[versionId to languageId].orEmpty()

    fun book(versionId: String, languageId: String, bookId: String): BibleBook? =
        booksFor(versionId, languageId).firstOrNull { it.id == bookId }

    /** Canonical (book slug, order, testament, chapter count) — the 66-book
     * structure standard across mainstream Bible translations, including
     * NWT; shared by every language's localized name list below so the
     * order/testament/chapterCount never has to be repeated per language. */
    private data class BookShape(val slug: String, val order: Int, val testament: Testament, val chapterCount: Int)

    private val bookShapes: List<BookShape> = listOf(
        BookShape("genesis", 1, Testament.OLD, 50),
        BookShape("exodus", 2, Testament.OLD, 40),
        BookShape("leviticus", 3, Testament.OLD, 27),
        BookShape("numbers", 4, Testament.OLD, 36),
        BookShape("deuteronomy", 5, Testament.OLD, 34),
        BookShape("joshua", 6, Testament.OLD, 24),
        BookShape("judges", 7, Testament.OLD, 21),
        BookShape("ruth", 8, Testament.OLD, 4),
        BookShape("1samuel", 9, Testament.OLD, 31),
        BookShape("2samuel", 10, Testament.OLD, 24),
        BookShape("1kings", 11, Testament.OLD, 22),
        BookShape("2kings", 12, Testament.OLD, 25),
        BookShape("1chronicles", 13, Testament.OLD, 29),
        BookShape("2chronicles", 14, Testament.OLD, 36),
        BookShape("ezra", 15, Testament.OLD, 10),
        BookShape("nehemiah", 16, Testament.OLD, 13),
        BookShape("esther", 17, Testament.OLD, 10),
        BookShape("job", 18, Testament.OLD, 42),
        BookShape("psalms", 19, Testament.OLD, 150),
        BookShape("proverbs", 20, Testament.OLD, 31),
        BookShape("ecclesiastes", 21, Testament.OLD, 12),
        BookShape("songofsolomon", 22, Testament.OLD, 8),
        BookShape("isaiah", 23, Testament.OLD, 66),
        BookShape("jeremiah", 24, Testament.OLD, 52),
        BookShape("lamentations", 25, Testament.OLD, 5),
        BookShape("ezekiel", 26, Testament.OLD, 48),
        BookShape("daniel", 27, Testament.OLD, 12),
        BookShape("hosea", 28, Testament.OLD, 14),
        BookShape("joel", 29, Testament.OLD, 3),
        BookShape("amos", 30, Testament.OLD, 9),
        BookShape("obadiah", 31, Testament.OLD, 1),
        BookShape("jonah", 32, Testament.OLD, 4),
        BookShape("micah", 33, Testament.OLD, 7),
        BookShape("nahum", 34, Testament.OLD, 3),
        BookShape("habakkuk", 35, Testament.OLD, 3),
        BookShape("zephaniah", 36, Testament.OLD, 3),
        BookShape("haggai", 37, Testament.OLD, 2),
        BookShape("zechariah", 38, Testament.OLD, 14),
        BookShape("malachi", 39, Testament.OLD, 4),
        BookShape("matthew", 40, Testament.NEW, 28),
        BookShape("mark", 41, Testament.NEW, 16),
        BookShape("luke", 42, Testament.NEW, 24),
        BookShape("john", 43, Testament.NEW, 21),
        BookShape("acts", 44, Testament.NEW, 28),
        BookShape("romans", 45, Testament.NEW, 16),
        BookShape("1corinthians", 46, Testament.NEW, 16),
        BookShape("2corinthians", 47, Testament.NEW, 13),
        BookShape("galatians", 48, Testament.NEW, 6),
        BookShape("ephesians", 49, Testament.NEW, 6),
        BookShape("philippians", 50, Testament.NEW, 4),
        BookShape("colossians", 51, Testament.NEW, 4),
        BookShape("1thessalonians", 52, Testament.NEW, 5),
        BookShape("2thessalonians", 53, Testament.NEW, 3),
        BookShape("1timothy", 54, Testament.NEW, 6),
        BookShape("2timothy", 55, Testament.NEW, 4),
        BookShape("titus", 56, Testament.NEW, 3),
        BookShape("philemon", 57, Testament.NEW, 1),
        BookShape("hebrews", 58, Testament.NEW, 13),
        BookShape("james", 59, Testament.NEW, 5),
        BookShape("1peter", 60, Testament.NEW, 5),
        BookShape("2peter", 61, Testament.NEW, 3),
        BookShape("1john", 62, Testament.NEW, 5),
        BookShape("2john", 63, Testament.NEW, 1),
        BookShape("3john", 64, Testament.NEW, 1),
        BookShape("jude", 65, Testament.NEW, 1),
        BookShape("revelation", 66, Testament.NEW, 22),
    )

    private val englishNames: Map<String, String> = mapOf(
        "genesis" to "Genesis", "exodus" to "Exodus", "leviticus" to "Leviticus", "numbers" to "Numbers",
        "deuteronomy" to "Deuteronomy", "joshua" to "Joshua", "judges" to "Judges", "ruth" to "Ruth",
        "1samuel" to "1 Samuel", "2samuel" to "2 Samuel", "1kings" to "1 Kings", "2kings" to "2 Kings",
        "1chronicles" to "1 Chronicles", "2chronicles" to "2 Chronicles", "ezra" to "Ezra", "nehemiah" to "Nehemiah",
        "esther" to "Esther", "job" to "Job", "psalms" to "Psalms", "proverbs" to "Proverbs",
        "ecclesiastes" to "Ecclesiastes", "songofsolomon" to "Song of Solomon", "isaiah" to "Isaiah",
        "jeremiah" to "Jeremiah", "lamentations" to "Lamentations", "ezekiel" to "Ezekiel", "daniel" to "Daniel",
        "hosea" to "Hosea", "joel" to "Joel", "amos" to "Amos", "obadiah" to "Obadiah", "jonah" to "Jonah",
        "micah" to "Micah", "nahum" to "Nahum", "habakkuk" to "Habakkuk", "zephaniah" to "Zephaniah",
        "haggai" to "Haggai", "zechariah" to "Zechariah", "malachi" to "Malachi",
        "matthew" to "Matthew", "mark" to "Mark", "luke" to "Luke", "john" to "John", "acts" to "Acts",
        "romans" to "Romans", "1corinthians" to "1 Corinthians", "2corinthians" to "2 Corinthians",
        "galatians" to "Galatians", "ephesians" to "Ephesians", "philippians" to "Philippians",
        "colossians" to "Colossians", "1thessalonians" to "1 Thessalonians", "2thessalonians" to "2 Thessalonians",
        "1timothy" to "1 Timothy", "2timothy" to "2 Timothy", "titus" to "Titus", "philemon" to "Philemon",
        "hebrews" to "Hebrews", "james" to "James", "1peter" to "1 Peter", "2peter" to "2 Peter",
        "1john" to "1 John", "2john" to "2 John", "3john" to "3 John", "jude" to "Jude", "revelation" to "Revelation",
    )

    // See the file's own doc comment — verify against an official Tagalog
    // NWT copy before production use.
    private val filipinoNames: Map<String, String> = mapOf(
        "genesis" to "Genesis", "exodus" to "Exodus", "leviticus" to "Levitico", "numbers" to "Mga Bilang",
        "deuteronomy" to "Deuteronomio", "joshua" to "Josue", "judges" to "Mga Hukom", "ruth" to "Ruth",
        "1samuel" to "1 Samuel", "2samuel" to "2 Samuel", "1kings" to "1 Hari", "2kings" to "2 Hari",
        "1chronicles" to "1 Cronica", "2chronicles" to "2 Cronica", "ezra" to "Ezra", "nehemiah" to "Nehemias",
        "esther" to "Esther", "job" to "Job", "psalms" to "Mga Awit", "proverbs" to "Kawikaan",
        "ecclesiastes" to "Eclesiastes", "songofsolomon" to "Awit ni Solomon", "isaiah" to "Isaias",
        "jeremiah" to "Jeremias", "lamentations" to "Mga Panaghoy", "ezekiel" to "Ezekiel", "daniel" to "Daniel",
        "hosea" to "Hoseas", "joel" to "Joel", "amos" to "Amos", "obadiah" to "Obadias", "jonah" to "Jonas",
        "micah" to "Mikas", "nahum" to "Nahum", "habakkuk" to "Habacuc", "zephaniah" to "Zefanias",
        "haggai" to "Hagai", "zechariah" to "Zacarias", "malachi" to "Malakias",
        "matthew" to "Mateo", "mark" to "Marcos", "luke" to "Lucas", "john" to "Juan", "acts" to "Mga Gawa",
        "romans" to "Roma", "1corinthians" to "1 Corinto", "2corinthians" to "2 Corinto",
        "galatians" to "Galacia", "ephesians" to "Efeso", "philippians" to "Filipos",
        "colossians" to "Colosas", "1thessalonians" to "1 Tesalonica", "2thessalonians" to "2 Tesalonica",
        "1timothy" to "1 Timoteo", "2timothy" to "2 Timoteo", "titus" to "Tito", "philemon" to "Filemon",
        "hebrews" to "Hebreo", "james" to "Santiago", "1peter" to "1 Pedro", "2peter" to "2 Pedro",
        "1john" to "1 Juan", "2john" to "2 Juan", "3john" to "3 Juan", "jude" to "Judas", "revelation" to "Apocalipsis",
    )

    private val namesByLanguage: Map<String, Map<String, String>> = mapOf("en" to englishNames, "fil" to filipinoNames)

    private val booksByVersionAndLanguage: Map<Pair<String, String>, List<BibleBook>> = buildMap {
        for (version in versions) {
            for (language in languages) {
                val names = namesByLanguage[language.id] ?: continue
                val books = bookShapes.mapNotNull { shape ->
                    val name = names[shape.slug] ?: return@mapNotNull null
                    BibleBook(
                        id = shape.slug,
                        bibleVersionId = version.id,
                        languageId = language.id,
                        name = name,
                        order = shape.order,
                        testament = shape.testament,
                        chapterCount = shape.chapterCount,
                    )
                }
                put(version.id to language.id, books)
            }
        }
    }
}
