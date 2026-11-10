package ca.safehaven.intake.data.pdf

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.IntakePdfWriter
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.model.Waiver
import ca.safehaven.intake.domain.model.WaiverClause
import ca.safehaven.intake.domain.validation.DateOfBirthRules
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a signed waiver on US Letter pages with the built-in [PdfDocument] (plan 3.5 / D2-A).
 * Text is real, selectable text. The layout follows the client's 2-page paper form: contact
 * information, the waiver text, the initialed clauses as a table, then the signature.
 *
 * Two passes: the content is first measured into [Block]s and split into pages, then drawn.
 * That way every page can say "Page X of Y", and no block runs off the bottom of a page.
 * A typical waiver fits on 2 pages, like the paper form.
 *
 * Privacy: never log anything from the record.
 */
class AndroidIntakePdfWriter(
    private val resources: Resources,
    private val config: () -> IntakeConfig,
    private val shelterName: String,
    private val appName: String,
    private val appVersion: String,
) : IntakePdfWriter {

    private val shelterPaint = textPaint(16f, Typeface.BOLD)
    private val titlePaint = textPaint(13f)
    private val recordLabelPaint = textPaint(8.5f, color = GREY).apply { textAlign = Paint.Align.RIGHT }
    private val recordPaint = textPaint(15f, Typeface.BOLD).apply { textAlign = Paint.Align.RIGHT }
    private val sectionPaint = textPaint(10f, Typeface.BOLD, GREY).apply { letterSpacing = 0.08f }
    private val labelPaint = textPaint(9.5f, color = GREY)
    private val valuePaint = textPaint(10.5f)
    private val bodyPaint = textPaint(BODY_SIZE)
    private val boldBodyPaint = textPaint(BODY_SIZE, Typeface.BOLD)
    private val tableHeadPaint = textPaint(9.5f, Typeface.BOLD)
    private val initialsPaint = textPaint(12f, Typeface.BOLD)
    private val notePaint = textPaint(8.5f, Typeface.ITALIC, GREY)
    private val smallPaint = textPaint(7.5f, color = GREY)
    private val pageNumberPaint = textPaint(8.5f, color = GREY).apply { textAlign = Paint.Align.RIGHT }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; strokeWidth = 0.75f }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER
        style = Paint.Style.STROKE
        strokeWidth = 0.75f
    }
    private val headerFillPaint = Paint().apply { color = HEADER_FILL; style = Paint.Style.FILL }
    private val signatureLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 0.75f }
    private val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Decoded once; the writer lives as long as the app. Null only if decoding fails. */
    private val logo: Bitmap? by lazy { BitmapFactory.decodeResource(resources, R.drawable.shfa_logo) }

    override fun write(record: IntakeRecord, out: OutputStream) {
        val cfg = config()
        val pages = paginate(buildBlocks(record))
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, blocks ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                drawHeader(canvas, record)
                blocks.forEach { it.block.draw(canvas, it.top) }
                drawFooter(canvas, record, cfg, index + 1, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    // ---- Content ----------------------------------------------------------------------------

    private fun buildBlocks(r: IntakeRecord): List<Block> {
        val blocks = mutableListOf<Block>()
        fun paragraph(text: String, paint: TextPaint = bodyPaint) {
            blocks += TextBlock(layout(text, paint, CONTENT_WIDTH), MARGIN)
            blocks += Gap(PARAGRAPH_GAP)
        }

        blocks += Heading(str(R.string.waiver_pdf_section_contact))
        blocks += Gap(8f)
        blocks += FieldRow(str(R.string.waiver_pdf_name), r.fullName)
        blocks += FieldRow(str(R.string.waiver_pdf_street), r.street)
        blocks += FieldRow(str(R.string.waiver_pdf_city_state_zip), "${r.city}, ${r.state.name} ${r.zip}")
        blocks += FieldRow(str(R.string.waiver_pdf_email), r.email ?: NO_VALUE)
        blocks += FieldRow(str(R.string.waiver_pdf_mobile), r.phoneDisplay)
        blocks += FieldRow(str(R.string.waiver_pdf_reason), reasonLabel(r.reason))
        blocks += FieldRow(str(R.string.waiver_pdf_heard_about), r.heardAbout ?: NO_VALUE)
        blocks += Gap(SECTION_GAP)

        paragraph(Waiver.WARNING, boldBodyPaint)
        paragraph(Waiver.READ_CAREFULLY, boldBodyPaint)
        paragraph(Waiver.ACKNOWLEDGEMENT)
        Waiver.RISKS.forEach { risk ->
            blocks += TextBlock(
                layout(risk, bodyPaint, CONTENT_WIDTH - BULLET_INDENT.toInt()),
                MARGIN + BULLET_INDENT,
                decoration = { canvas, top, l -> canvas.drawText(BULLET, MARGIN + 4f, top + l.getLineBaseline(0), bodyPaint) },
            )
            blocks += Gap(2f)
        }
        blocks += Gap(PARAGRAPH_GAP - 2f)
        paragraph(Waiver.RELEASE)
        paragraph(Waiver.INITIALS_INTRO)

        val tableHeader = TableRow(
            left = layout(str(R.string.waiver_pdf_col_clause), tableHeadPaint, CLAUSE_TEXT_WIDTH),
            right = layout(str(R.string.waiver_pdf_col_initials), tableHeadPaint, INITIALS_TEXT_WIDTH, Layout.Alignment.ALIGN_CENTER),
            isHeader = true,
            repeatHeader = null,
        )
        blocks += tableHeader
        WaiverClause.entries.forEach { clause ->
            blocks += TableRow(
                left = layout(clause.text, bodyPaint, CLAUSE_TEXT_WIDTH),
                right = layout(r.initials[clause].orEmpty(), initialsPaint, INITIALS_TEXT_WIDTH, Layout.Alignment.ALIGN_CENTER),
                isHeader = false,
                repeatHeader = tableHeader,
            )
        }
        blocks += Gap(SECTION_GAP + 10f)
        blocks += SignatureBlock(r)
        return blocks
    }

    private fun reasonLabel(reason: VisitReason): String = str(
        when (reason) {
            VisitReason.ADOPTING -> R.string.waiver_pdf_reason_adopting
            VisitReason.VOLUNTEERING -> R.string.waiver_pdf_reason_volunteering
            VisitReason.VISITING -> R.string.waiver_pdf_reason_visiting
            VisitReason.DROPPING_OFF -> R.string.waiver_pdf_reason_dropping_off
            VisitReason.OTHER -> R.string.waiver_pdf_reason_other
        },
    )

    // ---- Pagination -------------------------------------------------------------------------

    private class Placed(val block: Block, val top: Float)

    /**
     * Flows [blocks] into pages. A block that doesn't fit in what's left of a page moves to the
     * next one; long paragraphs may split between lines instead (at least 2 lines on each side).
     * Gaps at the top of a page are dropped, a block marked [Block.keepWithNext] moves with the
     * start of the next block, and a table row starting a page repeats the table's header row.
     */
    private fun paginate(blocks: List<Block>): List<List<Placed>> {
        val pages = mutableListOf<List<Placed>>()
        var page = mutableListOf<Placed>()
        var y = CONTENT_TOP
        var placedOnPage = 0 // Content blocks only, not a repeated header.
        // java.util.ArrayDeque, not Kotlin's: its pollFirst/addFirst are API 9, so Lint's
        // NewApi check can't confuse them with List.removeFirst (API 35).
        val queue = java.util.ArrayDeque(blocks)

        fun place(block: Block) {
            page += Placed(block, y)
            y += block.height
        }

        fun newPage() {
            pages += page
            page = mutableListOf()
            y = CONTENT_TOP
            placedOnPage = 0
        }

        while (true) {
            val block = queue.pollFirst() ?: break
            if (block is Gap) {
                if (page.isNotEmpty()) y += block.height
                continue
            }
            if (page.isEmpty() && pages.isNotEmpty()) block.repeatHeader?.let(::place)

            val space = CONTENT_BOTTOM - y
            val need = if (block.keepWithNext) block.height + headHeightOfNext(queue) else block.height
            if (need > space && placedOnPage > 0) {
                val parts = if (block.keepWithNext) null else block.split(space, force = false)
                if (parts != null) {
                    place(parts.first)
                    queue.addFirst(parts.second)
                } else {
                    queue.addFirst(block)
                }
                newPage()
                continue
            }
            if (block.height > space) {
                // Taller than a whole page (never happens with the form's length limits).
                val parts = block.split(space, force = true)
                if (parts != null) {
                    place(parts.first)
                    queue.addFirst(parts.second)
                    newPage()
                    continue
                }
            }
            place(block)
            placedOnPage++
        }
        if (page.isNotEmpty()) pages += page
        return pages
    }

    /** Height of the gaps after a keep-with-next block plus the smallest start of the block after them. */
    private fun headHeightOfNext(queue: java.util.ArrayDeque<Block>): Float {
        var height = 0f
        for (next in queue) {
            if (next is Gap) height += next.height else return height + next.minHeadHeight
        }
        return height
    }

    // ---- Blocks -----------------------------------------------------------------------------

    /** A measured piece of content. [draw] puts its top edge at `top`. */
    private abstract class Block {
        abstract val height: Float
        open val keepWithNext: Boolean get() = false

        /** Drawn first when this block starts a new page (a table's header row). */
        open val repeatHeader: Block? get() = null

        /** The smallest top part that can stand on its own at the bottom of a page. */
        open val minHeadHeight: Float get() = height

        abstract fun draw(canvas: Canvas, top: Float)

        /**
         * Splits into a part at most [maxHeight] tall and the rest, or null if this block can't
         * be split there. [force] drops the "at least 2 lines on each side" rule.
         */
        open fun split(maxHeight: Float, force: Boolean): Pair<Block, Block>? = null
    }

    private class Gap(override val height: Float) : Block() {
        override fun draw(canvas: Canvas, top: Float) = Unit
    }

    /** Wrapped text, or the lines [firstLine] until [endLine] of it once split across pages. */
    private class TextBlock(
        private val layout: StaticLayout,
        private val x: Float,
        private val firstLine: Int = 0,
        private val endLine: Int = layout.lineCount,
        /** Drawn with the first line only, e.g. a bullet. */
        private val decoration: ((Canvas, Float, Layout) -> Unit)? = null,
    ) : Block() {
        private val offset = layout.getLineTop(firstLine).toFloat()
        override val height: Float = layout.getLineTop(endLine) - offset
        override val minHeadHeight: Float
            get() = layout.getLineTop(min(firstLine + MIN_SPLIT_LINES, endLine)) - offset

        override fun draw(canvas: Canvas, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            if (firstLine > 0 || endLine < layout.lineCount) {
                // Show only this part's lines; the clip is wide so no glyph edge is cut.
                canvas.clipRect(-x, 0f, PAGE_WIDTH - x, height)
            }
            canvas.translate(0f, -offset)
            layout.draw(canvas)
            canvas.restore()
            if (firstLine == 0) decoration?.invoke(canvas, top, layout)
        }

        override fun split(maxHeight: Float, force: Boolean): Pair<Block, Block>? {
            var fit = firstLine
            while (fit < endLine && layout.getLineBottom(fit) - offset <= maxHeight) fit++
            val minLines = if (force) 1 else MIN_SPLIT_LINES
            if (fit - firstLine < minLines || endLine - fit < minLines) return null
            return TextBlock(layout, x, firstLine, fit, decoration) to TextBlock(layout, x, fit, endLine, decoration)
        }
    }

    /** A section title with a rule under it. Never left alone at the bottom of a page. */
    private inner class Heading(text: String) : Block() {
        private val layout = layout(text, sectionPaint, CONTENT_WIDTH)
        override val height: Float = layout.height + 5f
        override val keepWithNext: Boolean get() = true

        override fun draw(canvas: Canvas, top: Float) {
            drawLayout(canvas, layout, MARGIN, top)
            val ruleY = top + layout.height + 3f
            canvas.drawLine(MARGIN, ruleY, PAGE_WIDTH - MARGIN, ruleY, rulePaint)
        }
    }

    /** "Label   value" with the value on an underline, like a filled-in paper form. */
    private inner class FieldRow(label: String, value: String) : Block() {
        private val labelLayout = layout(label, labelPaint, LABEL_WIDTH - 8)
        private val valueLayout = layout(value, valuePaint, CONTENT_WIDTH - LABEL_WIDTH)
        private val contentHeight = max(labelLayout.height + 1f, valueLayout.height.toFloat())
        override val height: Float = contentHeight + 2f + FIELD_ROW_GAP

        override fun draw(canvas: Canvas, top: Float) {
            drawLayout(canvas, labelLayout, MARGIN, top + 1f)
            drawLayout(canvas, valueLayout, MARGIN + LABEL_WIDTH, top)
            val lineY = top + contentHeight + 2f
            canvas.drawLine(MARGIN + LABEL_WIDTH, lineY, PAGE_WIDTH - MARGIN, lineY, rulePaint)
        }
    }

    /** One bordered row of the "Please Read Carefully | Initials" table. */
    private inner class TableRow(
        private val left: StaticLayout,
        private val right: StaticLayout,
        private val isHeader: Boolean,
        override val repeatHeader: Block?,
    ) : Block() {
        override val height: Float = max(left.height, right.height) + 2 * CELL_PADDING
        override val keepWithNext: Boolean get() = isHeader

        override fun draw(canvas: Canvas, top: Float) {
            val rightEdge = PAGE_WIDTH - MARGIN
            val bottom = top + height
            if (isHeader) canvas.drawRect(MARGIN, top, rightEdge, bottom, headerFillPaint)
            canvas.drawRect(MARGIN, top, rightEdge, bottom, borderPaint)
            canvas.drawLine(DIVIDER_X, top, DIVIDER_X, bottom, borderPaint)
            drawLayout(canvas, left, MARGIN + CELL_PADDING, top + CELL_PADDING)
            // Initials centred in their cell, both ways.
            drawLayout(canvas, right, DIVIDER_X + CELL_PADDING, top + (height - right.height) / 2f)
        }
    }

    /**
     * The drawn signature on a line with its label, beside the date of birth and the date, and
     * the form's footnote under them. Kept together on one page.
     */
    private inner class SignatureBlock(private val r: IntakeRecord) : Block() {
        private val label = layout(
            if (r.isMinor) {
                str(R.string.waiver_pdf_signature_guardian, r.guardianName.orEmpty())
            } else {
                str(R.string.waiver_pdf_signature_adult)
            },
            labelPaint,
            SIGNATURE_WIDTH.toInt(),
        )
        private val dobValue = layout(DateOfBirthRules.formatDisplay(r.dateOfBirth), valuePaint, FIELD_WIDTH)
        private val dobLabel = layout(str(R.string.waiver_pdf_date_of_birth), labelPaint, FIELD_WIDTH)
        private val dateValue = layout(DATE.format(r.signedOn), valuePaint, FIELD_WIDTH)
        private val dateLabel = layout(str(R.string.waiver_pdf_date), labelPaint, FIELD_WIDTH)
        private val note = layout(Waiver.SIGNATURE_NOTE, notePaint, CONTENT_WIDTH)
        private val labelsHeight = maxOf(label.height, dobLabel.height, dateLabel.height).toFloat()
        override val height: Float = SIGNATURE_HEIGHT + LABEL_GAP + labelsHeight + 6f + note.height

        override fun draw(canvas: Canvas, top: Float) {
            val lineY = top + SIGNATURE_HEIGHT
            val labelTop = lineY + LABEL_GAP
            drawSignature(canvas, r.signature, RectF(MARGIN, top, MARGIN + SIGNATURE_WIDTH, lineY - 1f))
            canvas.drawLine(MARGIN, lineY, MARGIN + SIGNATURE_WIDTH, lineY, signatureLinePaint)
            drawLayout(canvas, label, MARGIN, labelTop)

            val dobX = MARGIN + SIGNATURE_WIDTH + FIELD_GAP
            val dateX = dobX + FIELD_WIDTH + FIELD_GAP
            for ((x, value, fieldLabel) in listOf(Triple(dobX, dobValue, dobLabel), Triple(dateX, dateValue, dateLabel))) {
                drawLayout(canvas, value, x, lineY - 3f - value.height)
                canvas.drawLine(x, lineY, x + FIELD_WIDTH, lineY, signatureLinePaint)
                drawLayout(canvas, fieldLabel, x, labelTop)
            }
            drawLayout(canvas, note, MARGIN, labelTop + labelsHeight + 6f)
        }
    }

    /**
     * Scales the strokes uniformly into [box], keeping the pad's aspect ratio, left-aligned and
     * sitting on the box's bottom edge (the signature line).
     */
    private fun drawSignature(canvas: Canvas, signature: Signature, box: RectF) {
        val points = signature.strokes.flatten()
        if (points.isEmpty()) return
        val padWidth = if (signature.width > 0f) signature.width else max(1f, points.maxOf { it.x })
        val padHeight = if (signature.height > 0f) signature.height else max(1f, points.maxOf { it.y })
        val scale = min(box.width() / padWidth, box.height() / padHeight)
        val left = box.left
        val top = box.bottom - padHeight * scale

        val path = Path()
        for (stroke in signature.strokes) {
            if (stroke.isEmpty()) continue
            val first = stroke[0]
            path.moveTo(left + first.x * scale, top + first.y * scale)
            if (stroke.size == 1) {
                // A tap: a tiny segment, which the round cap turns into a dot.
                path.lineTo(left + first.x * scale + 0.01f, top + first.y * scale)
            } else {
                for (i in 1 until stroke.size) path.lineTo(left + stroke[i].x * scale, top + stroke[i].y * scale)
            }
        }
        canvas.save()
        canvas.clipRect(box.left - 2f, box.top - 2f, box.right + 2f, box.bottom + 2f)
        canvas.drawPath(path, signaturePaint)
        canvas.restore()
    }

    // ---- Header and footer ------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, r: IntakeRecord) {
        var textX = MARGIN
        logo?.let { bitmap ->
            val width = LOGO_HEIGHT * bitmap.width / bitmap.height
            canvas.drawBitmap(bitmap, null, RectF(MARGIN, TOP_MARGIN, MARGIN + width, TOP_MARGIN + LOGO_HEIGHT), logoPaint)
            textX = MARGIN + width + 12f
        }
        val nameBaseline = TOP_MARGIN + 25f
        canvas.drawText(shelterName, textX, nameBaseline, shelterPaint)
        canvas.drawText(str(R.string.waiver_pdf_title), textX, nameBaseline + 19f, titlePaint)

        val right = PAGE_WIDTH - MARGIN
        canvas.drawText(str(R.string.waiver_pdf_record_label), right, TOP_MARGIN + 21f, recordLabelPaint)
        canvas.drawText(r.recordCode, right, TOP_MARGIN + 41f, recordPaint)

        canvas.drawLine(MARGIN, HEADER_RULE_Y, PAGE_WIDTH - MARGIN, HEADER_RULE_Y, rulePaint)
    }

    private fun drawFooter(canvas: Canvas, r: IntakeRecord, cfg: IntakeConfig, pageNumber: Int, pageCount: Int) {
        canvas.drawLine(MARGIN, FOOTER_RULE_Y, PAGE_WIDTH - MARGIN, FOOTER_RULE_Y, rulePaint)
        val createdAt = CREATED_AT.format(r.createdAt.atZone(ZoneId.of(cfg.timeZoneId)))
        val meta = str(R.string.waiver_pdf_footer, r.recordCode, r.waiverVersion, cfg.deviceLabel, appName, appVersion, createdAt)
        val fitted = TextUtils.ellipsize(meta, smallPaint, CONTENT_WIDTH.toFloat(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, MARGIN, FOOTER_RULE_Y + 11f, smallPaint)
        canvas.drawText(str(R.string.waiver_pdf_confidential), MARGIN, FOOTER_RULE_Y + 22f, smallPaint)
        canvas.drawText(
            str(R.string.waiver_pdf_page, pageNumber, pageCount),
            PAGE_WIDTH - MARGIN, FOOTER_RULE_Y + 22f, pageNumberPaint,
        )
    }

    // ---- Helpers ----------------------------------------------------------------------------

    /** StaticLayout.Builder is API 23, the app's minSdk. */
    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(LINE_SPACING_EXTRA, 1f)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

    private fun drawLayout(canvas: Canvas, layout: Layout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun str(id: Int, vararg args: Any): String = resources.getString(id, *args)

    private fun textPaint(size: Float, style: Int = Typeface.NORMAL, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
        }

    companion object {
        const val PAGE_WIDTH = 612 // US Letter, points
        const val PAGE_HEIGHT = 792

        private const val MARGIN = 54f // 0.75" left and right
        private const val TOP_MARGIN = 40f
        private const val BOTTOM_MARGIN = 36f
        private const val CONTENT_WIDTH = PAGE_WIDTH - 108

        private const val LOGO_HEIGHT = 54f
        private const val HEADER_RULE_Y = TOP_MARGIN + LOGO_HEIGHT + 8f
        private const val CONTENT_TOP = HEADER_RULE_Y + 14f
        private const val FOOTER_RULE_Y = PAGE_HEIGHT - BOTTOM_MARGIN - 26f
        private const val CONTENT_BOTTOM = FOOTER_RULE_Y - 12f

        private const val BODY_SIZE = 10f
        private const val LINE_SPACING_EXTRA = 1.5f
        private const val PARAGRAPH_GAP = 7f
        private const val SECTION_GAP = 14f
        private const val MIN_SPLIT_LINES = 2

        private const val LABEL_WIDTH = 176
        private const val FIELD_ROW_GAP = 7f

        private const val BULLET = "•"
        private const val BULLET_INDENT = 16f

        private const val INITIALS_COLUMN_WIDTH = 76f
        private const val CELL_PADDING = 5f
        private const val DIVIDER_X = PAGE_WIDTH - MARGIN - INITIALS_COLUMN_WIDTH
        private const val CLAUSE_TEXT_WIDTH = (CONTENT_WIDTH - INITIALS_COLUMN_WIDTH - 2 * CELL_PADDING).toInt()
        private const val INITIALS_TEXT_WIDTH = (INITIALS_COLUMN_WIDTH - 2 * CELL_PADDING).toInt()

        private const val SIGNATURE_WIDTH = 300f
        private const val SIGNATURE_HEIGHT = 90f
        private const val LABEL_GAP = 3f
        private const val FIELD_GAP = 16f
        private const val FIELD_WIDTH = ((CONTENT_WIDTH - SIGNATURE_WIDTH - 2 * FIELD_GAP) / 2).toInt()

        /** Shown for optional fields left blank. */
        private const val NO_VALUE = "—"

        private val GREY = 0xFF555555.toInt()
        private val RULE = 0xFF999999.toInt()
        private val BORDER = 0xFF444444.toInt()
        private val HEADER_FILL = 0xFFEEEEEE.toInt()

        private val DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
        private val CREATED_AT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a z", Locale.US)
    }
}
