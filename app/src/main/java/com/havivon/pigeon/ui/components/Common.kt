package com.havivon.pigeon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.common.avatarInitial
import com.havivon.pigeon.ui.theme.HebTheme

/** The coloured band at the top of every screen, with two soft circles for depth. */
@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(HebTheme.colors.header)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.9f, center = Offset(size.width * 0.05f, -size.height * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.55f, center = Offset(size.width * 0.9f, size.height * 1.05f))
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

/**
 * A screen whose [header] (a [GradientHeader]) stays at the top while the list of [content] scrolls under it,
 * into the header's rounded corners. [content] gets the header's height to put before its first item (as the
 * list's top content padding), so at rest the screen looks as if the header were the list's first item.
 * Measured in one pass: the first frame is already right.
 */
@Composable
fun FixedHeaderLayout(modifier: Modifier = Modifier, header: @Composable () -> Unit, content: @Composable (top: Dp) -> Unit) {
    SubcomposeLayout(modifier) { constraints ->
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val headers = subcompose(FixedHeaderSlot.Header, header).map { it.measure(free) }
        val top = headers.maxOfOrNull { it.height } ?: 0
        val contents = subcompose(FixedHeaderSlot.Content) { content(top.toDp()) }.map { it.measure(constraints) }
        val width = maxOf(constraints.minWidth, contents.maxOfOrNull { it.width } ?: 0, headers.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(constraints.minHeight, contents.maxOfOrNull { it.height } ?: 0, top)
        layout(width, height) {
            contents.forEach { it.placeRelative(0, 0) }
            // Drawn last: the list scrolls under it
            headers.forEach { it.placeRelative(0, 0) }
        }
    }
}

private enum class FixedHeaderSlot { Header, Content }

@Composable
fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
fun Avatar(name: String, argb: Long, size: Dp = 48.dp) {
    val base = Color(argb)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.7f).compositeOver(Color.White), base))),
        contentAlignment = Alignment.Center,
    ) {
        val initial = avatarInitial(name)
        if (initial != null) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        } else {
            // A number without a contact name
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/**
 * Avatar used for suspected spam: the red warning sign in a plain circle. Red is kept for the frame, the
 * buttons and this sign; everything else of spam is drawn in the ordinary colours.
 */
@Composable
fun SpamAvatar(size: Dp = 48.dp) {
    val c = HebTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = stringResource(R.string.common_suspected_spam), tint = c.spam, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun RuleTag(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun CountBadge(count: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(HebTheme.colors.header)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A pill button: filled with the header gradient, or outlined in [color]. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = HebTheme.colors.accent,
    filled: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(HebTheme.colors.header) else Modifier.border(1.5.dp, color, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (filled) Color.White else color
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String, icon: ImageVector? = null) {
    val c = HebTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = c.onSurfaceMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** A launcher icon as the launcher draws it: both 108dp adaptive layers, masked to the central 72dp. */
@Composable
fun LauncherIcon(@DrawableRes foreground: Int, size: Dp, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        Image(painterResource(foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

/** What a screen shows when it has nothing: a soft round icon, a title, a line of help, and an optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = HebTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.header), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = c.onSurface, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = c.onSurfaceMuted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/** One action in a bottom sheet: an icon in a soft circle and its label; red for a destructive one. [trailing] is a muted note at the far end (a time). */
@Composable
fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    val c = HebTheme.colors
    val color = if (danger) c.spam else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) c.spam else c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = c.onSurfaceMuted, fontSize = 13.sp)
    }
}

/** The top of a sheet's content: the grab handle drawn by the sheet, then a title row. */
@Composable
fun SheetTitle(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null) {
    val c = HebTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = c.onSurfaceMuted, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** A chip on the gradient header (folders, search filters): white when on, glassy when off. */
@Composable
fun HeaderChip(label: String, selected: Boolean, onClick: () -> Unit, trailing: ImageVector? = null) {
    val c = HebTheme.colors
    val onWhite = if (c.isDark) Color(0xFF3A2596) else c.accent
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) onWhite else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            Icon(trailing, contentDescription = null, tint = if (selected) onWhite else Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
