package com.havivon.pigeon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Layouts that keep working with a large font and a large display size (a narrow screen in dp): what does not fit
 * beside something moves under it or gets shorter, and never overlaps or gets clipped. The choices themselves are
 * plain functions, so the unit tests check them without drawing anything.
 */

/** How the button at the end of a row of filters is drawn: its full label, its short label, or its icon alone. */
enum class EndButtonFit { Full, Compact, IconOnly }

/**
 * The widest form of the end button that fits in [room] px (what the filters leave, minus the gap): the full label,
 * else the short one, else the icon alone. Never a line of its own.
 */
fun endButtonFit(room: Int, fullWidth: Int, compactWidth: Int): EndButtonFit = when {
    fullWidth <= room -> EndButtonFit.Full
    compactWidth <= room -> EndButtonFit.Compact
    else -> EndButtonFit.IconOnly
}

/**
 * Whether the [leadWidth] px at the start of a row of filters (the category menu) goes on a line of its own above
 * them: when it, the filters ([filtersWidth]) and the smallest end button ([iconWidth]), with [gap]s between, do not
 * fit in [width].
 */
fun leadOnOwnRow(width: Int, leadWidth: Int, filtersWidth: Int, iconWidth: Int, gap: Int): Boolean =
    leadWidth + gap + filtersWidth + gap + iconWidth > width

/**
 * Whether something [trailingWidth] px wide fits at the end of a row [width] px wide, after a [gap], while what is
 * before it keeps at least [minMainWidth]. A [trailingWidth] of 0 (nothing there) always fits.
 */
fun trailingFitsBeside(width: Int, trailingWidth: Int, gap: Int, minMainWidth: Int): Boolean =
    trailingWidth == 0 || trailingWidth + gap + minMainWidth <= width

/** The widths a name line gives to the name and to the tags after it (0: the tags are left out). */
data class NameLineWidths(val name: Int, val tags: Int)

/**
 * Shares [room] px between a name ([nameWidth] wide on one line) and the tags after it ([tagsWidth]). Both whole
 * when they fit. Otherwise the name keeps at least [namePriority] of the room (all of it that it needs, if less) and
 * the tags get the rest, shortened; tags with less than [minTagWidth] would be unreadable, so they are left out.
 */
fun nameLineWidths(room: Int, nameWidth: Int, tagsWidth: Int, minTagWidth: Int, namePriority: Float = 0.6f): NameLineWidths {
    if (room <= 0) return NameLineWidths(0, 0)
    if (nameWidth + tagsWidth <= room) return NameLineWidths(nameWidth, tagsWidth)
    val nameKeeps = minOf(nameWidth, maxOf((room * namePriority).toInt(), room - tagsWidth))
    val tags = room - nameKeeps
    return if (tags < minTagWidth) NameLineWidths(minOf(nameWidth, room), 0) else NameLineWidths(nameKeeps, minOf(tags, tagsWidth))
}

/** Where the tags of a name line go: after the name, on a line of their own under it, or nowhere (left out). */
enum class TagPlacement { Beside, Under, Dropped }

/**
 * Where the tags ([tagsWidth] px, with their gap) go on a name line with [room] px for the name and the tags. Beside the
 * name when they fit there (see [nameLineWidths]). When they do not: with [underWhenTight] (the search results, whose
 * tag must always be seen) under the name; otherwise (the inbox) shortened beside it, and left out when too narrow.
 * A row with no tags ([tagsWidth] 0) is [TagPlacement.Beside].
 */
fun tagPlacement(room: Int, nameWidth: Int, tagsWidth: Int, minTagWidth: Int, underWhenTight: Boolean = false): TagPlacement {
    if (tagsWidth <= 0) return TagPlacement.Beside
    val tags = nameLineWidths(room, nameWidth, tagsWidth, minTagWidth).tags
    return when {
        underWhenTight -> if (tags >= tagsWidth) TagPlacement.Beside else TagPlacement.Under
        tags > 0 -> TagPlacement.Beside
        else -> TagPlacement.Dropped
    }
}

