package com.havivon.pigeon.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.havivon.pigeon.R
import com.havivon.pigeon.ui.theme.Skin

/**
 * The skin the widgets are drawn in: the app's setting, read when a widget is drawn and handed down by each widget's
 * content. Light or dark follows the phone's night mode (the launcher's), not the app's own theme choice.
 */
internal val LocalWidgetSkin = staticCompositionLocalOf { Skin.Sunset }

/**
 * A skin's widget backgrounds. Glance cannot draw a Brush, so each gradient is a shape drawable per skin, whose colours
 * (`res/values/widget_colors.xml`, dark ones in `values-night`) are the skin's header stops and surface.
 */
internal class WidgetDrawables(
    /** The page of the list widgets (unread, contacts, favourites). */
    @DrawableRes val surface: Int,
    /** The whole background of the code and counter widgets. */
    @DrawableRes val gradient: Int,
    /** The band at the top of the list widgets. */
    @DrawableRes val header: Int,
    /** The round "new message" button of the contacts widget. */
    @DrawableRes val circle: Int,
)

internal val Skin.widgetDrawables: WidgetDrawables
    get() = when (this) {
        Skin.Sunset -> WidgetDrawables(R.drawable.widget_bg_surface_sunset, R.drawable.widget_bg_gradient_sunset, R.drawable.widget_bg_header_sunset, R.drawable.widget_circle_gradient_sunset)
        Skin.Ocean -> WidgetDrawables(R.drawable.widget_bg_surface_ocean, R.drawable.widget_bg_gradient_ocean, R.drawable.widget_bg_header_ocean, R.drawable.widget_circle_gradient_ocean)
        Skin.Forest -> WidgetDrawables(R.drawable.widget_bg_surface_forest, R.drawable.widget_bg_gradient_forest, R.drawable.widget_bg_header_forest, R.drawable.widget_circle_gradient_forest)
        Skin.Midnight -> WidgetDrawables(R.drawable.widget_bg_surface_midnight, R.drawable.widget_bg_gradient_midnight, R.drawable.widget_bg_header_midnight, R.drawable.widget_circle_gradient_midnight)
        Skin.Pomegranate -> WidgetDrawables(R.drawable.widget_bg_surface_pomegranate, R.drawable.widget_bg_gradient_pomegranate, R.drawable.widget_bg_header_pomegranate, R.drawable.widget_circle_gradient_pomegranate)
        Skin.Desert -> WidgetDrawables(R.drawable.widget_bg_surface_desert, R.drawable.widget_bg_gradient_desert, R.drawable.widget_bg_header_desert, R.drawable.widget_circle_gradient_desert)
        Skin.Pastel -> WidgetDrawables(R.drawable.widget_bg_surface_pastel, R.drawable.widget_bg_gradient_pastel, R.drawable.widget_bg_header_pastel, R.drawable.widget_circle_gradient_pastel)
        Skin.Graphite -> WidgetDrawables(R.drawable.widget_bg_surface_graphite, R.drawable.widget_bg_gradient_graphite, R.drawable.widget_bg_header_graphite, R.drawable.widget_circle_gradient_graphite)
    }
