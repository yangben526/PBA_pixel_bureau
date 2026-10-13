package app.pixelbureau.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Soft pixel / Kairosoft-friendly syntax colors on dark agent bubbles. */
private object CodeDark {
    val bg = Color(0xFF25221E)
    val border = Color(0xFF5A5040)
    val plain = Color(0xFFEDE4D0)
    val keyword = Color(0xFFE2A23A)
    val string = Color(0xFF8FBF78)
    val comment = Color(0xFF8A7E6A)
    val number = Color(0xFF7EB8D4)
    val type = Color(0xFFC9A0E8)
    val punct = Color(0xFFB8A890)
    val langTag = Color(0xFFC8B89A)
}

/** Darker tokens on the amber user bubble. */
private object CodeLight {
    val bg = Color(0x33FFFFFF)
    val border = Color(0xFFB87A20)
    val plain = Color(0xFF161410)
    val keyword = Color(0xFF8B4518)
    val string = Color(0xFF2E6B3A)
    val comment = Color(0xFF6A5A40)
    val number = Color(0xFF1A5A80)
    val type = Color(0xFF5A3A8A)
    val punct = Color(0xFF4A3A28)
    val langTag = Color(0xFF5A4A30)
}

private data class TablePalette(
    val card: Color,
    val headerBg: Color,
    val rowAlt: Color,
    val headerText: Color,
    val value: Color,
    val muted: Color,
    val line: Color,
)

private fun tablePalette(onAgentBubble: Boolean): TablePalette =
    if (onAgentBubble) {
        TablePalette(
            card = Color(0xFF3A342C),
            headerBg = Color(0xFF5C4A32),
            rowAlt = Color(0xFF453E36),
            headerText = Color(0xFFFFF0C2),
            value = Color(0xFFF7F1E6),
            muted = CreamDim,
            line = Color(0xFF8A7355),
        )
    } else {
        TablePalette(
            card = Color(0xFFFFF8EC),
            headerBg = Color(0xFFE8B04A),
            rowAlt = Color(0xFFFFF0D0),
            headerText = Color(0xFF2A1C08),
            value = Ink,
            muted = Color(0xFF8A6A3A),
            line = Color(0xFFC4923A),
        )
    }

internal sealed class ChatBlock {
    data class Paragraph(val text: String) : ChatBlock()
    data class Heading(val level: Int, val text: String) : ChatBlock()
    data class Code(val language: String, val code: String) : ChatBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ChatBlock()
}

/**
 * Split LLM markdown into paragraphs, fenced code, and pipe tables.
 * Incomplete streaming fences stay as paragraphs until closed.
 */
