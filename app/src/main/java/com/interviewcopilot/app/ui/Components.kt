package com.interviewcopilot.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-like press feedback: spring scale + soft haptic. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun AppChip(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text, color = fg, fontSize = 16.sp)
    }
}

@Composable
fun BigButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val bgAnim by animateColorAsState(if (enabled) bg else c.chipGray, label = "bbg")
    val fgAnim by animateColorAsState(if (enabled) fg else c.text2, label = "bfg")
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bgAnim)
            .bounceClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fgAnim, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    onSend: (() -> Unit)? = null
) {
    val c = LocalAppColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = TextStyle(color = c.text, fontSize = 17.sp),
        cursorBrush = SolidColor(c.blue),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default),
        keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = c.text2, fontSize = 17.sp)
                inner()
            }
        }
    )
}

@Composable
fun BoxedField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false
) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bg)
            .border(BorderStroke(1.dp, c.sep), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        AppTextField(value, onChange, hint, Modifier.fillMaxWidth(), singleLine, minLines, secret)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipChoice(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalAppColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            AppChip(
                text = o,
                bg = if (on) c.blue else c.chipGray,
                fg = if (on) Color.White else c.text
            ) { onSelect(o) }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.green,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = c.chipGray,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = c.sep
            )
        )
    }
}

@Composable
fun ScreenTitle(text: String) {
    val c = LocalAppColors.current
    Text(
        text,
        color = c.text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(PaddingValues(start = 18.dp, top = 12.dp, bottom = 10.dp))
    )
}
