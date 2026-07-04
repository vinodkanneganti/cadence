package studio.sparkcube.cadence

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.posix.memcpy
import studio.sparkcube.cadence.ui.PickedPdf
import kotlin.coroutines.resume

// Keeps the picker delegate alive while the sheet is up (delegate is a weak ref).
private var heldDelegate: NSObject? = null

/** Present the iOS document picker and return the chosen PDF's bytes. */
@OptIn(ExperimentalForeignApi::class)
suspend fun pickPdfIos(): PickedPdf? = suspendCancellableCoroutine { cont ->
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF))

    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            heldDelegate = null
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                cont.resume(null)
                return
            }
            val accessed = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url)
            if (accessed) url.stopAccessingSecurityScopedResource()
            if (data == null) {
                cont.resume(null)
                return
            }
            cont.resume(
                PickedPdf(
                    name = url.lastPathComponent ?: "document.pdf",
                    bytes = data.toByteArray(),
                    path = url.absoluteString,
                ),
            )
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            heldDelegate = null
            cont.resume(null)
        }
    }

    heldDelegate = delegate
    picker.delegate = delegate

    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root == null) {
        heldDelegate = null
        cont.resume(null)
    } else {
        root.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, this.length) }
    return out
}
