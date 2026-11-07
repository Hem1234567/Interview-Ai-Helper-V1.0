package com.interviewcopilot.app.vm

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.interviewcopilot.app.data.Defaults
import com.interviewcopilot.app.data.JobProfile
import com.interviewcopilot.app.data.QA
import com.interviewcopilot.app.data.Store
import com.interviewcopilot.app.logic.Detector
import com.interviewcopilot.app.logic.GeminiProvider
import com.interviewcopilot.app.logic.LlmProvider
import com.interviewcopilot.app.logic.LocalEngine
import com.interviewcopilot.app.logic.LocalProvider
import com.interviewcopilot.app.logic.OpenRouterProvider
import com.interviewcopilot.app.logic.Prompts
import com.interviewcopilot.app.logic.SpeechEngine
import com.interviewcopilot.app.logic.Vocab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class CopilotVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val store = Store(ctx)

    // ---------- settings ----------
    val prefs = mutableStateMapOf<String, String>().apply { putAll(store.all()) }
    fun g(key: String): String = prefs[key] ?: Defaults.map[key] ?: ""
    fun b(key: String): Boolean = g(key) == "true"
    fun put(key: String, value: String) {
        prefs[key] = value
        store.put(key, value)
    }

    // ---------- live copilot state ----------
    var mode by mutableStateOf("Interview")
    var recording by mutableStateOf(false)
    var partial by mutableStateOf("")
    val transcript = mutableStateListOf<String>()
    var question by mutableStateOf("")
    var questionTime by mutableStateOf(System.currentTimeMillis())
    var answerRaw by mutableStateOf("")
    var generating by mutableStateOf(false)
    var followups by mutableStateOf<List<String>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var level by mutableFloatStateOf(0f)
    var engineUsed by mutableStateOf("")
    var firstTokenMs by mutableStateOf(0L)

    val session = mutableStateListOf<QA>()
    var viewIndex by mutableIntStateOf(-1)
    private var sessionId = UUID.randomUUID().toString()

    val history = mutableStateListOf<QA>().apply { addAll(store.loadHistory()) }
    val jobs = mutableStateListOf<JobProfile>().apply { addAll(store.loadJobs()) }

    // ---------- practice / jobs ----------
    var practiceOut by mutableStateOf("")
    var practiceBusy by mutableStateOf(false)
    var jobBusyId by mutableStateOf("")
    var modelStatus by mutableStateOf("")

    private var genJob: Job? = null
    private var speech: SpeechEngine? = null
    private var tts: TextToSpeech? = null

    init {
        mode = g("mode")
        val p = g("localPath")
        if (p.isNotBlank() && File(p).exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    LocalEngine.get(ctx, p)
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    // ---------- derived ----------
    val shownQuestion: String
        get() = if (viewIndex in session.indices) session[viewIndex].question else question
    val shownAnswer: String
        get() = if (viewIndex in session.indices) session[viewIndex].answer else answerRaw
    val shownTime: Long
        get() = if (viewIndex in session.indices) session[viewIndex].time else questionTime
    val shownNumber: Int
        get() = if (viewIndex in session.indices) viewIndex + 1 else session.size + 1

    fun setMode(m: String) {
        mode = m
        put("mode", m)
    }

    // ---------- recording ----------
    fun startRecording() {
        if (recording) return
        error = null
        recording = true
        partial = ""
        val engine = SpeechEngine(
            ctx,
            g("lang"),
            cbPartial = { partial = it },
            cbFinal = { onFinalText(it) },
            cbRms = { level = it },
            cbError = {
                error = it
                recording = false
            }
        )
        speech = engine
        engine.start()
    }

    fun stopRecording() {
        speech?.stop()
        speech = null
        recording = false
        partial = ""
        level = 0f
    }

    private fun onFinalText(raw: String) {
        val t = Vocab.apply(raw, g("vocab"))
        partial = ""
        transcript.add(t)
        if (Detector.isQuestion(t)) {
            question = t
            questionTime = System.currentTimeMillis()
            viewIndex = -1
            if (b("auto")) generate(t)
        }
    }

    fun endSession() {
        stopRecording()
        genJob?.cancel()
        generating = false
        session.clear()
        transcript.clear()
        question = ""
        answerRaw = ""
        followups = emptyList()
        viewIndex = -1
        sessionId = UUID.randomUUID().toString()
    }

    fun clear() {
        genJob?.cancel()
        generating = false
        question = ""
        answerRaw = ""
        followups = emptyList()
        viewIndex = -1
        error = null
    }

    fun stopGeneration() {
        genJob?.cancel()
        generating = false
    }

    fun prev() {
        if (session.isEmpty()) return
        viewIndex = if (viewIndex == -1) session.lastIndex else (viewIndex - 1).coerceAtLeast(0)
        followups = emptyList()
    }

    fun next() {
        if (viewIndex == -1) return
        viewIndex = if (viewIndex >= session.lastIndex) -1 else viewIndex + 1
        followups = emptyList()
    }

    // ---------- generation ----------
    private fun activeJob(): String = jobs.firstOrNull { it.active }?.let { it.title + "\n" + it.description } ?: ""

    fun generate(q: String, extra: String = "") {
        val text = q.trim()
        if (text.isEmpty()) return
        genJob?.cancel()
        question = text
        if (extra.isEmpty()) questionTime = System.currentTimeMillis()
        answerRaw = ""
        followups = emptyList()
        error = null
        generating = true
        viewIndex = -1
        val startedAt = System.currentTimeMillis()
        val currentMode = mode
        genJob = viewModelScope.launch {
            try {
                val (sys, user, max) = Prompts.build(
                    currentMode, g("style"), g("speed"), g("resume"), activeJob(), g("vocab"),
                    session.map { it.question to it.answer }, text, extra
                )
                var raw = ""
                var gotFirst = false
                runProviders(sys, user, max, onAttempt = { raw = ""; answerRaw = ""; gotFirst = false }) { tok ->
                    if (!gotFirst) {
                        gotFirst = true
                        firstTokenMs = System.currentTimeMillis() - startedAt
                    }
                    raw += tok
                    answerRaw = cleanAnswer(raw)
                }
                val clean = cleanAnswer(raw)
                if (clean.isNotBlank()) {
                    followups = parseFollowups(raw)
                    val qa = QA(UUID.randomUUID().toString(), sessionId, text, clean, System.currentTimeMillis(), currentMode)
                    session.add(qa)
                    history.add(qa)
                    store.saveHistory(history.toList())
                    viewIndex = session.lastIndex
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not get an answer."
            } finally {
                generating = false
            }
        }
    }

    fun followUp(extra: String) {
        val q = shownQuestion
        if (q.isBlank()) return
        val prev = shownAnswer
        val full = if (prev.isNotBlank()) "$extra\n\nPrevious answer for reference:\n${prev.take(900)}" else extra
        generate(q, full)
    }

    fun fixTerm(wrong: String, right: String) {
        if (wrong.isBlank() || right.isBlank()) return
        val rules = g("vocab")
        put("vocab", (if (rules.isBlank()) "" else rules.trimEnd() + "\n") + "${wrong.trim()}=${right.trim()}")
        val fixed = shownQuestion.replace(wrong.trim(), right.trim(), ignoreCase = true)
        generate(fixed)
    }

    private fun cleanAnswer(raw: String): String {
        val i = raw.indexOf("###")
        return (if (i >= 0) raw.substring(0, i) else raw).trim()
    }

    private fun parseFollowups(raw: String): List<String> {
        val marker = "###FOLLOWUPS:"
        val i = raw.indexOf(marker)
        if (i < 0) return emptyList()
        return raw.substring(i + marker.length).split("|")
            .map { it.trim().trim('.', '"', '\'') }
            .filter { it.isNotBlank() && it.length < 40 }
            .take(3)
    }

    private fun providers(): List<LlmProvider> {
        val or = g("orKey").trim()
        val gem = g("geminiKey").trim()
        val lp = g("localPath")
        val all = mutableMapOf<String, LlmProvider>()
        if (or.isNotEmpty()) all["OpenRouter"] = OpenRouterProvider(or, g("orModel").trim())
        if (gem.isNotEmpty()) all["Gemini"] = GeminiProvider(gem, g("geminiModel").trim())
        if (lp.isNotEmpty() && File(lp).exists()) all["Local"] = LocalProvider(ctx, lp, g("localName"))
        val primary = g("provider")
        val order = listOf(primary) + listOf("OpenRouter", "Gemini", "Local").filter { it != primary }
        val list = order.mapNotNull { all[it] }
        return if (b("fallback")) list else list.take(1).filter { it.name == primary }
    }

    private suspend fun runProviders(
        sys: String,
        user: String,
        max: Int,
        onAttempt: () -> Unit,
        onToken: (String) -> Unit
    ) {
        val list = providers()
        if (list.isEmpty()) {
            throw IOException("No answer engine is ready. Add an OpenRouter or Gemini key, or import a local model in Settings.")
        }
        var last: Exception? = null
        for (p in list) {
            try {
                onAttempt()
                engineUsed = p.name
                attempt(p, sys, user, max, onToken)
                return
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) throw e
                last = e
            }
        }
        throw last ?: IOException("Could not get an answer.")
    }

    private suspend fun attempt(p: LlmProvider, sys: String, user: String, max: Int, onToken: (String) -> Unit) {
        var got = false
        coroutineScope {
            val job = launch {
                p.stream(sys, user, max).collect {
                    got = true
                    onToken(it)
                }
            }
            val watchdog = launch {
                delay(p.firstTokenTimeoutMs)
                if (!got) job.cancel(CancellationException("slow"))
            }
            job.join()
            watchdog.cancel()
        }
        if (!got) throw IOException("${p.name} did not respond in time.")
    }

    /** Non-streaming-style helper used by Practice and Jobs. */
    private fun runPlain(sys: String, user: String, max: Int, onText: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                var acc = ""
                runProviders(sys, user, max, onAttempt = { acc = "" }) { tok ->
                    acc += tok
                    onText(acc)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
                onText("Error: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    // ---------- practice ----------
    fun scorePractice(question: String, answer: String) {
        if (answer.isBlank() || practiceBusy) return
        practiceBusy = true
        practiceOut = ""
        runPlain(
            Prompts.SCORE,
            "Interview question: $question\n\nCandidate answer: $answer",
            700,
            onText = { practiceOut = it },
            onDone = {
                practiceBusy = false
                val m = Regex("Score:\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*10").find(practiceOut)
                if (m != null) {
                    val old = g("pscores")
                    put("pscores", if (old.isBlank()) m.groupValues[1] else "$old,${m.groupValues[1]}")
                }
            }
        )
    }

    fun practiceScores(): List<Double> = g("pscores").split(",").mapNotNull { it.trim().toDoubleOrNull() }

    fun speak(text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q")
        } else {
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q")
                }
            }
        }
    }

    // ---------- jobs ----------
    fun addJob(title: String, description: String) {
        if (title.isBlank() || description.isBlank()) return
        jobs.add(0, JobProfile(UUID.randomUUID().toString(), title.trim(), description.trim()))
        store.saveJobs(jobs.toList())
    }

    fun setActiveJob(id: String) {
        val updated = jobs.map { it.copy(active = it.id == id && !it.active) }
        jobs.clear()
        jobs.addAll(updated)
        store.saveJobs(jobs.toList())
    }

    fun deleteJob(id: String) {
        jobs.removeAll { it.id == id }
        store.saveJobs(jobs.toList())
    }

    fun analyzeJob(id: String) {
        val job = jobs.firstOrNull { it.id == id } ?: return
        if (jobBusyId.isNotEmpty()) return
        jobBusyId = id
        runPlain(
            Prompts.JOB_ANALYSIS,
            "Job title: ${job.title}\n\n${job.description.take(3000)}",
            700,
            onText = { text ->
                val idx = jobs.indexOfFirst { it.id == id }
                if (idx >= 0) jobs[idx] = jobs[idx].copy(keywords = text)
            },
            onDone = {
                jobBusyId = ""
                store.saveJobs(jobs.toList())
            }
        )
    }

    // ---------- history ----------
    fun toggleFavorite(id: String) {
        val idx = history.indexOfFirst { it.id == id }
        if (idx >= 0) {
            history[idx] = history[idx].copy(favorite = !history[idx].favorite)
            store.saveHistory(history.toList())
        }
    }

    fun deleteQA(id: String) {
        history.removeAll { it.id == id }
        store.saveHistory(history.toList())
    }

    // ---------- local model ----------
    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { modelStatus = "Copying model…" }
                var display = "model"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) display = c.getString(idx) ?: "model"
                }
                val out = File(ctx.filesDir, "local_model.task")
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                LocalEngine.reset()
                withContext(Dispatchers.Main) {
                    put("localPath", out.absolutePath)
                    put("localName", display)
                    modelStatus = "Loading model…"
                }
                LocalEngine.get(ctx, out.absolutePath)
                withContext(Dispatchers.Main) { modelStatus = "Ready: $display" }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { modelStatus = "Failed: ${e.message}" }
            }
        }
    }

    fun removeModel() {
        LocalEngine.reset()
        File(ctx.filesDir, "local_model.task").delete()
        put("localPath", "")
        put("localName", "")
        modelStatus = ""
    }

    fun wipeAll() {
        endSession()
        LocalEngine.reset()
        store.wipe()
        prefs.clear()
        history.clear()
        jobs.clear()
        modelStatus = ""
    }

    override fun onCleared() {
        speech?.stop()
        tts?.shutdown()
        super.onCleared()
    }
}