internal fun parseChatBlocks(raw: String): List<ChatBlock> {
    if (raw.isEmpty()) return emptyList()
    val out = mutableListOf<ChatBlock>()
    val lines = raw.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        val t = para.toString().trimEnd()
        para.clear()
        if (t.isNotBlank()) {
            out += splitTextAndTables(t)
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val fence = fenceOpen(line)
        if (fence != null) {
            flushPara()
            val lang = fence
            val body = StringBuilder()
            i++
            var closed = false
            while (i < lines.size) {
                if (lines[i].trimStart().startsWith("```")) {
                    closed = true
                    i++
                    break
                }
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
            if (closed || body.isNotEmpty()) {
                out += ChatBlock.Code(normalizeLang(lang), body.toString())
            } else {
                para.append(line).append('\n')
            }
            continue
        }
        if (para.isNotEmpty()) para.append('\n')
        para.append(line)
        i++
    }
    flushPara()
    return out
}

private fun fenceOpen(line: String): String? {
    val t = line.trimStart()
    if (!t.startsWith("```")) return null
    val rest = t.removePrefix("```").trim()
    return rest.substringBefore(' ').substringBefore('{').trim().lowercase()
}

private fun normalizeLang(raw: String): String {
    val l = raw.lowercase().trim()
    return when (l) {
        "golang" -> "go"
        "c++", "cplusplus", "cc", "cxx" -> "cpp"
        "c#" -> "csharp"
        "js", "node" -> "javascript"
        "ts" -> "typescript"
        "py" -> "python"
        "tf", "terraform" -> "hcl"
        "sh", "zsh", "bash", "shell" -> "bash"
        "kt" -> "kotlin"
        "yml" -> "yaml"
        "rs" -> "rust"
        "plaintext", "text", "txt" -> ""
        else -> l
    }
}

private fun splitTextAndTables(text: String): List<ChatBlock> {
    val lines = text.split('\n')
    val out = mutableListOf<ChatBlock>()
    var i = 0
    val buf = StringBuilder()

    fun flushBuf() {
        val t = buf.toString().trimEnd()
        buf.clear()
        if (t.isNotBlank()) out += ChatBlock.Paragraph(t)
    }

    while (i < lines.size) {
        val heading = headingLine(lines[i])
        if (heading != null) {
            flushBuf()
            out += ChatBlock.Heading(heading.first, heading.second)
            i++
            continue
        }
        if (tableStartsAt(lines, i)) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && isTableLine(lines[i])) {
                tableLines += lines[i]
                i++
            }
            val table = parsePipeTable(tableLines)
            if (table != null && table.headers.isNotEmpty()) {
                flushBuf()
                out += table
            } else {
                for (tl in tableLines) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(tl)
                }
            }
            continue
        }
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(lines[i])
        i++
    }
    flushBuf()
    return out
}

/**
 * An ATX heading on its own line: `## Title`. A hash without a space stays text,
 * so a hashtag or `##no-space` is not a heading.
 */
internal fun headingLine(line: String): Pair<Int, String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed[0] != '#') {
        return null
    }
    var level = 0
    while (level < trimmed.length && level < 6 && trimmed[level] == '#') {
        level++
    }
    if (level == 0 || level >= trimmed.length) {
        return null
    }
    if (trimmed[level] != ' ' && trimmed[level] != '\t') {
        return null
    }
    val title = trimmed.substring(level)
        .trim()
        .replace(Regex("""\s+#+\s*$"""), "")
        .trim()
    if (title.isEmpty()) {
        return null
    }
    return level to title
}

private fun isLeadingPipeRow(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3 || !t.startsWith('|')) return false
    return t.count { it == '|' } >= 2 && !isTableSep(t)
}

/** A pipe row, including a single outer-piped cell and GFM rows that omit the outer pipes. */
private fun isPipeLine(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3 || !t.contains('|')) return false
    if (t.startsWith('|') && t.count { it == '|' } >= 2) return true
    return t.trim('|').split('|').size >= 2
}

private fun isTableSep(line: String): Boolean {
    val t = line.trim()
    if (!t.contains('|') || !t.contains('-')) return false
    val cells = t.trim('|').split('|')
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' }
    }
}

private fun isTableLine(line: String): Boolean = isPipeLine(line) || isTableSep(line)

/**
 * A leading-pipe row starts a table on its own. A row without outer pipes
 * starts one only when the next line is a real separator, so a prose "|" or
 * a markdown rule stays text.
 */
private fun tableStartsAt(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    if (isLeadingPipeRow(line)) return true
    if (!isPipeLine(line) || isTableSep(line)) return false
    val next = index + 1
    return next < lines.size && isTableSep(lines[next])
}

private fun splitPipeRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith('|')) t = t.drop(1)
    if (t.endsWith('|') && !t.endsWith("\\|")) t = t.dropLast(1)
    val cells = mutableListOf<String>()
    val cur = StringBuilder()
    var i = 0
    while (i < t.length) {
        if (t[i] == '\\' && i + 1 < t.length && t[i + 1] == '|') {
            cur.append('|')
            i += 2
            continue
        }
        if (t[i] == '|') {
            cells += tidyTableCell(cur.toString())
            cur.clear()
            i++
            continue
        }
        cur.append(t[i])
        i++
    }
    cells += tidyTableCell(cur.toString())
    return cells
}

