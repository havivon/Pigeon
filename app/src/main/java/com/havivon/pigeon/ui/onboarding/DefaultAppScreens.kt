package com.havivon.pigeon.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.components.LauncherIcon
import com.havivon.pigeon.ui.components.PillButton
import com.havivon.pigeon.ui.theme.HebTheme

/**
 * First launch: the whole screen offers the two ways to use Pigeon. Making it the phone's
 * messaging app comes first; "just view" reads (and sends) while the current app stays the default.
 */
@Composable
fun DefaultAppWelcome(onMakeDefault: () -> Unit, onViewOnly: () -> Unit, onNotNow: () -> Unit) {
    val c = HebTheme.colors
    val iconShape = RoundedCornerShape(30)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.header)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.width * 0.8f, center = Offset(size.width * 0.05f, size.height * 0.02f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.92f))
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.shadow(16.dp, iconShape)) {
                    LauncherIcon(R.drawable.ic_launcher_foreground, 112.dp, iconShape)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(R.string.onboarding_welcome_title),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.onboarding_welcome_body),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BenefitRow(Icons.Rounded.Palette, stringResource(R.string.onboarding_benefit_colors))
                    BenefitRow(Icons.Rounded.Block, stringResource(R.string.onboarding_benefit_spam))
                    BenefitRow(Icons.Rounded.WifiOff, stringResource(R.string.onboarding_benefit_offline))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // The accent is too pale on white in the dark theme, so the text takes the dark gradient's first stop
        val onWhite = if (c.isDark) Color(0xFF3A2596) else c.accent
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(onClick = onMakeDefault)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.onboarding_make_default), color = onWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
                .clickable(onClick = onViewOnly)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.onboarding_view_only), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onNotNow)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.onboarding_not_now), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/**
 * Shown instead of the system's "denied" dialog when Android's restricted settings block a
 * request (the app was installed from a file): to be the messaging app, or to read the messages.
 * The three steps to allow it, and a way there.
 */
@Composable
fun RestrictedSettingsHelp(onOpenAppInfo: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RestrictedSettingsHelpCard(onOpenAppInfo, onClose, Modifier.fillMaxWidth())
    }
}

/** The help's card itself, apart from its window, so the design snapshots can draw it. */
@Composable
internal fun RestrictedSettingsHelpCard(onOpenAppInfo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = c.surface, shadowElevation = 8.dp) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LauncherIcon(R.drawable.ic_launcher_foreground, 64.dp, RoundedCornerShape(30))
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.onboarding_restricted_title),
                color = c.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_restricted_body),
                color = c.onSurfaceMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            listOf(R.string.onboarding_restricted_step1, R.string.onboarding_restricted_step2, R.string.onboarding_restricted_step3)
                .forEachIndexed { i, step -> RestrictedStep(i + 1, stringResource(step)) }
            Spacer(Modifier.height(20.dp))
            PillButton(
                stringResource(R.string.onboarding_restricted_open),
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                filled = true,
                onClick = onOpenAppInfo,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClose)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.onboarding_restricted_close), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RestrictedStep(number: Int, text: String) {
    val c = HebTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(c.header),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}

/**
 * Every later open while Pigeon can see no messages (neither the default nor allowed to view):
 * a card over the app with the same two ways, easy to dismiss. A viewer gets the inbox banner instead.
 */
@Composable
fun DefaultAppReminder(onMakeDefault: () -> Unit, onViewOnly: () -> Unit, onNotNow: () -> Unit) {
    Dialog(onDismissRequest = onNotNow) {
        DefaultAppReminderCard(onMakeDefault, onViewOnly, onNotNow, Modifier.fillMaxWidth())
    }
}

/** The reminder's card itself, apart from its window, so the design snapshots can draw it. */
@Composable
internal fun DefaultAppReminderCard(onMakeDefault: () -> Unit, onViewOnly: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = HebTheme.colors
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = c.surface, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LauncherIcon(R.drawable.ic_launcher_foreground, 64.dp, RoundedCornerShape(30))
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.onboarding_reminder_title),
                color = c.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_reminder_body),
                color = c.onSurfaceMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PillButton(
                stringResource(R.string.onboarding_make_default),
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                filled = true,
                onClick = onMakeDefault,
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                stringResource(R.string.onboarding_view_only),
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                onClick = onViewOnly,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onNotNow)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.onboarding_not_now), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
