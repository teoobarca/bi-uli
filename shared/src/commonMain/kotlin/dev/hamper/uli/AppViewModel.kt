package dev.hamper.uli

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hamper.uli.data.Loaded
import dev.hamper.uli.data.MemoryProgressStore
import dev.hamper.uli.data.MemorySessionStore
import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.data.SessionStore
import dev.hamper.uli.data.ProgressStore
import dev.hamper.uli.engine.Content
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.ExamResult
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Round
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.Topic
import dev.hamper.uli.engine.Options
import dev.hamper.uli.engine.result
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.random.Random

sealed interface Screen {
    data object Home : Screen
    data object Quiz : Screen
    data object RoundEnd : Screen
    data object ExamRun : Screen
    data object ExamEnd : Screen
    data class Browse(val topic: Topic? = null, val expanded: Int? = null) : Screen
}

@Serializable
enum class RoundKind { Smart, Mistakes }

@Serializable
data class QuizState(
    val kind: RoundKind,
    val round: Round,
    /** Display order of the current question's options (indices into the original list). */
    val order: List<Int>,
    val selected: Set<Int> = emptySet(),
    val answered: Boolean = false,
    val correct: Boolean = false,
    /** [ProgressData.seq] when this question came up: a higher count there means its answer is already in. */
    val seq: Long = 0,
    /** When this question came up (monotonic ms, 0 = unknown); taps right after it are ignored. Not saved. */
    @Transient val shownAt: Long = 0,
)

@Serializable
data class ExamState(
    val exam: Exam,
    val order: List<Int>,
    val selected: Set<Int> = emptySet(),
    /** When this question came up (monotonic ms, 0 = unknown); taps right after it are ignored. Not saved. */
    @Transient val shownAt: Long = 0,
)

data class RoundSummary(
    val kind: RoundKind,
    val ids: List<Int>,
    val score: Int,
    val total: Int,
    val mistakes: List<Int>,
    val newlyMastered: Int,
    /** Cards whose box went up this round, mastered or not: a first clean round shows this instead of "+0". */
    val climbed: Int = 0,
)

data class ExamSummary(val result: ExamResult, val exam: Exam) {
    val durationMs: Long get() = (exam.finishedAt ?: exam.startedAt) - exam.startedAt
}

