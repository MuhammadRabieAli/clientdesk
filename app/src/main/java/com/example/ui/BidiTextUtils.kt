package com.example.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

private const val UNICODE_RLM = "\u200F"
private const val UNICODE_FSI = "\u2068"
private const val UNICODE_PDI = "\u2069"

/**
 * Determines whether [this] string should be laid out and aligned as Right-to-Left (e.g. Arabic).
 *
 * Uses Unicode strong directionality:
 * - If the first strong character is RTL (Arabic, Hebrew, etc.), returns true.
 * - For mixed strings starting with an LTR token (e.g. "API ربط السيرفر"), returns true
 *   when RTL characters outnumber LTR characters.
 */
fun String.isRtlText(): Boolean {
    if (isEmpty()) return false

    // Fast path: all RTL scripts in Unicode start at U+0590 (Hebrew/Arabic/etc.)
    var hasPotentialRtl = false
    for (idx in 0 until length) {
        if (this[idx].code >= 0x0590) {
            hasPotentialRtl = true
            break
        }
    }
    if (!hasPotentialRtl) return false

    var firstStrong: Byte? = null
    var rtlCount = 0
    var ltrCount = 0

    var i = 0
    while (i < length) {
        val codePoint = codePointAt(i)
        i += Character.charCount(codePoint)
        when (val dir = Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> {
                if (firstStrong == null) firstStrong = dir
                rtlCount++
            }

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> {
                if (firstStrong == null) firstStrong = dir
                ltrCount++
            }
        }
    }

    if (rtlCount == 0) return false
    return when (firstStrong) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> true
        else -> rtlCount > ltrCount
    }
}

/**
 * Explicit horizontal alignment based on text script: Right for Arabic/RTL, Left for LTR.
 * Uses Right/Left rather than End/Start so Arabic remains right-aligned regardless of device locale.
 */
fun String.contentTextAlign(): TextAlign =
    if (isRtlText()) TextAlign.Right else TextAlign.Left

fun String.contentTextDirection(): TextDirection =
    if (isRtlText()) TextDirection.Rtl else TextDirection.Ltr

private val WHITESPACE_REGEX = Regex("\\s+")

fun TextStyle.withBidi(isRtl: Boolean): TextStyle = copy(
    textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr,
    textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
)

fun TextStyle.withBidi(text: String): TextStyle = withBidi(text.isRtlText())

fun TextStyle.withBidiCentered(text: String): TextStyle = copy(
    textDirection = text.contentTextDirection(),
    textAlign = TextAlign.Center
)

/**
 * Wraps user text in Unicode First-Strong Isolate (FSI..PDI) when interpolating into
 * localized UI strings (e.g. "Add a task for %1$s…") so Arabic names do not reorder
 * surrounding punctuation or English words.
 */
fun bidiIsolate(text: String): String =
    if (text.isBlank()) text else "$UNICODE_FSI$text$UNICODE_PDI"

/**
 * Ensures Android XML TextViews using textDirection="firstStrong" + textAlignment="textStart"
 * resolve mixed Arabic strings (such as "API ربط السيرفر") as RTL and align them to the right.
 */
fun formatWidgetBidiText(text: String): String {
    if (text.isBlank()) return text
    return if (text.isRtlText()) "$UNICODE_RLM$text" else text
}

/**
 * Builds clean avatar initials for both Latin and Arabic client names.
 * For Arabic names, avoids joining the first two letters into an unintended 2-letter word.
 */
fun clientInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "CL"
    val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    return if (trimmed.isRtlText()) {
        if (words.size >= 2) {
            "${words[0].first()} ${words[1].first()}"
        } else {
            words[0].first().toString()
        }
    } else {
        if (words.size >= 2) {
            "${words[0].first()}${words[1].first()}".uppercase()
        } else {
            trimmed.take(2).uppercase()
        }
    }
}
