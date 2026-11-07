package com.interviewcopilot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.interviewcopilot.app.logic.Prompts
import com.interviewcopilot.app.vm.CopilotVM
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopilotScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var typed by remember { mutableStateOf("") }
    var transcriptOpen by remember { mutableStateOf(false) }
    var showFix by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startRecording() else vm.error = "Microphone permission is needed to hear the interviewer."
    }

    fun startRec() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        ModeCard(vm) { if (vm.recording) vm.stopRecording() else startRec() }
        Spacer(Modifier.height(8.dp))

        // Live transcript line
        Row(
            Modifier
                .fillMaxWidth()
                .bounceClick { transcriptOpen = !transcriptOpen }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val line = when {
                vm.partial.isNotBlank() -> vm.partial
                vm.recording -> "Waiting for speech..."
                vm.transcript.isNotEmpty() -> vm.transcript.last()
                else -> "Transcript will appear here."
            }
            Text(
                line,
                color = if (vm.partial.isNotBlank()) c.text else c.text2,
                fontSize = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.text2, modifier = Modifier.size(28.dp))
        }
        AnimatedVisibility(
            visible = transcriptOpen,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.card)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.transcript.isEmpty()) Text("No speech yet.", color = c.text2, fontSize = 15.sp)
                vm.transcript.forEach { Text("Interviewer: $it", color = c.text, fontSize = 15.sp) }
            }
        }

        vm.error?.let { err ->
            Text(
                err,
                color = c.red,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Question label
        if (vm.shownQuestion.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
                Text(
                    vm.shownQuestion,
                    color = c.text2,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text("Q${vm.shownNumber}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(vm.shownTime)).lowercase(),
                        color = c.text2,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Answer + follow-ups
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AnswerCard(vm)
            val fu = vm.followups
            val hasAnswer = vm.shownAnswer.isNotBlank()
            Spacer(Modifier.height(14.dp))
            Text("Follow-up", color = c.text2, fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppChip("New answer", c.chipRed, c.text) {
                    if (hasAnswer) vm.followUp("Give a different answer with a fresh angle and different wording.")
                    else if (vm.shownQuestion.isNotBlank()) vm.generate(vm.shownQuestion)
                }
                AppChip("Misheard term", c.chipRed, c.text) { if (vm.shownQuestion.isNotBlank()) showFix = true }
                AppChip("Explain more", c.chipBlue, c.text) {
                    vm.followUp("Expand the previous answer with more depth and detail, still in spoken style.")
                }
                AppChip("Give an example", c.chipBlue, c.text) {
                    vm.followUp("Add one concrete example (STAR format for behavioral, short code for technical).")
                }
                fu.forEach { chip ->
                    AppChip(chip, c.chipGray, c.text) { vm.followUp("Follow-up request: $chip") }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (vm.engineUsed.isNotBlank() && vm.firstTokenMs > 0 && hasAnswer) {
                Text(
                    "${vm.engineUsed} · first words in ${vm.firstTokenMs} ms",
                    color = c.text2,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        InputBar(vm, typed, { typed = it }, { startRec() }, clipboardPaste = { clipboard.getText()?.text?.let { typed = it } })
    }

    if (showFix) {
        var wrong by remember { mutableStateOf("") }
        var right by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFix = false },
            containerColor = c.card,
            title = { Text("Misheard term", color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Heard: ${vm.shownQuestion}", color = c.text2, fontSize = 14.sp)
                    BoxedField(wrong, { wrong = it }, "Word that was heard wrongly")
                    BoxedField(right, { right = it }, "What it should be")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.fixTerm(wrong, right)
                    showFix = false
                }) { Text("Fix and regenerate", color = c.blue) }
            },
            dismissButton = { TextButton(onClick = { showFix = false }) { Text("Cancel", color = c.text2) } }
        )
    }
}

@Composable
private fun ModeCard(vm: CopilotVM, onToggleRec: () -> Unit) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(c.card)
            .border(BorderStroke(1.dp, c.sep), RoundedCornerShape(30.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = vm.recording, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            Box(Modifier.padding(end = 10.dp).bounceClick { onToggleRec() }) { AudioBars(vm.level) }
        }
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.bounceClick { open = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(vm.mode, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.text2, modifier = Modifier.padding(start = 6.dp).size(26.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Prompts.modes.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = {
                        vm.setMode(m)
                        open = false
                    })
                }
            }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(c.chipGray)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Rounded.ChevronLeft, "Previous question", tint = c.text,
                modifier = Modifier.size(40.dp).bounceClick { vm.prev() }.padding(6.dp)
            )
            Icon(
                Icons.Rounded.ChevronRight, "Next question", tint = c.text,
                modifier = Modifier.size(40.dp).bounceClick { vm.next() }.padding(6.dp)
            )
        }
    }
}

@Composable
fun AudioBars(level: Float) {
    val c = LocalAppColors.current
    val smooth by animateFloatAsState(level, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "lvl")
    val t = rememberInfiniteTransition(label = "bars")
    val wob by t.animateFloat(0.6f, 1f, InfiniteRepeatableSpec(tween(520), RepeatMode.Reverse), label = "wob")
    val factors = listOf(0.55f, 1f, 0.75f)
    Row(
        Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        factors.forEachIndexed { i, f ->
            val h = (8f + 22f * (0.25f + smooth) * f * (if (i == 1) wob else 1.2f - wob * 0.4f)).coerceIn(6f, 30f)
            Box(
                Modifier
                    .width(5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.red)
            )
        }
    }
}

@Composable
private fun AnswerCard(vm: CopilotVM) {
    val c = LocalAppColors.current
    val answer = vm.shownAnswer
    val live = vm.viewIndex < 0 && vm.generating
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.35f, 1f, InfiniteRepeatableSpec(tween(700), RepeatMode.Reverse), label = "a")
    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .padding(18.dp)
    ) {
        when {
            answer.isBlank() && vm.generating -> Text("Thinking…", color = c.text2, fontSize = 22.sp, modifier = Modifier.alpha(a))
            answer.isBlank() -> Text("Copilot answer will appear here.", color = c.text2, fontSize = 22.sp, modifier = Modifier.height(120.dp))
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (vm.mode == "Coding") "Solution" else "Overview",
                    color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                AnswerContent(answer, live)
            }
        }
    }
}

