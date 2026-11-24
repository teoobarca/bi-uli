package dev.hamper.uli

import dev.hamper.uli.engine.CardState
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Round
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.result
import kotlin.random.Random

/**
 * Deterministic states for screenshots, opened with
 * `adb shell am start -n dev.hamper.uli.debug/dev.hamper.uli.MainActivity --es shot <name>`.
 * Only reachable from debug builds (MainActivity checks BuildConfig.DEBUG).
 */
object Shots {
    val names = listOf("home", "q-single", "q-wrong", "q-right", "q-multi", "round-end", "exam-result", "browse")

    private const val NOW = 1_793_707_200_000L // 2026-11-03 12:00 UTC
    private const val MIN = 60_000L

    /** ~25 mastered, ~15 learning, the rest new: the first 40 questions in course order have been seen. */
    fun seededProgress(ids: List<Int>): ProgressData {
        val seen = ids.take(40).shuffled(Random(5))
        val cards = mutableMapOf<Int, CardState>()
        seen.forEachIndexed { i, id ->
            cards[id] = if (i < 25) {
                val box = 3 + i % 3
                CardState(box = box, lastSeenAt = NOW - (i + 2) * 60 * MIN, dueAt = NOW + (i - 3) * 60 * MIN, timesRight = box, timesWrong = i % 4 / 3)
            } else {
                val box = 1 + i % 2
                CardState(box = box, lastSeenAt = NOW - (i % 5 + 1) * 30 * MIN, dueAt = NOW - i * MIN, timesRight = box - 1, timesWrong = 1 + i % 3)
            }
        }
        return ProgressData(cards = cards, showSk = false)
    }

    private val roundIds = listOf(12, 30, 44, 67, 41, 2, 55, 61, 70, 35)

    /** The progress right after answering [id], as the round would have saved it. */
    private fun answered(data: ProgressData, id: Int, correct: Boolean) =
        data.copy(cards = data.cards + (id to Srs.answer(data.card(id), correct, NOW)))

