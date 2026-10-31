package com.havivon.pigeon.widget

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.havivon.pigeon.R
import com.havivon.pigeon.data.sample.SampleSet
import com.havivon.pigeon.domain.model.Conversation

/** How long ago the sample code arrived; a real time-ago comes with the SMS data. */
private const val SampleMinutesAgo = 2

private val CodeKey = ActionParameters.Key<String>("code")

/** The latest verification code, big, with a Copy button. 2x2 stacks the pieces; a short 3x1 puts the button beside the code. */
class CodeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sample = sampleFor(context)
        provideContent { CodeWidgetContent(sample) }
    }
}

class CodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodeWidget()
}

/** Copies the code flagged as sensitive, so Android keeps it out of the clipboard preview, then confirms with a toast. */
class CopyCodeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val code = parameters[CodeKey] ?: return
        val clip = ClipData.newPlainText(context.getString(R.string.widget_code_kind), code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.widget_code_copied, Toast.LENGTH_SHORT).show()
        }
    }
}

/** The digits with thin spaces between them (Glance has no letter spacing), forced left to right inside a right-to-left page. */
private fun spaced(code: String) = "‪" + code.toList().joinToString(" ") + "‬"

@Composable
fun CodeWidgetContent(sample: SampleSet) {
    val conversation = sample.inbox.firstOrNull { it.code != null }
    val code = conversation?.code
    val compact = LocalSize.current.height < 110.dp
    val modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg_gradient))
    if (conversation == null || code == null) {
        Box(modifier = modifier.clickable(openApp()), contentAlignment = Alignment.Center) {
            Text(
                text = widgetString(R.string.widget_code_none),
                style = TextStyle(color = WidgetColors.onGradient, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    } else if (compact) {
        Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Later: a deep link to the conversation the code came from
            Column(modifier = GlanceModifier.defaultWeight().clickable(openApp())) {
                CodeCaption(conversation)
                CodeDigits(code, 24.sp)
            }
            Spacer(GlanceModifier.width(8.dp))
            CopyPill(code)
        }
    } else {
        Column(modifier = modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(openApp())) {
                CodeCaption(conversation)
                Spacer(GlanceModifier.height(6.dp))
                CodeDigits(code, 28.sp)
            }
            Spacer(GlanceModifier.height(12.dp))
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CopyPill(code) }
        }
    }
}

@Composable
private fun CodeCaption(conversation: Conversation) {
    Text(
        text = widgetString(
            R.string.widget_code_subtitle,
            widgetString(R.string.widget_code_kind),
            conversation.name,
            widgetPlural(R.plurals.widget_ago_minutes, SampleMinutesAgo),
        ),
        style = TextStyle(color = WidgetColors.onGradientSoft, fontSize = 11.sp),
        maxLines = 2,
    )
}

@Composable
private fun CodeDigits(code: String, size: TextUnit) {
    Text(
        text = spaced(code),
        style = TextStyle(color = WidgetColors.onGradient, fontSize = size, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
        maxLines = 1,
    )
}

@Composable
private fun CopyPill(code: String) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_pill_white))
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .clickable(actionRunCallback<CopyCodeAction>(actionParametersOf(CodeKey to code))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = widgetString(R.string.common_copy),
            style = TextStyle(color = WidgetColors.onWhitePill, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}
