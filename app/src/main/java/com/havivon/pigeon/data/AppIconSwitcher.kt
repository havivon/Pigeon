package com.havivon.pigeon.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.havivon.pigeon.domain.model.AppIcon

/**
 * Switches the launcher icon by enabling exactly one of the manifest's activity-aliases.
 * Some launchers drop the old icon from the home screen when its alias is disabled.
 */
object AppIconSwitcher {

    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.enabledInManifest
                else -> false
            }
        } ?: AppIcon.Envelope
    }

    fun set(context: Context, chosen: AppIcon) {
        val pm = context.packageManager
        // Enable the new one first, so there is never a moment with no launcher entry
        pm.setComponentEnabledSetting(component(context, chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != chosen }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    private fun component(context: Context, icon: AppIcon) =
        ComponentName(context.packageName, "${context.packageName}.${icon.alias}")
}