class AppViewModel(
    questionsJson: String,
    private var store: ProgressStore,
    private var sessionStore: SessionStore,
    private val host: Host,
) : ViewModel() {
    val questions: List<Question> = Content.parse(questionsJson)
    val byId: Map<Int, Question> = questions.associateBy { it.id }
    val ids: List<Int> = questions.map { it.id }

    private var random: Random = Random.Default
    private var seededShuffle = false
    var clock: () -> Long = host::now
        private set

    /** Monotonic time for "this question just came up" (wall-clock jumps must not unlock or lock taps). */
    private val uptime: () -> Long = host::uptime

    var progress by mutableStateOf(ProgressData())
        private set
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var quiz by mutableStateOf<QuizState?>(null)
        private set
    var exam by mutableStateOf<ExamState?>(null)
        private set
    var roundSummary by mutableStateOf<RoundSummary?>(null)
        private set
    var examSummary by mutableStateOf<ExamSummary?>(null)
        private set

    /** The last finished exam, saved with the session so its review survives a killed process. */
    private var lastExam: Exam? = null

    /**
     * progress.json could not be read: it is backed up, the app runs on empty progress in memory, and the
     * file is not overwritten until the user says so on Domov.
     */
    var damagedProgress by mutableStateOf<Loaded.Corrupt?>(null)
        private set

    /** Screenshot session: one-shot effects render a fixed frame so captures are repeatable. */
    var shotMode by mutableStateOf(false)
        private set

    /** Boxes at the start of the round, to count what got newly mastered. */
    private var roundStart: Map<Int, Int> = emptyMap()

    private var toldAboutWriteError = false

    init {
        val loaded = store.load()
        val readable = when (loaded) {
            is Loaded.Ok -> { progress = loaded.data; true }
            Loaded.Missing -> true
            is Loaded.Corrupt -> {
                host.log("progress.json unreadable, backed up to ${loaded.backup}", loaded.error)
                damagedProgress = loaded
                false
            }
        }
        // An open round or exam survives a killed process; Domov offers to continue it.
        val saved = when (val s = sessionStore.load()) {
            is Loaded.Ok -> s.data
            Loaded.Missing -> SavedSession()
            is Loaded.Corrupt -> {
                host.log("session.json unreadable, dropped (copy at ${s.backup})", s.error)
                SavedSession()
            }
        }
        val r = Flow.restore(saved, progress, byId, catchUp = readable, shownAt = uptime())
        quiz = r.quiz
        roundStart = r.roundStart
        exam = r.exam
        lastExam = r.lastExam
        examSummary = r.lastExam?.let { ExamSummary(it.result(byId), it) }
        progress = r.progress
        if (r.progressChanged) write(progress = progress)
        // Back on the screen the process died on, when there is still something to show there.
        screen = when (val key = host.savedScreen) {
            "quiz" -> if (quiz != null) Screen.Quiz else Screen.Home
            "exam" -> if (exam != null) Screen.ExamRun else Screen.Home
            "exam-end" -> if (examSummary != null) Screen.ExamEnd else Screen.Home
            else -> if (key != null && key.startsWith("browse")) {
                Screen.Browse(Topic.entries.firstOrNull { it.key == key.substringAfter(":", "") })
            } else {
                Screen.Home
            }
        }
    }

    private fun go(to: Screen) {
        screen = to
        host.savedScreen = when (to) {
            Screen.Quiz -> "quiz"
            Screen.ExamRun -> "exam"
            Screen.ExamEnd -> "exam-end"
            is Screen.Browse -> "browse:${to.topic?.key.orEmpty()}"
            else -> "home"
        }
    }

    private fun session() = SavedSession(quiz = quiz, roundStartBoxes = roundStart, exam = exam, lastExam = lastExam)

    /**
     * One write job on the single IO lane, in the given order. Rounds save progress first (an answer that
     * reached progress but not the session is recognised by its seq); a finished exam saves the session first
     * (an exam that reached the session but not progress is caught up on the next start).
     */
    private fun write(progress: ProgressData? = null, session: SavedSession? = null, sessionFirst: Boolean = false) {
        if (shotMode) return
        val p = if (damagedProgress != null) null else progress
        val ps = store
        val ss = sessionStore
        viewModelScope.launch(host.io) {
            val steps = buildList<() -> Unit> {
                if (p != null) add { ps.save(p) }
                if (session != null) add { ss.save(session) }
                if (sessionFirst) reverse()
            }
            for (step in steps) {
                try {
                    step()
                } catch (e: Exception) {
                    host.log("save failed", e)
                    if (!toldAboutWriteError) {
                        toldAboutWriteError = true
                        host.saveFailed()
                    }
                }
            }
        }
    }

    private fun saveSession() = write(session = session())

    private fun persist(data: ProgressData) {
        progress = data
        write(progress = data)
    }

    // Damaged progress file

    /** Tries progress.json again (e.g. after it was restored by hand). */
    fun retryLoadProgress() {
        when (val r = store.load()) {
            is Loaded.Ok -> {
                damagedProgress = null
                // The open round and exam ran against the empty progress: move them onto the loaded one.
                val b = Flow.rebase(session(), r.data, byId)
                quiz = b.quiz
                roundStart = b.roundStart
                exam = b.exam
                lastExam = b.lastExam
                examSummary = b.lastExam?.let { ExamSummary(it.result(byId), it) }
                progress = b.progress
                write(progress = if (b.progressChanged) progress else null, session = session())
            }
            Loaded.Missing -> { damagedProgress = null; write(progress = progress) }
            is Loaded.Corrupt -> damagedProgress = r
        }
    }

    /** The user saw the warning: from now on the in-memory progress replaces the damaged file (its copy stays). */
    fun acceptFreshProgress() {
        damagedProgress = null
        write(progress = progress)
    }

    private fun shuffledOrder(q: Question): List<Int> {
        val r = if (seededShuffle) Random(q.id * 31 + 7) else random
        return Options.order(q, r)
    }

    // Home

    /** What a Chyby round drills right now (up to a round's worth). */
    fun mistakeIds(): List<Int> = Srs.mistakes(ids, progress, clock())

    /** Every Chyby card, for the true count on Domov (a round still drills up to 10). */
    fun allMistakeIds(): List<Int> = Srs.allMistakes(ids, progress, clock())

    /** Nothing answered yet and nothing open: the main button says "Začať". */
    val isFresh: Boolean get() = progress.cards.isEmpty() && quiz == null

    fun nextRoundPreview(): List<Int> = Srs.buildRound(ids, progress, clock())

    fun toggleSk() = persist(progress.copy(showSk = !progress.showSk))

    fun resetProgress() {
        quiz = null
        exam = null
        lastExam = null
        examSummary = null
        roundStart = emptyMap()
        progress = ProgressData(showSk = progress.showSk)
        // Session first: a kill in between leaves the old progress (reset again), never an old exam to replay.
        write(progress = progress, session = session(), sessionFirst = true)
    }

    // Learning rounds

    fun startRound() = startRound(RoundKind.Smart, Srs.buildRound(ids, progress, clock()))

    /** Domov's main button: back into the open round, or a fresh smart round. */
    fun continueOrStart() {
        if (quiz != null) go(Screen.Quiz) else startRound()
    }

    fun startMistakes() {
        val m = mistakeIds()
        if (m.isNotEmpty()) startRound(RoundKind.Mistakes, m)
    }

    private fun startRound(kind: RoundKind, roundIds: List<Int>) {
        roundStart = roundIds.associateWith { progress.card(it).box }
        val round = Round(roundIds)
        quiz = QuizState(kind = kind, round = round, order = shuffledOrder(byId.getValue(round.current!!)), seq = progress.seq, shownAt = uptime())
        saveSession()
        go(Screen.Quiz)
    }

    fun current(): Question? = quiz?.round?.current?.let(byId::get)

    /** A tap on option [index] of question [questionId]; single and truefalse answer on the tap. */
    fun pick(questionId: Int, index: Int) {
        val s = quiz ?: return
        val q = current() ?: return
        val picked = Flow.pick(s, q, questionId, index, uptime()) ?: return
        quiz = picked
        if (q.kind == Kind.Multi) saveSession() else submit(questionId)
    }

    fun submit(questionId: Int) {
        val s = quiz ?: return
        val q = current() ?: return
        if (s.answered || q.id != questionId) return
        if (q.kind == Kind.Multi && s.selected.size != q.choose) return
        val (answered, updated) = Flow.submit(s, q, progress, clock())
        quiz = answered
        progress = updated
        write(progress = updated, session = session())
    }

    fun next() {
        val s = quiz ?: return
        if (!s.answered) return
        val round = s.round.next()
        if (round.isFinished) {
            val mastered = round.ids.count { (roundStart[it] ?: 0) < Srs.MASTERED_BOX && progress.card(it).isMastered }
            val climbed = round.ids.distinct().count { progress.card(it).box > (roundStart[it] ?: 0) }
            roundSummary = RoundSummary(s.kind, round.ids, round.score, round.total, round.mistakes, mastered, climbed)
            quiz = null
            roundStart = emptyMap()
            go(Screen.RoundEnd)
        } else {
            quiz = QuizState(kind = s.kind, round = round, order = shuffledOrder(byId.getValue(round.current!!)), seq = progress.seq, shownAt = uptime())
        }
        saveSession()
    }

    // Mock exam

    fun startExam() {
        val e = Exam.start(ids, clock(), random)
        exam = ExamState(e, shuffledOrder(byId.getValue(e.current!!)), shownAt = uptime())
        // The last attempt's review stays until this one has its first answer: an attempt dropped with no
        // answer does not cost it.
        saveSession()
        go(Screen.ExamRun)
    }

    fun resumeExam() {
        if (exam != null) go(Screen.ExamRun)
    }

    /** Domov's line under Skúška nanečisto: back to the last finished exam's result and mistakes. */
    fun openLastExam() {
        if (examSummary != null) go(Screen.ExamEnd)
    }

    fun discardExam() {
        exam = null
        saveSession()
    }

    /** Leaves the exam: an attempt with no answer yet is simply dropped, anything else stays open. */
    fun leaveExam() {
        if ((exam?.exam?.index ?: 0) == 0) discardExam()
        home()
    }

    fun examPick(questionId: Int, index: Int) {
        val s = exam ?: return
        val q = s.exam.current?.let(byId::get) ?: return
        exam = Flow.examPick(s, q, questionId, index, uptime()) ?: return
        saveSession()
    }

    fun examNext(questionId: Int) {
        val s = exam ?: return
        val q = s.exam.current?.let(byId::get) ?: return
        if (q.id != questionId || s.selected.size != q.choose) return
        val e = s.exam.answer(s.selected, clock())
        // A new attempt with an answer replaces the last one's review.
        lastExam = null
        examSummary = null
        if (e.isFinished) {
            finishExam(e)
        } else {
            exam = ExamState(e, shuffledOrder(byId.getValue(e.current!!)), shownAt = uptime())
            saveSession()
        }
    }

    private fun finishExam(e: Exam) {
        // The mock exam is real practice: every answer moves its card like in a round, exactly once.
        progress = Flow.applyExam(progress, e, byId, clock())
        examSummary = ExamSummary(e.result(byId), e)
        lastExam = e
        exam = null
        write(progress = progress, session = session(), sessionFirst = true)
        go(Screen.ExamEnd)
    }

    // Navigation

    fun openBrowse(topic: Topic? = null) {
        go(Screen.Browse(topic))
    }

    /** Leaving mid-round or mid-exam keeps it open (and saved); Domov offers to continue. */
    fun home() {
        go(Screen.Home)
    }

    // Debug screenshots

    fun applyShot(name: String) {
        Shots.apply(this, name)
    }

    internal fun installShot(
        data: ProgressData,
        now: Long,
        screen: Screen,
        quiz: QuizState? = null,
        exam: ExamState? = null,
        roundSummary: RoundSummary? = null,
        examSummary: ExamSummary? = null,
    ) {
        store = MemoryProgressStore(data)
        sessionStore = MemorySessionStore()
        seededShuffle = true
        shotMode = true
        damagedProgress = null
        random = Random(42)
        clock = { now }
        progress = data
        this.quiz = quiz
        this.exam = exam
        this.roundSummary = roundSummary
        this.examSummary = examSummary
        this.screen = screen
    }

    internal fun orderFor(q: Question): List<Int> = shuffledOrder(q)

    internal fun enableSeededShuffle() {
        seededShuffle = true
    }
}