@Composable
fun AnswerContent(text: String, live: Boolean) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val parts = text.split("```")
    var firstDone = false
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEachIndexed { i, p ->
            if (i % 2 == 1) {
                val lines = p.lines()
                val lang = lines.firstOrNull()?.trim().orEmpty()
                val code = (if (lines.size > 1) lines.drop(1).joinToString("\n") else p).trim('\n')
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.cardHi)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (lines.size > 1) lang else "code", color = c.text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.ContentCopy, "Copy code", tint = c.text2,
                            modifier = Modifier.size(34.dp).bounceClick { clipboard.setText(AnnotatedString(code)) }.padding(7.dp)
                        )
                    }
                    Text(
                        code,
                        color = c.text,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            } else if (p.isNotBlank()) {
                val cursor = live && i == parts.lastIndex
                Text(styled(p.trim('\n'), !firstDone, c, cursor), fontSize = 20.sp, lineHeight = 28.sp, color = c.text)
                firstDone = true
            }
        }
    }
}

private fun AnnotatedString.Builder.md(s: String) {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

private fun styled(raw: String, highlightFirst: Boolean, c: AppColors, cursor: Boolean): AnnotatedString {
    val text = raw.replace(Regex("(?m)^\\s*[-*] "), "• ")
    return buildAnnotatedString {
        var rest = text
        if (highlightFirst) {
            val cuts = listOf(text.indexOf(". "), text.indexOf("? "), text.indexOf("\n")).filter { it >= 0 }
            val idx = cuts.minOrNull()
            val end = when {
                idx == null -> text.length
                text[idx] == '\n' -> idx
                else -> idx + 1
            }
            withStyle(SpanStyle(color = c.yellow, fontStyle = FontStyle.Italic)) { md(text.substring(0, end)) }
            rest = text.substring(end)
        }
        md(rest)
        if (cursor) withStyle(SpanStyle(color = c.blue)) { append(" ▍") }
    }
}

@Composable
private fun InputBar(
    vm: CopilotVM,
    typed: String,
    onTyped: (String) -> Unit,
    onStart: () -> Unit,
    clipboardPaste: () -> Unit
) {
    val c = LocalAppColors.current
    var plus by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    val hasQ = typed.isNotBlank() || vm.question.isNotBlank()

    fun send() {
        val q = if (typed.isNotBlank()) typed else vm.question
        vm.generate(q)
        onTyped("")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(c.card)
            .border(BorderStroke(1.dp, c.sep), RoundedCornerShape(32.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    Icons.Rounded.Add, "Attach", tint = c.text,
                    modifier = Modifier.size(36.dp).bounceClick { plus = true }.padding(4.dp)
                )
                DropdownMenu(expanded = plus, onDismissRequest = { plus = false }) {
                    DropdownMenuItem(text = { Text("Paste question from clipboard") }, onClick = {
                        clipboardPaste()
                        plus = false
                    })
                    DropdownMenuItem(text = { Text("Clear typed text") }, onClick = {
                        onTyped("")
                        plus = false
                    })
                }
            }
            Spacer(Modifier.width(10.dp))
            AppTextField(
                value = typed,
                onChange = onTyped,
                hint = "Type a question",
                modifier = Modifier.weight(1f),
                onSend = { if (typed.isNotBlank()) send() }
            )
            Box {
                Row(Modifier.bounceClick { speedMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.g("speed"), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.text2, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf("Fast", "Standard", "Deep").forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = {
                            vm.put("speed", s)
                            speedMenu = false
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                vm.generating -> {
                    BigButton("Generate Answer", c.chipGray, c.text2, Modifier.weight(1f), enabled = false) {}
                    BigButton("Stop", c.red, Color.White, Modifier.width(112.dp)) { vm.stopGeneration() }
                }
                hasQ -> {
                    BigButton("Generate Answer", c.green, Color.White, Modifier.weight(1f)) { send() }
                    BigButton("Clear", c.chipGray, c.text, Modifier.width(112.dp)) {
                        vm.clear()
                        onTyped("")
                    }
                }
                vm.recording -> {
                    BigButton("Generate Answer", c.chipGray, c.text2, Modifier.weight(1f), enabled = false) {}
                    BigButton("Stop", c.red, Color.White, Modifier.width(112.dp)) { vm.stopRecording() }
                }
                else -> {
                    BigButton("Start Recording", Color(0xFFEAB62A), Color.Black, Modifier.weight(1f)) { onStart() }
                    BigButton("End", c.chipGray, c.text, Modifier.width(112.dp)) { vm.endSession() }
                }
            }
        }
    }
}
