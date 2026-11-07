package com.interviewcopilot.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun JobsScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val shown = vm.jobs.filter { query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        ScreenTitle("Jobs")
        BoxedField(query, { query = it }, "Search saved jobs")
        Spacer(Modifier.height(14.dp))
        SectionCard("Add a job") {
            BoxedField(title, { title = it }, "Job title and company")
            BoxedField(desc, { desc = it }, "Paste the job description", singleLine = false, minLines = 5)
            BigButton("Save job", c.blue, Color.White, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && desc.isNotBlank()) {
                vm.addJob(title, desc)
                title = ""
                desc = ""
            }
        }
        Spacer(Modifier.height(14.dp))
        if (shown.isEmpty()) {
            Text("No saved jobs yet. The active job tailors every Copilot answer.", color = c.text2, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        }
        shown.forEach { job ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .animateContentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (job.active) {
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(c.green).padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Active", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Text(job.description.take(220) + if (job.description.length > 220) "…" else "", color = c.text2, fontSize = 15.sp)
                if (job.keywords.isNotBlank()) {
                    Text(job.keywords, color = c.text, fontSize = 15.sp, lineHeight = 22.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip(if (job.active) "Deactivate" else "Set active", c.chipBlue, c.text) { vm.setActiveJob(job.id) }
                    AppChip(if (vm.jobBusyId == job.id) "Analysing…" else "Analyse", c.chipGray, c.text) { vm.analyzeJob(job.id) }
                    AppChip("Delete", c.chipRed, c.text) { vm.deleteJob(job.id) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
