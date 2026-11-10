package com.havivon.pigeon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.havivon.pigeon.R
import com.havivon.pigeon.domain.repository.SettingsRepository
import com.havivon.pigeon.ui.components.FixedHeaderLayout
import com.havivon.pigeon.ui.components.GradientHeader
import com.havivon.pigeon.ui.components.HeaderIcon
import com.havivon.pigeon.ui.components.HeaderTitle
import com.havivon.pigeon.ui.components.listBottomPadding
import com.havivon.pigeon.ui.theme.HebColors
import com.havivon.pigeon.ui.theme.HebTheme
import com.havivon.pigeon.ui.theme.Skin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The skin picker. [current] is null until the stored choice is read, so no card is checked by mistake for a moment.
 * A pick is stored at once; the whole app (and the widgets) recolour from the stored value.
 */
class SkinPickerViewModel(private val settings: SettingsRepository) : ViewModel() {

    val current: StateFlow<Skin?> = settings.settings
        .map { Skin.fromId(it.skin) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pick(skin: Skin) {
        viewModelScope.launch { settings.setSkin(skin.id) }
    }
}

/**
 * The narrowest a skin card may be: two columns on a phone; one when the font is very large, so the names stay whole;
 * more on a wide screen.
 */
internal fun skinCardMinWidth(fontScale: Float): Dp = if (fontScale >= LARGE_FONT_SCALE) 220.dp else 140.dp

private const val LARGE_FONT_SCALE = 1.5f

/** The eight skins as cards, in a grid that adapts to the width and the font; a tap applies one at once. */
@Composable
fun SkinPickerScreen(current: Skin?, onPick: (Skin) -> Unit, onBack: () -> Unit) {
    val c = HebTheme.colors
    val minWidth = skinCardMinWidth(LocalDensity.current.fontScale)
    FixedHeaderLayout(
        Modifier.fillMaxSize().background(c.background),
        header = {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    HeaderTitle(stringResource(R.string.skins_title), stringResource(R.string.skins_subtitle), Modifier.weight(1f))
                }
            }
        },
    ) { top ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minWidth),
            modifier = Modifier.fillMaxSize().selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = listBottomPadding(24.dp)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Skin.entries, key = { it.id }) { skin -> SkinCard(skin, selected = skin == current) { onPick(skin) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.skins_note),
                    color = c.onSurfaceMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * A skin in small, in the current light or dark mode: its header gradient (with a check when chosen), a bubble pair on
 * its page, and its name beside its accent dot.
 */
@Composable
private fun SkinCard(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    val c = HebTheme.colors
    val look = skin.colors(c.isDark)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) c.accent else c.divider, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(look.header)) {
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = look.onWhite, modifier = Modifier.size(16.dp))
                }
            }
        }
        BubblePair(look)
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(look.accent))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(skin.labelRes),
                color = c.onSurface,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** An incoming and an outgoing bubble, as bars without text, on the skin's page. */
@Composable
private fun BubblePair(look: HebColors) {
    val bar = 14.dp
    Column(
        Modifier.fillMaxWidth().background(look.background).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val incoming = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomEnd = 9.dp, bottomStart = 3.dp)
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(bar)
                .clip(incoming)
                .background(look.incoming)
                .border(1.dp, look.divider, incoming),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(bar)
                    .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomEnd = 3.dp, bottomStart = 9.dp))
                    .background(look.outgoing),
            )
        }
    }
}

/** The settings row's mini swatch: the skin's header gradient in the current mode. */
@Composable
internal fun SkinSwatch(skin: Skin, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(50)
    Box(modifier.size(width = 44.dp, height = 24.dp).clip(shape).background(skin.colors(c.isDark).header).border(1.dp, c.divider, shape))
}