/** The widest a row's time may get, as a share of the row: past it, the time is shortened, not the name. */
const val TIME_MAX_FRACTION = 0.38f

/**
 * [main] with [trailing] at its end, centred on one line, when the trailing part fits and leaves [main] at least
 * [minMainFraction] of the width; otherwise [trailing] goes under [main], at the end side. Used for a code's copy
 * button beside a snippet, and for a settings row's button beside its text.
 */
@Composable
fun TrailingOrBelow(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    belowGap: Dp = 6.dp,
    minMainFraction: Float = 0.5f,
    main: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(Slot.Main)) { main() }
            Box(Modifier.layoutId(Slot.Trailing)) { trailing() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val mainM = measurables.slot(Slot.Main)
        val trailingP = measurables.slot(Slot.Trailing).measure(Constraints(maxWidth = width))
        val g = if (trailingP.width == 0) 0 else gap.roundToPx()
        val beside = !constraints.hasBoundedWidth ||
            trailingFitsBeside(width, trailingP.width, g, (width * minMainFraction).toInt())
        if (beside) {
            val mainP = mainM.measure(Constraints(maxWidth = if (constraints.hasBoundedWidth) width - trailingP.width - g else Constraints.Infinity))
            val w = if (constraints.hasBoundedWidth) width else mainP.width + g + trailingP.width
            val h = maxOf(mainP.height, trailingP.height, constraints.minHeight)
            layout(w, h) {
                mainP.placeRelative(0, centre(h, mainP.height))
                trailingP.placeRelative(w - trailingP.width, centre(h, trailingP.height))
            }
        } else {
            val mainP = mainM.measure(Constraints(maxWidth = width))
            val below = belowGap.roundToPx()
            val h = maxOf(mainP.height + below + trailingP.height, constraints.minHeight)
            layout(width, h) {
                mainP.placeRelative(0, 0)
                // placeRelative mirrors in RTL: computed for left to right, the end is the right edge
                trailingP.placeRelative(Alignment.End.align(trailingP.width, width, LayoutDirection.Ltr), mainP.height + below)
            }
        }
    }
}

/**
 * The first line of a row: [name] at the start, the [tags] right after it, the [time] at the end, centred. The name
 * has priority: the time is capped at [TIME_MAX_FRACTION] of the line and shortened past it, and the tags shorten
 * (or leave) before the name does (see [nameLineWidths]). Each part is expected to be one line with an ellipsis.
 * With [tagsUnderWhenTight] the tags never leave: when they do not fit beside the name they go on a line of their own
 * under it, at its start (see [tagPlacement]). [tagGap] separates the tags from the name; beside the name only.
 */