/** Turn table-cell HTML and escaped pipes into readable text. */
internal fun tidyTableCell(raw: String): String {
    if (raw.isEmpty()) return ""
    return raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()
}

private fun parsePipeTable(lines: List<String>): ChatBlock.Table? {
    val dataLines = lines.filterNot { isTableSep(it) }
    if (dataLines.isEmpty()) return null
    val header = splitPipeRow(dataLines.first())
    if (header.isEmpty() || header.all { it.isEmpty() }) return null
    val rows = dataLines.drop(1).map { splitPipeRow(it) }.map { row ->
        List(header.size) { idx -> row.getOrElse(idx) { "" } }
    }
    return ChatBlock.Table(header, rows)
}

/**
 * Column widths for a real grid. Each column is the widest cell in it, held
 * between [minPx] and [maxPx]. Leftover room in the bubble is shared so a
 * short table still fills the row. A wider table keeps these widths and scrolls.
 */
internal fun tableColumnWidths(
    contentPx: List<Float>,
    availablePx: Float,
    minPx: Float,
    maxPx: Float,
): List<Float> {
    if (contentPx.isEmpty()) return emptyList()
    val minW = if (minPx.isFinite()) minPx.coerceAtLeast(0f) else 0f
    val maxW = if (maxPx.isFinite()) maxPx.coerceAtLeast(minW) else minW
    val fitted = contentPx.map { raw ->
        val w = if (raw.isFinite()) raw else minW
        w.coerceIn(minW, maxW)
    }
    val sum = fitted.sum()
    if (!availablePx.isFinite() || availablePx <= sum) return fitted
    val extra = (availablePx - sum) / fitted.size
    return fitted.map { it + extra }
}

@Composable
internal fun ChatMarkdownContent(
    text: String,
    onAgentBubble: Boolean,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseChatBlocks(text) }
    val bodyColor = if (onAgentBubble) Color.White else Ink
    val weight = if (onAgentBubble) FontWeight.SemiBold else FontWeight.Bold
    Column(modifier) {
        blocks.forEachIndexed { idx, block ->
            val topPad = if (idx == 0) 0.dp else 6.dp
            when (block) {
                is ChatBlock.Paragraph -> {
                    val styled = remember(block.text, bodyColor, weight) {
                        styledInlineMarkdown(block.text, bodyColor, weight)
                    }
                    Text(
                        text = styled,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = topPad),
                    )
                }
                is ChatBlock.Heading -> {
                    MarkdownHeading(
                        level = block.level,
                        text = block.text,
                        onAgentBubble = onAgentBubble,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 10.dp),
                    )
                }
                is ChatBlock.Code -> {
                    CodeFenceBlock(
                        language = block.language,
                        code = block.code,
                        onAgentBubble = onAgentBubble,
                        modifier = Modifier.padding(top = topPad),
                    )
                }
                is ChatBlock.Table -> {
                    MarkdownTableBlock(
                        table = block,
                        onAgentBubble = onAgentBubble,
                        modifier = Modifier.padding(top = topPad),
                    )
                }
            }
        }
    }
}

/** Section title. `##` is the usual model heading: larger, bold, with a short amber tick. */
@Composable
private fun MarkdownHeading(
    level: Int,
    text: String,
    onAgentBubble: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        onAgentBubble && level <= 2 -> Color(0xFFFFF0C2)
        onAgentBubble -> Color.White
        else -> Ink
    }
    val size = when (level) {
        1 -> 18.sp
        2 -> 16.sp
        else -> 15.sp
    }
    val line = when (level) {
        1 -> 22.sp
        2 -> 20.sp
        else -> 19.sp
    }
    val styled = remember(text, color) {
        styledInlineMarkdown(text, color, FontWeight.Bold)
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = styled,
            fontSize = size,
            lineHeight = line,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
        )
        if (level <= 2) {
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .width(if (level == 1) 36.dp else 28.dp)
                    .height(2.dp)
                    .background(if (onAgentBubble) Amber else Color(0xFFC4923A)),
            )
        }
    }
}

