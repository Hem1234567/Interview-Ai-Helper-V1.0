package com.interviewcopilot.app.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interviewcopilot.app.data.QA
import com.interviewcopilot.app.vm.CopilotVM
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var onlyFav by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf("") }

    val filtered = vm.history.filter {
        (!onlyFav || it.favorite) &&
            (query.isBlank() || it.question.contains(query, true) || it.answer.contains(query, true))
    }
    val groups = filtered.groupBy { it.session }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }
    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        ScreenTitle("History")
        BoxedField(query, { query = it }, "Search questions and answers")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("All", if (!onlyFav) c.blue else c.chipGray, if (!onlyFav) androidx.compose.ui.graphics.Color.White else c.text) { onlyFav = false }
            AppChip("Favorites", if (onlyFav) c.blue else c.chipGray, if (onlyFav) androidx.compose.ui.graphics.Color.White else c.text) { onlyFav = true }
        }
        Spacer(Modifier.height(14.dp))
        if (groups.isEmpty()) {
            Text("No sessions yet. Your questions and answers are saved on this device only.", color = c.text2, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        }
        groups.forEach { (_, items) ->
            val sorted = items.sortedBy { it.time }
            val head = "${fmt.format(Date(sorted.first().time))}  ·  ${sorted.size} question${if (sorted.size == 1) "" else "s"}  ·  ${sorted.first().mode}"
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(head, color = c.text2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AppChip("Export", c.chipGray, c.text) {
                    share(sorted.mapIndexed { i, q -> "Q${i + 1}. ${q.question}\n${q.answer}" }.joinToString("\n\n"))
                }
            }
            sorted.forEach { qa -> QaCard(qa, openId == qa.id, { openId = if (openId == qa.id) "" else qa.id }, vm, ::share) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QaCard(qa: QA, open: Boolean, toggle: () -> Unit, vm: CopilotVM, share: (String) -> Unit) {
    val c = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .bounceClick { toggle() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            qa.question, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = if (open) 10 else 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            qa.answer, color = if (open) c.text else c.text2, fontSize = 15.sp, lineHeight = 22.sp,
            maxLines = if (open) 200 else 2, overflow = TextOverflow.Ellipsis
        )
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (qa.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite",
                    tint = c.yellow, modifier = Modifier.size(40.dp).bounceClick { vm.toggleFavorite(qa.id) }.padding(8.dp)
                )
                Icon(
                    Icons.Rounded.ContentCopy, "Copy", tint = c.text,
                    modifier = Modifier.size(40.dp).bounceClick { clipboard.setText(AnnotatedString(qa.answer)) }.padding(8.dp)
                )
                Icon(
                    Icons.Rounded.Share, "Share", tint = c.text,
                    modifier = Modifier.size(40.dp).bounceClick { share("${qa.question}\n\n${qa.answer}") }.padding(8.dp)
                )
                Icon(
                    Icons.Rounded.Delete, "Delete", tint = c.red,
                    modifier = Modifier.size(40.dp).bounceClick { vm.deleteQA(qa.id) }.padding(8.dp)
                )
            }
        }
    }
}
