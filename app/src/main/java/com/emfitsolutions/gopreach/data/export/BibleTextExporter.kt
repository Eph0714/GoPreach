package com.emfitsolutions.gopreach.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.emfitsolutions.gopreach.data.model.BibleTextCategory
import com.emfitsolutions.gopreach.data.model.BibleTextRecord
import com.google.gson.Gson
import java.io.File

/** Marks a JSON file as one this app's own "My Bible Text Record" share/
 * import flow produced — [BibleTextExporter.parseExportJson] refuses
 * anything else (a stray JSON file the user picked by mistake, a future
 * incompatible export shape) rather than silently misreading it as records. */
private const val EXPORT_FILE_TYPE = "gopreach-bible-text-export"

/** One portable, publisher-independent Bible Text Record. [categoryName] —
 * not a categoryId — since a category id only means something inside the
 * *exporting* Publisher's own `bibleTextCategories`; the receiving Publisher
 * resolves-or-creates their own category by this name on import (see
 * [com.emfitsolutions.gopreach.ui.screens.bibletext.BibleTextRecordViewModel
 * .importRecords]), rather than any id ever crossing Publishers. */
data class ExportedBibleTextRecord(
    val categoryName: String,
    val bibleVersionId: String,
    val languageId: String,
    val bibleBookId: String,
    val chapter: Int,
    val verses: String,
    val remarks: String,
)

data class BibleTextExportFile(
    val type: String = EXPORT_FILE_TYPE,
    val exportedAt: Long = 0L,
    val records: List<ExportedBibleTextRecord> = emptyList(),
)

/**
 * "Share the My Bible Text Record to other[s]... export or share through
 * Messenger and other platform[s]... the receiving Publisher can import the
 * data" — a plain JSON file (Gson, the same JSON library every other
 * export/backup feature in this app already uses — see BackupRepository),
 * shared through Android's own share sheet so it reaches whatever app the
 * Publisher picks (Messenger, Gmail, Drive, Bluetooth, ...), then re-opened
 * on the receiving device via a plain file picker for import.
 */
object BibleTextExporter {
    private val gson = Gson()

    fun buildExportJson(records: List<BibleTextRecord>, categoriesById: Map<String, BibleTextCategory>): String {
        val exported = records.map { record ->
            ExportedBibleTextRecord(
                categoryName = categoriesById[record.categoryId]?.name ?: "Uncategorized",
                bibleVersionId = record.bibleVersionId,
                languageId = record.languageId,
                bibleBookId = record.bibleBookId,
                chapter = record.chapter,
                verses = record.verses,
                remarks = record.remarks,
            )
        }
        return gson.toJson(BibleTextExportFile(exportedAt = System.currentTimeMillis(), records = exported))
    }

    /** Null for anything that isn't a genuine export of this shape — a
     * malformed file, or valid JSON that just isn't one of these (missing/
     * wrong [EXPORT_FILE_TYPE]) — so the caller can show "this isn't a
     * Bible Text Record file" instead of crashing or silently importing
     * garbage. */
    fun parseExportJson(json: String): BibleTextExportFile? = runCatching {
        val file = gson.fromJson(json, BibleTextExportFile::class.java)
        file?.takeIf { it.type == EXPORT_FILE_TYPE }
    }.getOrNull()

    /** Writes [json] to a fresh file under cacheDir/exports/ and launches the
     * system share sheet for it — same FileProvider mechanism
     * [com.emfitsolutions.gopreach.ui.components.SupportingImageCapture]'s
     * camera capture already uses, generalized to a JSON payload instead of
     * a photo, so a Messenger/Gmail/Drive/Bluetooth/... target can all
     * receive it the normal Android way. */
    fun share(context: Context, json: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "gopreach-bible-text-${System.currentTimeMillis()}.json")
        file.writeText(json)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Bible Text Records"))
    }
}
