package com.interviewcopilot.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.interviewcopilot.app.logic.Bank
import com.interviewcopilot.app.vm.CopilotVM

@Composable
fun PracticeScreen(vm: CopilotVM) {
    val c = LocalAppColors.current
    var topic by remember { mutableStateOf("Java") }
    var index by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    val questions = Bank.topics[topic].orEmpty()
    val question = questions.getOrElse(index % questions.size.coerceAtLeast(1)) { "" }
    val scores = vm.practiceScores()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        ScreenTitle("Practice")
        if (scores.isNotEmpty()) {
            Text(
                "Answered: ${scores.size}  ·  Average score: ${"%.1f".format(scores.average())}/10",
                color = c.text2, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)
            )
        }
        ChipChoice(Bank.topics.keys.toList(), topic) {
            topic = it
            index = 0
            answer = ""
            vm.practiceOut = ""
        }
        Spacer(Modifier.height(14.dp))
        SectionCard("Question") {
            Text(question, color = c.text, fontSize = 20.sp, lineHeight = 28.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppChip("Ask aloud", c.chipBlue, c.text) { vm.speak(question) }
                AppChip("Next question", c.chipGray, c.text) {
                    index += 1
                    answer = ""
                    vm.practiceOut = ""
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionCard("Your answer") {
            BoxedField(answer, { answer = it }, "Type or dictate your answer", singleLine = false, minLines = 5)
            BigButton(
                if (vm.practiceBusy) "Scoring…" else "Score my answer",
                c.green, Color.White, Modifier.fillMaxWidth(), enabled = !vm.practiceBusy && answer.isNotBlank()
            ) { vm.scorePractice(question, answer) }
        }
        if (vm.practiceOut.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.card)
                    .padding(18.dp)
            ) {
                Text("Feedback", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(vm.practiceOut, color = c.text, fontSize = 17.sp, lineHeight = 25.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
