package ca.safehaven.intake.data.pdf

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.IntakePdfWriter
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.labelRes
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * One-page US Letter record drawn with the built-in [PdfDocument] (plan 3.5 / D2-A).
 * Text is real, selectable text. Layout follows the sample in the plan.
 */
class AndroidIntakePdfWriter(
    private val resources: Resources,
    private val zone: ZoneId,
    private val shelterName: String,
    private val appName: String,
    private val deviceLabel: String,
    private val appVersion: String,
) : IntakePdfWriter {

    private val titlePaint = textPaint(20f, bold = true)
    private val subtitlePaint = textPaint(12f, color = GREY)
    private val sectionPaint = textPaint(10f, bold = true, color = GREY).apply { letterSpacing = 0.08f }
    private val labelPaint = textPaint(10.5f, color = GREY)
    private val bodyPaint = textPaint(12f)
    private val smallPaint = textPaint(8.5f, color = GREY)
    private val rulePaint = Paint().apply { color = RULE; strokeWidth = 0.75f }

    override fun write(record: IntakeRecord, out: OutputStream) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawPage(page.canvas, record)
            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun drawPage(canvas: Canvas, r: IntakeRecord) {
        // TODO(client): draw the shelter logo left of the title once we have it.
        var y = MARGIN
        y = drawText(canvas, shelterName, titlePaint, MARGIN, y, CONTENT_WIDTH)
        y = drawText(canvas, str(R.string.pdf_title), subtitlePaint, MARGIN, y + 2f, CONTENT_WIDTH)
        y += 12f
        drawRule(canvas, y)
        y += 16f

        y = drawRow(canvas, str(R.string.pdf_record_id), r.recordCode, y)
        y = drawRow(canvas, str(R.string.pdf_date_time), DATE_TIME.format(r.createdAt.atZone(zone)), y)

        y += SECTION_GAP
        y = drawText(canvas, str(R.string.pdf_section_visitor), sectionPaint, MARGIN, y, CONTENT_WIDTH) + 6f
        y = drawRow(canvas, str(R.string.pdf_full_name), r.fullName, y)
        y = drawRow(canvas, str(R.string.pdf_phone), r.phoneDisplay, y)
        val streetLine = listOfNotNull(r.street, r.unit?.let { str(R.string.pdf_unit, it) }).joinToString(", ")
        val cityLine = "${r.city}, ${r.province.name}  ${r.postalCode}"
        y = drawRow(canvas, str(R.string.pdf_address), "$streetLine\n$cityLine", y)
        r.reason?.let { y = drawRow(canvas, str(R.string.pdf_reason), resources.getString(it.labelRes()), y) }

        r.consentTextVersion?.let { version ->
            y += SECTION_GAP
            y = drawText(canvas, str(R.string.pdf_section_consent), sectionPaint, MARGIN, y, CONTENT_WIDTH) + 6f
            val box = if (r.consentGiven) "[x]" else "[ ]"
            val consent = "$box ${str(R.string.consent_agree)} ${str(R.string.consent_notice, shelterName)}"
            y = drawText(canvas, consent, bodyPaint, MARGIN, y, CONTENT_WIDTH)
            drawText(canvas, str(R.string.pdf_consent_version, version), smallPaint, MARGIN, y + 4f, CONTENT_WIDTH)
        }

        val footerTop = PAGE_HEIGHT - MARGIN - 28f
        drawRule(canvas, footerTop)
        var fy = footerTop + 8f
        fy = drawText(
            canvas, str(R.string.pdf_footer_generated, appName, appVersion, deviceLabel),
            smallPaint, MARGIN, fy, CONTENT_WIDTH,
        )
        drawText(canvas, str(R.string.pdf_footer_confidential), smallPaint, MARGIN, fy + 2f, CONTENT_WIDTH)
    }

    /** Draws wrapped text with its top at [y]; returns the y just below it. */
    private fun drawText(canvas: Canvas, text: CharSequence, paint: TextPaint, x: Float, y: Float, width: Int): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun drawRow(canvas: Canvas, label: String, value: String, y: Float): Float {
        val labelBottom = drawText(canvas, label, labelPaint, MARGIN, y + 1f, LABEL_WIDTH)
        val valueBottom = drawText(canvas, value, bodyPaint, MARGIN + LABEL_WIDTH, y, CONTENT_WIDTH - LABEL_WIDTH)
        return max(labelBottom, valueBottom) + ROW_GAP
    }

    private fun drawRule(canvas: Canvas, y: Float) {
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
    }

    private fun str(id: Int, vararg args: Any): String = resources.getString(id, *args)

    private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    companion object {
        const val PAGE_WIDTH = 612 // US Letter, points
        const val PAGE_HEIGHT = 792
        private const val MARGIN = 54f // 0.75"
        private const val CONTENT_WIDTH = PAGE_WIDTH - 108
        private const val LABEL_WIDTH = 110
        private const val ROW_GAP = 6f
        private const val SECTION_GAP = 14f
        private val GREY = 0xFF555555.toInt()
        private val RULE = 0xFF999999.toInt()

        private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, h:mm a (zzz)", Locale.CANADA)
    }
}
