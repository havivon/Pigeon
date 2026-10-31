package com.havivon.pigeon.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.havivon.pigeon.R

/** The launcher icons the user can pick in settings; [alias] is the manifest activity-alias. */
enum class AppIcon(
    val alias: String,
    @StringRes val labelRes: Int,
    @DrawableRes val foregroundRes: Int,
    val enabledInManifest: Boolean = false,
) {
    Envelope("IconEnvelope", R.string.rules_icon_envelope, R.drawable.ic_launcher_foreground, enabledInManifest = true),
    Origami("IconOrigami", R.string.rules_icon_origami, R.drawable.ic_launcher_foreground_origami),
    Bubble("IconBubble", R.string.rules_icon_bubble, R.drawable.ic_launcher_foreground_bubble),
}
