package studio.sparkcube.cadence

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Holds the application [Context] that Android platform actuals need
 * (TextToSpeech, PdfBox init, bookmark files) and performs one-time platform
 * setup. Call [init] once from `MainActivity` before any UI is composed. Keeps
 * `expect class Speaker()` / `PdfExtractor()` no-arg while still reaching the
 * platform, and keeps the PDFBox dependency inside `:shared`.
 */
object AndroidApp {
    lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        PDFBoxResourceLoader.init(context)
    }
}
