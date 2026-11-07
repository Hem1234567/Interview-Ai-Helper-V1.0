package com.interviewcopilot.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun SettingsScreen(vm: CopilotVM, onBack: () -> Unit) {
    val c = LocalAppColors.current
    var confirmWipe by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ChevronLeft, "Back", tint = c.blue,
                modifier = Modifier.size(48.dp).bounceClick { onBack() }.padding(8.dp)
            )
            Text("Settings", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard("Answer engine") {
                Text("Primary engine", color = c.text2, fontSize = 14.sp)
                ChipChoice(listOf("OpenRouter", "Gemini", "Local"), vm.g("provider")) { vm.put("provider", it) }
                SwitchRow("Auto fallback to other engines", vm.b("fallback")) { vm.put("fallback", it.toString()) }
                Text("If the primary engine fails or is slow, the next ready engine answers automatically.", color = c.text2, fontSize = 13.sp)
            }
            SectionCard("OpenRouter") {
                BoxedField(vm.g("orKey"), { vm.put("orKey", it) }, "OpenRouter API key", secret = true)
                BoxedField(vm.g("orModel"), { vm.put("orModel", it) }, "Model id, e.g. google/gemini-2.0-flash-001")
                Text("Fast picks: google/gemini-2.0-flash-001, openai/gpt-4o-mini, meta-llama/llama-3.3-70b-instruct", color = c.text2, fontSize = 13.sp)
            }
            SectionCard("Google Gemini") {
                BoxedField(vm.g("geminiKey"), { vm.put("geminiKey", it) }, "Google AI Studio API key", secret = true)
                BoxedField(vm.g("geminiModel"), { vm.put("geminiModel", it) }, "Model id, e.g. gemini-2.0-flash")
            }
            SectionCard("Local model (on device)") {
                Text(
                    if (vm.g("localPath").isBlank()) "No model imported." else "Model: ${vm.g("localName")}",
                    color = c.text, fontSize = 16.sp
                )
                if (vm.modelStatus.isNotBlank()) Text(vm.modelStatus, color = c.text2, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppChip("Import model file", c.blue, Color.White) { picker.launch(arrayOf("*/*")) }
                    if (vm.g("localPath").isNotBlank()) AppChip("Remove", c.chipRed, c.text) { vm.removeModel() }
                }
                Text(
                    "Import a MediaPipe LLM .task model, for example Gemma 2B or Gemma 3 1B (int4) from Kaggle or Hugging Face. 4 GB or more RAM recommended.",
                    color = c.text2, fontSize = 13.sp
                )
            }
            SectionCard("Speech") {
                Text("Recognition language", color = c.text2, fontSize = 14.sp)
                ChipChoice(listOf("en-US", "en-IN", "en-GB"), vm.g("lang")) { vm.put("lang", it) }
                SwitchRow("Auto-generate when a question is heard", vm.b("auto")) { vm.put("auto", it.toString()) }
                Text("Custom vocabulary, one rule per line as wrong=correct", color = c.text2, fontSize = 14.sp)
                BoxedField(vm.g("vocab"), { vm.put("vocab", it) }, "spring boot=Spring Boot", singleLine = false, minLines = 3)
            }
            SectionCard("Personalization") {
                Text("Answer style", color = c.text2, fontSize = 14.sp)
                ChipChoice(listOf("Concise", "Confident", "STAR", "Simple English"), vm.g("style")) { vm.put("style", it) }
                Text("Resume (paste text). Used to personalise every answer.", color = c.text2, fontSize = 14.sp)
                BoxedField(vm.g("resume"), { vm.put("resume", it) }, "Paste your resume text", singleLine = false, minLines = 6)
            }
            SectionCard("Appearance") {
                ChipChoice(listOf("System", "Light", "Dark"), vm.g("theme")) { vm.put("theme", it) }
            }
            SectionCard("Privacy") {
                Text(
                    "API keys are stored encrypted on this device. History, jobs and resume stay on the device. Questions are sent only to the engine you choose.",
                    color = c.text2, fontSize = 14.sp
                )
                AppChip("Delete all data", c.chipRed, c.red) { confirmWipe = true }
            }
            SectionCard("About") {
                Text("Interview Copilot 1.0", color = c.text, fontSize = 16.sp)
                Text(
                    "You are responsible for following the rules of any interview or employer about AI assistance. Best used for practice, preparation and where permitted.",
                    color = c.text2, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = c.card,
            title = { Text("Delete all data?", color = c.text) },
            text = { Text("This removes keys, history, jobs, resume and the imported model.", color = c.text2) },
            confirmButton = {
                TextButton(onClick = {
                    vm.wipeAll()
                    confirmWipe = false
                }) { Text("Delete", color = c.red) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel", color = c.text2) } }
        )
    }
}