@Composable
fun NameLine(
    name: @Composable () -> Unit,
    time: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tags: (@Composable () -> Unit)? = null,
    timeGap: Dp = 8.dp,
    tagsUnderWhenTight: Boolean = false,
    tagGap: Dp = 0.dp,
    underGap: Dp = 2.dp,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(Slot.Main)) { name() }
            if (tags != null) Box(Modifier.layoutId(Slot.Tags)) { tags() }
            Box(Modifier.layoutId(Slot.Trailing)) { time() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val timeM = measurables.slot(Slot.Trailing)
        val timeCap = (width * TIME_MAX_FRACTION).toInt()
        val timeP = timeM.measure(Constraints(maxWidth = minOf(timeM.maxIntrinsicWidth(Constraints.Infinity), timeCap)))
        val room = width - timeP.width - (if (timeP.width > 0) timeGap.roundToPx() else 0)
        val nameM = measurables.slot(Slot.Main)
        val tagsM = measurables.firstOrNull { it.layoutId == Slot.Tags }
        val gap = tagGap.roundToPx()
        val tagsNeed = tagsM?.maxIntrinsicWidth(Constraints.Infinity)?.let { if (it > 0) it + gap else 0 } ?: 0
        val placement = tagPlacement(
            room, nameM.maxIntrinsicWidth(Constraints.Infinity), tagsNeed, minTagWidth = 28.dp.roundToPx(), underWhenTight = tagsUnderWhenTight,
        )
        if (tagsM != null && tagsNeed > 0 && placement == TagPlacement.Under) {
            val nameP = nameM.measure(Constraints(maxWidth = room.coerceAtLeast(0)))
            val tagsP = tagsM.measure(Constraints(maxWidth = width))
            val lineH = maxOf(nameP.height, timeP.height, constraints.minHeight)
            val h = lineH + underGap.roundToPx() + tagsP.height
            return@Layout layout(width, h) {
                nameP.placeRelative(0, centre(lineH, nameP.height))
                timeP.placeRelative(width - timeP.width, centre(lineH, timeP.height))
                tagsP.placeRelative(0, lineH + underGap.roundToPx())
            }
        }
        val tagsRoom = if (tagsM != null && placement == TagPlacement.Beside) nameLineWidths(room, nameM.maxIntrinsicWidth(Constraints.Infinity), tagsNeed, 28.dp.roundToPx()).tags else 0
        // All the room the tags leave: the name takes its own width of it (as in a Row), so its text is drawn where a Row would draw it
        val nameP = nameM.measure(Constraints(maxWidth = (room - tagsRoom).coerceAtLeast(0)))
        val tagsP = if (tagsM != null && tagsRoom > gap) tagsM.measure(Constraints(maxWidth = tagsRoom - gap)) else null
        val h = maxOf(nameP.height, tagsP?.height ?: 0, timeP.height, constraints.minHeight)
        layout(width, h) {
            nameP.placeRelative(0, centre(h, nameP.height))
            tagsP?.placeRelative(nameP.width + gap, centre(h, tagsP.height))
            timeP.placeRelative(width - timeP.width, centre(h, timeP.height))
        }
    }
}

/**
 * [full] when it fits the width it is given, else [short] (a header's "3 selected" becomes "3" when the action icons
 * leave too little room).
 */
@Composable
fun FitOrShort(modifier: Modifier = Modifier, full: @Composable () -> Unit, short: @Composable () -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0)
        val fullM = subcompose(Slot.Main, full)
        val fits = !constraints.hasBoundedWidth || fullM.all { it.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth }
        val placeables = (if (fits) fullM else subcompose(Slot.Tags, short)).map { it.measure(loose) }
        val w = maxOf(placeables.maxOfOrNull { it.width } ?: 0, constraints.minWidth)
        val h = maxOf(placeables.maxOfOrNull { it.height } ?: 0, constraints.minHeight)
        layout(w, h) { placeables.forEach { it.placeRelative(0, centre(h, it.height)) } }
    }
}

/** A dialog's buttons at its end; with a large font, the ones that do not fit go to a second line instead of being cut. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogButtons(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { content() }
}

/** A row of buttons that wraps to a second line when the font is too large for one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonsRow(modifier: Modifier = Modifier, spacing: Dp = 10.dp, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** The big header titles grow with the font only this far; the rest of the text grows fully. */
const val HEADER_TITLE_MAX_SCALE = 1.3f

/**
 * This size in sp, scaled with the user's font size only up to [maxScale]: for the big header titles, which are
 * already large and would otherwise take a third of a narrow screen. Text in the content scales fully.
 */
@Composable
fun TextUnit.scaledAtMost(maxScale: Float): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale <= maxScale) this else this * (maxScale / scale)
}

/** A text size that follows a dp size and not the font size: an initial inside a round avatar, a digit in a dot. */
@Composable
fun Dp.asFixedSp(): TextUnit = with(LocalDensity.current) { this@asFixedSp.toSp() }

private enum class Slot { Main, Tags, Trailing }

/** Where a child [size] px tall starts to be centred in [space] px: rounded as Compose's own CenterVertically does, so a row drawn with these layouts matches one drawn with a Row. */
private fun centre(space: Int, size: Int): Int = ((space - size) / 2f).roundToInt()

private fun List<Measurable>.slot(slot: Slot): Measurable = first { it.layoutId == slot }
