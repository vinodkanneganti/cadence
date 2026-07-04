package studio.sparkcube.cadence

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.sparkcube.cadence.core.bookmark.AndroidBookmarkStore
import studio.sparkcube.cadence.ui.PickedPdf

class MainActivity : ComponentActivity() {

    private var pending: CompletableDeferred<Uri?>? = null

    private val opener = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        pending?.complete(uri)
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.init(applicationContext)
        val bookmarks = AndroidBookmarkStore()

        setContent {
            App(
                pickPdf = { pickPdf() },
                bookmarks = bookmarks,
                now = { System.currentTimeMillis() },
                initialPdf = { lastSessionPdf(bookmarks) },
            )
        }
    }

    private suspend fun pickPdf(): PickedPdf? {
        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        opener.launch(arrayOf("application/pdf"))
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val name = queryName(uri) ?: uri.lastPathSegment ?: "document.pdf"
            PickedPdf(name = name, bytes = bytes, path = uri.toString())
        }
    }

    private suspend fun lastSessionPdf(bookmarks: AndroidBookmarkStore): PickedPdf? = withContext(Dispatchers.IO) {
        val mark = bookmarks.loadLast() ?: return@withContext null
        val path = mark.filePath ?: return@withContext null
        runCatching {
            val uri = Uri.parse(path)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            PickedPdf(name = mark.docName, bytes = bytes, path = path)
        }.getOrNull()
    }

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
}