    fun apply(vm: AppViewModel, name: String) {
        val data = seededProgress(vm.ids)
        vm.enableSeededShuffle()
        when (name) {
            "q-single", "q-wrong" -> {
                val r = Round(roundIds).answer(true).next().answer(false).next().answer(true).next()
                val q = vm.byId.getValue(67)
                var state = QuizState(RoundKind.Smart, r, vm.orderFor(q))
                if (name == "q-wrong") {
                    val pick = q.options.indexOfFirst { it.text.contains("2755") }
                    state = state.copy(round = r.answer(false), selected = setOf(pick), answered = true, correct = false)
                }
                val shown = if (name == "q-wrong") answered(data, 67, false) else data
                vm.installShot(shown, NOW, Screen.Quiz, quiz = state)
            }
            "q-right" -> {
                val ids = listOf(3, 18, 21, 33, 52, 27, 60, 64, 14, 49)
                // A miss on the first card, fixed three cards later, then a run: Q27 makes it five in a row.
                var r = Round(ids).answer(false).next()
                repeat(5) { r = r.answer(true).next() }
                check(r.current == 27)
                val q = vm.byId.getValue(27)
                val state = QuizState(RoundKind.Smart, r.answer(true), vm.orderFor(q), selected = q.correctIndices, answered = true, correct = true)
                vm.installShot(answered(data, 27, true), NOW, Screen.Quiz, quiz = state)
            }
            "q-multi" -> {
                val ids = listOf(8, 5, 22, 29, 36, 47, 53, 58, 62, 69)
                val r = Round(ids).answer(true).next()
                val q = vm.byId.getValue(5)
                val state = QuizState(RoundKind.Smart, r, vm.orderFor(q), selected = q.correctIndices.take(2).toSet())
                vm.installShot(data, NOW, Screen.Quiz, quiz = state)
            }
            // The two misses happened a few minutes ago, so the note says when they really come back.
            "round-end" -> vm.installShot(
                data.copy(cards = data.cards + listOf(67, 33).associateWith { Srs.answer(data.card(it), false, NOW - 4 * MIN) }),
                NOW, Screen.RoundEnd,
                roundSummary = RoundSummary(RoundKind.Smart, roundIds, score = 8, total = 10, mistakes = listOf(67, 33), newlyMastered = 3),
            )
            // A first clean round: nothing is mastered yet, so the card shows the cards that moved up.
            "round-end-first" -> vm.installShot(
                ProgressData(), NOW, Screen.RoundEnd,
                roundSummary = RoundSummary(RoundKind.Smart, (1..10).toList(), score = 10, total = 10, mistakes = emptyList(), newlyMastered = 0, climbed = 10),
            )
            "exam-result" -> {
                val rnd = Random(3)
                var e = Exam.start(vm.ids, NOW - (23 * 60 + 41) * 1000L, rnd)
                val wrong = e.ids.shuffled(Random(9)).take(8).toSet()
                for (id in e.ids) {
                    val q = vm.byId.getValue(id)
                    val pick = if (id in wrong) setOf(q.options.indexOfFirst { !it.correct }).let { w ->
                        if (q.choose > 1) w + q.correctIndices.drop(1) else w
                    } else q.correctIndices
                    e = e.answer(pick, NOW)
                }
                // As after a real exam: the answers have already moved their cards.
                val res = e.result(vm.byId)
                vm.installShot(Srs.applyExam(data, e.ids, res.wrong, NOW), NOW, Screen.ExamEnd, examSummary = ExamSummary(res, e))
            }
            "browse" -> vm.installShot(data, NOW, Screen.Browse(expanded = 2))
            // Extra states for manual checks (not in the frozen capture list).
            "q-retry" -> {
                // Q61 (users, the longest topic name) coming back as a correction.
                val ids = listOf(61, 12, 30, 44, 67)
                val r = Round(ids).answer(false).next().answer(true).next().answer(true).next()
                val q = vm.byId.getValue(r.current!!)
                vm.installShot(data, NOW, Screen.Quiz, quiz = QuizState(RoundKind.Smart, r, vm.orderFor(q)))
            }
            "q-disputed" -> {
                val r = Round(listOf(33, 12, 30))
                val q = vm.byId.getValue(33)
                val pick = q.options.indexOfFirst { it.text == "tar -tf foo.tar.gz" }
                val state = QuizState(RoundKind.Smart, r.answer(false), vm.orderFor(q), selected = setOf(pick), answered = true, correct = false)
                vm.installShot(data, NOW, Screen.Quiz, quiz = state)
            }
            "home-fresh" -> vm.installShot(ProgressData(), NOW, Screen.Home)
            "home-open" -> {
                val r = Round(roundIds).answer(true).next().answer(true).next()
                val q = vm.byId.getValue(r.current!!)
                var e = Exam.start(vm.ids, NOW - 9 * 60 * MIN / 60, Random(3))
                repeat(12) { e = e.answer(vm.byId.getValue(e.current!!).correctIndices, NOW) }
                vm.installShot(
                    data, NOW, Screen.Home,
                    quiz = QuizState(RoundKind.Smart, r, vm.orderFor(q)),
                    exam = ExamState(e, vm.orderFor(vm.byId.getValue(e.current!!))),
                )
            }
            // "q:51", "q:5:sk", "wrong:16", "exam:41": any question unanswered, in a round or the mock exam (manual checks).
            else -> {
                val parts = name.split(":")
                val id = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in vm.byId }
                val seeded = data.copy(showSk = parts.getOrNull(2) == "sk")
                val q = id?.let { vm.byId.getValue(it) }
                when {
                    q != null && parts[0] == "q" -> {
                        val r = Round(listOf(id) + roundIds.filter { it != id }.take(9))
                        vm.installShot(seeded, NOW, Screen.Quiz, quiz = QuizState(RoundKind.Smart, r, vm.orderFor(q)))
                    }
                    // "wrong:16": answered with its first wrong option, to check the feedback layout.
                    q != null && parts[0] == "wrong" -> {
                        val r = Round(listOf(id) + roundIds.filter { it != id }.take(9))
                        val pick = q.options.indexOfFirst { !it.correct }
                        val state = QuizState(RoundKind.Smart, r.answer(false), vm.orderFor(q), selected = setOf(pick), answered = true, correct = false)
                        vm.installShot(answered(seeded, id, false), NOW, Screen.Quiz, quiz = state)
                    }
                    q != null && parts[0] == "exam" -> {
                        val ids = vm.ids.filter { it != id }.shuffled(Random(3))
                        val e = Exam(ids = ids.take(6) + id + ids.drop(6).take(43), index = 6, startedAt = NOW - 4 * 60_000L)
                        vm.installShot(seeded, NOW, Screen.ExamRun, exam = ExamState(e, vm.orderFor(q)))
                    }
                    else -> vm.installShot(data, NOW, Screen.Home)
                }
            }
        }
    }
}