/**
 * Lightweight chat lint: **bold** / __bold__, *italics* / _italics_.
 * Markers are stripped; unmatched markers stay literal. Soft Kairosoft weights.
 */
internal fun styledInlineMarkdown(
    raw: String,
    color: Color,
    baseWeight: FontWeight,
): AnnotatedString {
    if (raw.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        var i = 0
        val n = raw.length
        fun emitPlain(from: Int, to: Int) {
            if (to <= from) return
            withStyle(SpanStyle(color = color, fontWeight = baseWeight)) {
                append(raw.substring(from, to))
            }
        }
        fun emitStyled(from: Int, to: Int, bold: Boolean, italic: Boolean) {
            if (to <= from) return
            val w = if (bold) FontWeight.Bold else baseWeight
            val style = if (italic) FontStyle.Italic else FontStyle.Normal
            withStyle(SpanStyle(color = color, fontWeight = w, fontStyle = style)) {
                append(raw.substring(from, to))
            }
        }
        while (i < n) {
            // Prefer double markers first so ** doesn't become two italics.
            val double = when {
                raw.startsWith("**", i) -> "**"
                raw.startsWith("__", i) -> "__"
                else -> null
            }
            if (double != null) {
                val close = raw.indexOf(double, i + 2)
                if (close > i + 1) {
                    emitStyled(i + 2, close, bold = true, italic = false)
                    i = close + 2
                    continue
                }
            }
            val single = when {
                raw[i] == '*' -> "*"
                raw[i] == '_' -> "_"
                else -> null
            }
            if (single != null) {
                // Avoid matching mid-token underscores like snake_case.
                val prevOk = i == 0 || !raw[i - 1].isLetterOrDigit()
                if (prevOk || single == "*") {
                    val close = raw.indexOf(single, i + 1)
                    if (close > i) {
                        val nextOk = close + 1 >= n || !raw[close + 1].isLetterOrDigit()
                        val inner = raw.substring(i + 1, close)
                        if (inner.isNotEmpty() && !inner.contains('\n') && (single == "*" || nextOk)) {
                            emitStyled(i + 1, close, bold = false, italic = true)
                            i = close + 1
                            continue
                        }
                    }
                }
            }
            // Consume one plain char (batch later for fewer spans).
            var j = i + 1
            while (j < n) {
                val c = raw[j]
                if (c == '*' || c == '_') break
                j++
            }
            emitPlain(i, j)
            i = j
        }
    }
}

