package com.interviewcopilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
fun AppRoot(vm: CopilotVM) {
    val c = LocalAppColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    val bg by animateColorAsState(c.bg, tween(300), label = "bg")

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(240)) + slideInHorizontally(
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        ) { full -> dir * full / 10 }) togetherWith fadeOut(tween(120))
                    },
                    label = "tabs"
                ) { t ->
                    when (t) {
                        0 -> CopilotScreen(vm)
                        1 -> PracticeScreen(vm)
                        2 -> JobsScreen(vm)
                        else -> HistoryScreen(vm)
                    }
                }
            }
            TabBar(tab, { tab = it }, { settings = true })
        }

        AnimatedVisibility(
            visible = settings,
            enter = slideInHorizontally(
                spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            ) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200))
        ) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                SettingsScreen(vm) { settings = false }
            }
        }
    }
}

@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit, onProfile: () -> Unit) {
    val c = LocalAppColors.current
    val items = listOf(
        "Copilot" to Icons.Rounded.SmartToy,
        "Practice" to Icons.Rounded.MenuBook,
        "Jobs" to Icons.Rounded.Search,
        "History" to Icons.Rounded.History
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(34.dp))
                .background(c.card)
                .border(1.dp, c.sep, RoundedCornerShape(34.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { i, (label, icon) ->
                val on = i == selected
                val itemBg by animateColorAsState(if (on) c.chipGray else Color.Transparent, label = "tabBg")
                val tint by animateColorAsState(if (on) c.yellow else c.text, label = "tabTint")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(itemBg)
                        .bounceClick { onSelect(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
                    Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(c.card)
                .border(1.dp, c.sep, CircleShape)
                .bounceClick { onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, contentDescription = "Profile and settings", tint = c.text, modifier = Modifier.size(30.dp))
        }
    }
}
