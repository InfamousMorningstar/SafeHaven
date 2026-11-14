package ca.safehaven.intake.ui.staff

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import ca.safehaven.intake.R
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Pages are shown at most this wide, so a Letter page stays readable without huge bitmaps. */
private val PAGE_MAX_WIDTH = 720.dp

/** Hard cap on bitmap width: 1600 x 2071 ARGB is about 13 MB, and at most two pages are alive. */
private const val MAX_BITMAP_WIDTH_PX = 1600

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data object Failed : PdfLoad
    class Ready(val doc: RenderedPdf) : PdfLoad
}

/**
 * Shows every page of [file], fit to width, in a scrolling column. Pages render off the main
 * thread, one at a time, at the on-screen width (not print resolution), and only while visible.
 */
@Composable
fun PdfPages(file: File, modifier: Modifier = Modifier) {
    val load by produceState<PdfLoad>(PdfLoad.Loading, file) {
        var doc: RenderedPdf? = null
        try {
            // Assigned inside, so a doc opened just as the screen closes is still closed below.
            withContext(Dispatchers.IO) { doc = RenderedPdf.openOrNull(file) }
            val opened = doc
            value = if (opened == null || opened.pageCount == 0) PdfLoad.Failed else PdfLoad.Ready(opened)
            awaitCancellation()
        } finally {
            doc?.closeAsync()
        }
    }

    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when (val state = load) {
            PdfLoad.Loading -> CircularProgressIndicator()
            PdfLoad.Failed -> Text(stringResource(R.string.staff_viewer_open_failed), style = MaterialTheme.typography.bodyLarge)
            is PdfLoad.Ready -> PageColumn(state.doc)
        }
    }
}

@Composable
private fun PageColumn(doc: RenderedPdf) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = min(maxWidth - 32.dp, PAGE_MAX_WIDTH)
        val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }.coerceIn(1, MAX_BITMAP_WIDTH_PX)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(doc.pageCount) { index ->
                val size = doc.pageSizes[index]
                PdfPage(
                    doc = doc,
                    index = index,
                    widthPx = widthPx,
                    modifier = Modifier
                        .width(pageWidth)
                        .aspectRatio(size.width.toFloat() / size.height.coerceAtLeast(1)),
                )
            }
        }
    }
}

@Composable
private fun PdfPage(doc: RenderedPdf, index: Int, widthPx: Int, modifier: Modifier) {
    val bitmap = remember(doc, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    val failed = remember(doc, index, widthPx) { mutableStateOf(false) }

    LaunchedEffect(bitmap) {
        val rendered = doc.render(index, widthPx)
        if (rendered != null) bitmap.value = rendered else failed.value = true
    }
    DisposableEffect(bitmap) {
        onDispose {
            val old = bitmap.value
            bitmap.value = null
            // The render thread may still be drawing the last frame that showed this page, so
            // recycle a moment later rather than right now.
            if (old != null) Handler(Looper.getMainLooper()).postDelayed({ old.recycle() }, RECYCLE_DELAY_MILLIS)
        }
    }

    Box(modifier.background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        val shown = bitmap.value
        when {
            shown != null -> Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = stringResource(R.string.staff_viewer_page, index + 1, doc.pageCount),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            failed.value -> Text(
                stringResource(R.string.staff_viewer_page_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color.Black,
            )
            else -> CircularProgressIndicator(Modifier.size(40.dp))
        }
    }
}

private const val RECYCLE_DELAY_MILLIS = 1_000L

/**
 * A PDF open in [PdfRenderer]. The renderer allows one page at a time and isn't thread-safe, so
 * every use goes through [mutex] on a background thread.
 */
private class RenderedPdf private constructor(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    /** Page sizes in points. */
    val pageSizes: List<IntSize>,
) {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /** Renders page [index] at [widthPx] wide on a white background, or returns null on failure. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            val size = pageSizes[index]
            val heightPx = (widthPx.toLong() * size.height / size.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            var bitmap: Bitmap? = null
            try {
                bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                val page = renderer.openPage(index)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
                bitmap
            } catch (e: OutOfMemoryError) {
                bitmap?.recycle()
                null
            } catch (e: RuntimeException) {
                bitmap?.recycle()
                null
            }
        }
    }

    /** Closes the renderer and the file descriptor once any render in progress has finished. */
    fun closeAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                try {
                    renderer.close()
                } catch (e: RuntimeException) {
                    // Already closed.
                }
                try {
                    descriptor.close()
                } catch (e: java.io.IOException) {
                    // PdfRenderer usually closes it already.
                }
            }
        }
    }

    companion object {
        /** Opens [file], or returns null if it can't be read as a PDF. Call off the main thread. */
        fun openOrNull(file: File): RenderedPdf? {
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: java.io.IOException) {
                return null
            }
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                try { descriptor.close() } catch (_: java.io.IOException) {}
                return null
            }
            return try {
                val sizes = (0 until renderer.pageCount).map { i ->
                    val page = renderer.openPage(i)
                    try {
                        IntSize(page.width, page.height)
                    } finally {
                        page.close()
                    }
                }
                RenderedPdf(renderer, descriptor, sizes)
            } catch (e: RuntimeException) {
                renderer.close()
                try { descriptor.close() } catch (_: java.io.IOException) {}
                null
            }
        }
    }
}
