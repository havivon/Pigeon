package com.havivon.pigeon.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.havivon.pigeon.R

private val CellWidth = 52.dp
private const val MaxContacts = 5

/** Quick contacts: the pinned conversations first, then the other personal ones, and a "+" for a new message. */
class ContactsWidget : GlanceAppWidget() {
    // How many contacts fit depends on the width
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetSource(context).contacts()
        provideContent { ContactsWidgetContent(data) }
    }
}

class ContactsWidgetReceiver : PigeonWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContactsWidget()
}

@Composable
fun ContactsWidgetContent(data: ContactsWidgetData) {
    val contacts = data.contacts
    // The "+" takes one cell, the rest go to the contacts
    val fitting = ((LocalSize.current.width - 24.dp) / CellWidth).toInt() - 1
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.widget_bg_surface))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        contacts.take(minOf(MaxContacts, fitting)).forEach { ContactCell(it) }
        Column(
            modifier = GlanceModifier.defaultWeight().clickable(openNewMessage()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_circle_gradient)),
                contentAlignment = Alignment.Center,
            ) {
                WidgetIcon(R.drawable.ic_widget_add, 22.dp, widgetString(R.string.inbox_new_message))
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = widgetString(R.string.widget_contacts_new),
                style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RowScope.ContactCell(contact: WidgetContact) {
    Column(
        modifier = GlanceModifier.defaultWeight().clickable(openThread(contact.threadId)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetAvatar(contact.name, contact.avatarArgb, 40.dp, textSize = 17.sp)
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = contact.name.substringBefore(' '),
            style = TextStyle(color = WidgetColors.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}