@Composable
private fun CodeFenceBlock(
    language: String,
    code: String,
    onAgentBubble: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = if (onAgentBubble) CodeDark.bg else CodeLight.bg
    val border = if (onAgentBubble) CodeDark.border else CodeLight.border
    val langTag = if (onAgentBubble) CodeDark.langTag else CodeLight.langTag
    val shape = RoundedCornerShape(8.dp)
    val highlighted = remember(code, language, onAgentBubble) {
        highlightCode(code, language, onAgentBubble)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (language.isNotBlank()) {
            Text(
                language,
                color = langTag,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        SelectionContainer {
            Text(
                text = highlighted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: ChatBlock.Table,
    onAgentBubble: Boolean,
    modifier: Modifier = Modifier,
) {
    val tone = tablePalette(onAgentBubble)
    val shape = RoundedCornerShape(10.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.SansSerif,
    )
    val bodyStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.card)
            .border(1.dp, tone.line, shape),
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val colWidths = remember(table, maxWidthPx) {
            val minPx = with(density) { 72.dp.toPx() }
            val maxPx = with(density) { 220.dp.toPx() }
            val padPx = with(density) { 24.dp.toPx() }
            fun contentWidth(text: String, style: TextStyle): Float {
                val shown = text.ifBlank { "—" }
                return shown.split('\n').maxOf { line ->
                    measurer.measure(
                        text = AnnotatedString(line.ifEmpty { " " }),
                        style = style,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = 10_000),
                    ).size.width.toFloat()
                }
            }
            val measured = List(table.headers.size) { col ->
                var widest = contentWidth(table.headers[col], headerStyle)
                for (row in table.rows) {
                    widest = maxOf(widest, contentWidth(row.getOrElse(col) { "" }, bodyStyle))
                }
                widest + padPx
            }
            tableColumnWidths(measured, maxWidthPx, minPx, maxPx).map { px ->
                with(density) { px.toDp() }
            }
        }
        SelectionContainer {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableGridRow(
                    cells = table.headers,
                    widths = colWidths,
                    background = tone.headerBg,
                    textColor = tone.headerText,
                    blankColor = tone.headerText,
                    weight = FontWeight.SemiBold,
                    lineColor = tone.line,
                    drawBottom = table.rows.isNotEmpty(),
                    dashBlanks = false,
                )
                table.rows.forEachIndexed { index, row ->
                    val cells = List(table.headers.size) { col -> row.getOrElse(col) { "" } }
                    TableGridRow(
                        cells = cells,
                        widths = colWidths,
                        background = if (index % 2 == 0) tone.card else tone.rowAlt,
                        textColor = tone.value,
                        blankColor = tone.muted,
                        weight = FontWeight.Normal,
                        lineColor = tone.line,
                        drawBottom = index != table.rows.lastIndex,
                        dashBlanks = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableGridRow(
    cells: List<String>,
    widths: List<Dp>,
    background: Color,
    textColor: Color,
    blankColor: Color,
    weight: FontWeight,
    lineColor: Color,
    drawBottom: Boolean,
    dashBlanks: Boolean,
) {
    Row(Modifier.height(IntrinsicSize.Max), verticalAlignment = Alignment.Top) {
        cells.forEachIndexed { col, raw ->
            val blank = raw.isBlank()
            val shown = when {
                !blank -> raw
                dashBlanks -> "—"
                else -> " "
            }
            val last = col == cells.lastIndex
            Box(
                Modifier
                    .width(widths.getOrElse(col) { 72.dp })
                    .fillMaxHeight()
                    .background(background)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        if (!last) {
                            drawLine(
                                lineColor,
                                Offset(size.width - stroke / 2f, 0f),
                                Offset(size.width - stroke / 2f, size.height),
                                stroke,
                            )
                        }
                        if (drawBottom) {
                            drawLine(
                                lineColor,
                                Offset(0f, size.height - stroke / 2f),
                                Offset(size.width, size.height - stroke / 2f),
                                stroke,
                            )
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                TableBodyText(
                    text = shown,
                    color = if (blank) blankColor else textColor,
                    weight = weight,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun TableBodyText(
    text: String,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 20.sp,
) {
    val styled = remember(text, color, weight) {
        styledInlineMarkdown(text, color, weight)
    }
    Text(
        text = styled,
        fontSize = fontSize,
        fontFamily = FontFamily.SansSerif,
        lineHeight = lineHeight,
        softWrap = true,
        modifier = modifier,
    )
}

private data class CodePal(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val punct: Color,
)

private fun paletteFor(onAgentBubble: Boolean): CodePal =
    if (onAgentBubble) {
        CodePal(
            plain = CodeDark.plain,
            keyword = CodeDark.keyword,
            string = CodeDark.string,
            comment = CodeDark.comment,
            number = CodeDark.number,
            type = CodeDark.type,
            punct = CodeDark.punct,
        )
    } else {
        CodePal(
            plain = CodeLight.plain,
            keyword = CodeLight.keyword,
            string = CodeLight.string,
            comment = CodeLight.comment,
            number = CodeLight.number,
            type = CodeLight.type,
            punct = CodeLight.punct,
        )
    }

private fun highlightCode(code: String, language: String, onAgentBubble: Boolean): AnnotatedString {
    val pal = paletteFor(onAgentBubble)
    val keywords = keywordsFor(language)
    val lineComment = lineCommentFor(language)
    val blockComments = blockCommentFor(language)
    val tripleDq = "\"\"\""
    val tripleSq = "'''"

    return buildAnnotatedString {
        var i = 0
        val n = code.length
        fun emit(from: Int, to: Int, color: Color) {
            if (to <= from) return
            withStyle(SpanStyle(color = color)) {
                append(code.substring(from, to))
            }
        }
        while (i < n) {
            var matched = false
            for ((open, close) in blockComments) {
                if (code.startsWith(open, i)) {
                    val end = code.indexOf(close, i + open.length).let { if (it < 0) n else it + close.length }
                    emit(i, end, pal.comment)
                    i = end
                    matched = true
                    break
                }
            }
            if (matched) continue

            if (lineComment != null && code.startsWith(lineComment, i)) {
                var end = i
                while (end < n && code[end] != '\n') end++
                emit(i, end, pal.comment)
                i = end
                continue
            }

            val ch = code[i]
            if (ch == '"' || ch == '\'' || ch == '`') {
                if (ch == '"' && code.startsWith(tripleDq, i)) {
                    val end = code.indexOf(tripleDq, i + 3).let { if (it < 0) n else it + 3 }
                    emit(i, end, pal.string)
                    i = end
                    continue
                }
                if (ch == '\'' && code.startsWith(tripleSq, i)) {
                    val end = code.indexOf(tripleSq, i + 3).let { if (it < 0) n else it + 3 }
                    emit(i, end, pal.string)
                    i = end
                    continue
                }
                val quote = ch
                var j = i + 1
                while (j < n) {
                    val c = code[j]
                    if (c == '\\' && j + 1 < n) {
                        j += 2
                        continue
                    }
                    if (c == quote) {
                        j++
                        break
                    }
                    if (c == '\n' && quote != '`') break
                    j++
                }
                emit(i, j, pal.string)
                i = j
                continue
            }

            if (ch.isDigit() || (ch == '.' && i + 1 < n && code[i + 1].isDigit())) {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                emit(i, j, pal.number)
                i = j
                continue
            }

            if (ch.isLetter() || ch == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                val color = when {
                    word in keywords -> pal.keyword
                    looksLikeType(word, language) -> pal.type
                    else -> pal.plain
                }
                emit(i, j, color)
                i = j
                continue
            }

            if (ch in "{}[]().,;:<>+-*/%&=!|?") {
                emit(i, i + 1, pal.punct)
            } else {
                emit(i, i + 1, pal.plain)
            }
            i++
        }
    }
}

private fun looksLikeType(word: String, language: String): Boolean {
    if (word.isEmpty()) return false
    val builtins = when (language) {
        "python" -> setOf("int", "str", "float", "bool", "list", "dict", "tuple", "set", "None", "True", "False")
        "go" -> setOf("string", "int", "int64", "int32", "float64", "bool", "byte", "error", "any", "rune")
        "typescript", "javascript" -> setOf("string", "number", "boolean", "void", "any", "never", "object")
        "kotlin", "java", "csharp" -> setOf("String", "Int", "Boolean", "Unit", "Long", "Double", "Float", "List", "Map")
        "cpp", "c" -> setOf("int", "char", "bool", "void", "float", "double", "size_t", "string", "auto")
        "hcl" -> setOf("string", "number", "bool", "list", "map", "object", "any")
        "rust" -> setOf("String", "str", "i32", "i64", "u32", "u64", "f32", "f64", "bool", "usize", "Vec", "Option", "Result")
        else -> emptySet()
    }
    if (word in builtins) return true
    return word[0].isUpperCase() && word.any { it.isLowerCase() }
}

private fun lineCommentFor(language: String): String? = when (language) {
    "python", "bash", "yaml", "toml", "hcl", "ruby" -> "#"
    "haskell", "lua", "sql" -> "--"
    "" -> null
    else -> "//"
}

private fun blockCommentFor(language: String): List<Pair<String, String>> = when (language) {
    "python" -> listOf("\"\"\"" to "\"\"\"", "'''" to "'''")
    "html", "xml", "markdown", "md" -> listOf("<!--" to "-->")
    "bash", "yaml", "toml", "hcl" -> emptyList()
    "" -> listOf("/*" to "*/")
    else -> listOf("/*" to "*/")
}

private fun keywordsFor(language: String): Set<String> {
    val commonC = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
        "return", "class", "struct", "enum", "public", "private", "protected",
        "static", "const", "void", "new", "this", "super", "try", "catch", "finally",
        "throw", "throws", "import", "package", "true", "false", "null", "default",
    )
    return when (language) {
        "python" -> setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not",
            "and", "or", "is", "None", "True", "False", "import", "from", "as", "with",
            "try", "except", "finally", "raise", "yield", "lambda", "pass", "break",
            "continue", "global", "nonlocal", "assert", "async", "await", "del",
        )
        "go" -> setOf(
            "func", "package", "import", "var", "const", "type", "struct", "interface",
            "map", "chan", "go", "defer", "return", "if", "else", "for", "range",
            "switch", "case", "default", "break", "continue", "select", "fallthrough",
            "true", "false", "nil", "make", "new", "len", "cap",
        )
        "cpp", "c" -> commonC + setOf(
            "namespace", "template", "typename", "using", "sizeof", "typedef",
            "inline", "virtual", "override", "explicit", "friend", "operator",
            "include", "ifdef", "ifndef", "define", "endif", "nullptr",
        )
        "javascript", "typescript" -> setOf(
            "function", "const", "let", "var", "return", "if", "else", "for", "while",
            "switch", "case", "break", "continue", "class", "extends", "new", "this",
            "super", "import", "export", "from", "default", "async", "await", "try",
            "catch", "finally", "throw", "typeof", "instanceof", "true", "false",
            "null", "undefined", "of", "in", "interface", "type", "enum", "implements",
        )
        "kotlin" -> setOf(
            "fun", "val", "var", "class", "object", "interface", "data", "sealed",
            "when", "if", "else", "for", "while", "do", "return", "break", "continue",
            "in", "is", "as", "null", "true", "false", "import", "package", "typealias",
            "companion", "override", "open", "abstract", "private", "public", "internal",
            "protected", "suspend", "try", "catch", "finally", "throw",
        )
        "java", "csharp" -> commonC + setOf(
            "namespace", "using", "async", "await", "var", "record", "sealed",
            "override", "virtual", "abstract", "interface", "implements", "extends",
        )
        "hcl" -> setOf(
            "resource", "provider", "variable", "output", "module", "locals", "data",
            "terraform", "backend", "required_providers", "true", "false", "null",
            "for", "in", "if", "else", "dynamic", "count", "depends_on",
        )
        "rust" -> setOf(
            "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "pub",
            "use", "mod", "crate", "self", "super", "return", "if", "else", "match",
            "loop", "while", "for", "in", "break", "continue", "async", "await",
            "true", "false", "where", "type", "static", "unsafe", "ref", "move",
        )
        "bash" -> setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
            "esac", "function", "return", "in", "local", "export", "readonly", "true",
            "false",
        )
        "sql" -> setOf(
            "select", "from", "where", "join", "left", "right", "inner", "outer",
            "on", "group", "by", "order", "asc", "desc", "insert", "into", "values",
            "update", "set", "delete", "create", "table", "index", "and", "or", "not",
            "null", "as", "limit", "offset", "having", "distinct", "union",
        )
        "yaml", "toml" -> setOf("true", "false", "null", "yes", "no")
        else -> commonC + setOf("def", "fun", "func", "let", "var", "val", "None", "nil")
    }
}
