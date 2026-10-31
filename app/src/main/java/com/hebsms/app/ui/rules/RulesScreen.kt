package com.hebsms.app.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebsms.app.R
import com.hebsms.app.domain.model.ColorRule
import com.hebsms.app.domain.model.RuleMatch
import com.hebsms.app.ui.components.GradientHeader
import com.hebsms.app.ui.components.HeaderIcon
import com.hebsms.app.ui.components.PillButton
import com.hebsms.app.ui.components.RuleTag
import com.hebsms.app.ui.components.SectionLabel
import com.hebsms.app.ui.theme.HebTheme

val RuleSwatches = listOf(
    0xFF1E9E6A, 0xFF14A3B8, 0xFF2F80ED, 0xFF5B3FD9, 0xFFC2408F,
    0xFFE0559B, 0xFFF08A24, 0xFFE8B10C, 0xFF8D6E63,
)

@Composable
fun RulesScreen(
    rules: List<ColorRule>,
    newRuleWord: String,
    newRuleSwatch: Long = 0xFF14A3B8,
    onBack: () -> Unit = {},
) {
    val c = HebTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onBack)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.rules_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            stringResource(R.string.rules_subtitle),
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.rules_section_yours)) }
        items(rules, key = { it.id }) { RuleCard(it) }
        item { SpamRuleCard() }
        item { SectionLabel(stringResource(R.string.rules_new_rule)) }
        item { NewRuleCard(newRuleWord, newRuleSwatch) }
    }
}

@Composable
private fun RuleCard(rule: ColorRule) {
    val color = Color(rule.argb)
    val (icon, subtitle) = when (val m = rule.match) {
        is RuleMatch.Sender -> Icons.Rounded.Person to stringResource(R.string.rules_sender_subtitle, m.name)
        is RuleMatch.Keyword -> Icons.Rounded.Tag to stringResource(R.string.rules_keyword_subtitle, m.word)
    }
    RuleRow(icon, rule.label, subtitle, if (rule.enabled) color else HebTheme.colors.divider, color, rule.enabled, dragHandle = true)
}

@Composable
private fun SpamRuleCard() {
    val spam = HebTheme.colors.spam
    RuleRow(
        Icons.Rounded.Warning,
        stringResource(R.string.rules_spam_title),
        stringResource(R.string.rules_spam_subtitle),
        spam,
        spam,
        enabled = true,
        dragHandle = false,
    )
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    frame: Color,
    color: Color,
    enabled: Boolean,
    dragHandle: Boolean,
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 2.dp, shape, spotColor = c.accent.copy(alpha = 0.25f))
            .clip(shape)
            .background(c.surface)
            .border(2.dp, frame, shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle) {
            Icon(Icons.Rounded.DragIndicator, contentDescription = stringResource(R.string.rules_drag), tint = c.onSurfaceMuted)
        } else {
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = if (enabled) 1f else 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = c.onSurfaceMuted, fontSize = 13.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun NewRuleCard(word: String, swatch: Long) {
    val c = HebTheme.colors
    val color = Color(swatch)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(if (c.isDark) 0.dp else 3.dp, shape, spotColor = c.accent.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.surface)
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surfaceVariant).padding(3.dp),
        ) {
            listOf(stringResource(R.string.rules_kind_sender) to false, stringResource(R.string.rules_kind_keyword) to true).forEach { (label, on) ->
                Text(
                    label,
                    color = if (on) Color.White else c.onSurfaceMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .then(if (on) Modifier.background(c.header) else Modifier)
                        .clickable { }
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            word,
            color = c.onSurface,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, c.accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RuleSwatches.forEach { argb ->
                val selected = argb == swatch
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .then(if (selected) Modifier.border(3.dp, c.onSurface, CircleShape) else Modifier)
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.rules_swatch_selected), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.rules_preview_label), color = c.onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.rules_preview_message, word),
                color = c.onSurface,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .background(c.background)
                    .border(2.dp, color, RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            RuleTag(word, color)
        }
        Spacer(Modifier.height(14.dp))
        PillButton(stringResource(R.string.rules_save), Modifier.fillMaxWidth(), filled = true)
    }
}
