package com.havivon.pigeon.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.havivon.pigeon.ui.theme.HebTheme

/** A single-line text box in the app's style, with its caption above. */
@Composable
fun LabeledTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    hint: String,
    emphasized: Boolean = false,
    onDone: () -> Unit = {},
) {
    val c = HebTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Text(label, color = c.onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = c.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(if (emphasized) 1.5.dp else 1.dp, if (emphasized) c.accent else c.divider, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = c.onSurfaceMuted.copy(alpha = 0.7f), fontSize = 16.sp)
                inner()
            }
        },
    )
}
