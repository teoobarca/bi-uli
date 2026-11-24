package dev.hamper.uli

import dev.hamper.uli.data.SavedSession
import dev.hamper.uli.engine.Exam
import dev.hamper.uli.engine.Kind
import dev.hamper.uli.engine.LastAnswer
import dev.hamper.uli.engine.Options
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Question
import dev.hamper.uli.engine.Srs
import dev.hamper.uli.engine.isCorrect
import dev.hamper.uli.engine.result

/**
 * The round and exam steps as plain functions of their state, so the guards (stale taps, double applies,
 * damaged saves) are tested without Android.
 */
object Flow {
    // Taps

    /**
     * How long after a question comes up its options ignore taps (ms, monotonic clock). The incoming card's
     * bottom option sits where "Ďalej" was, so a quick double tap on "Ďalej" would otherwise answer it.
     */
    const val SETTLE_MS = 350L

    /** [now] is too soon after [shownAt] for a deliberate tap. A [shownAt] of 0 means unknown: no wait. */
    private fun tooSoon(shownAt: Long, now: Long) = shownAt > 0 && now - shownAt in 0 until SETTLE_MS

    /**
     * A tap on option [index] of question [questionId] in a round at [now] (monotonic ms). Null when the tap
     * must be ignored: it came from a card that is no longer current (a ghost tap during the slide-out), the
     * question came up less than [SETTLE_MS] ago, the index is not an option of this question, or the question
     * is already answered.
     */
    fun pick(s: QuizState, q: Question, questionId: Int, index: Int, now: Long): QuizState? {
        if (s.answered || q.id != questionId || s.round.current != questionId || index !in q.options.indices) return null
        if (tooSoon(s.shownAt, now)) return null
        return s.copy(selected = toggle(q, s.selected, index))
    }

    /** The same for the mock exam. */
    fun examPick(s: ExamState, q: Question, questionId: Int, index: Int, now: Long): ExamState? {
        if (s.exam.current != questionId || q.id != questionId || index !in q.options.indices) return null
        if (tooSoon(s.shownAt, now)) return null
        return s.copy(selected = toggle(q, s.selected, index))
    }

    private fun toggle(q: Question, selected: Set<Int>, index: Int): Set<Int> = when {
        q.kind != Kind.Multi -> setOf(index)
        index in selected -> selected - index
        selected.size < q.choose -> selected + index
        else -> selected
    }

    // Applying answers

    /**
     * Answers the current round card. Returns the new round state and the progress to save. When the progress
     * already counts this answer (the app died after saving progress but before saving the round), the card
     * is not moved a second time.
     */
    fun submit(s: QuizState, q: Question, progress: ProgressData, now: Long): Pair<QuizState, ProgressData> {
        val ok = q.isCorrect(s.selected)
        val answered = s.copy(round = s.round.answer(ok), answered = true, correct = ok)
        if (progress.seq > s.seq) return answered to progress
        val card = progress.card(q.id)
        // Only the first attempt in a round moves the box; corrections just mark the card as seen.
        val updated = if (!s.round.isRetry) Srs.answer(card, ok, now) else card.copy(lastSeenAt = now)
        return answered to progress.copy(
            cards = progress.cards + (q.id to updated),
            seq = progress.seq + 1,
            last = LastAnswer(q.id, s.selected, ok),
        )
    }

    /** A finished mock exam moves every card it asked, exactly once (keyed by its start time). */
    fun applyExam(progress: ProgressData, e: Exam, byId: Map<Int, Question>, now: Long): ProgressData {
        if (progress.examApplied == e.startedAt) return progress
        val res = e.result(byId)
        return Srs.applyExam(progress, e.ids, res.wrong, now).copy(examApplied = e.startedAt)
    }

    // Restoring after a killed process

    data class Restored(
        val quiz: QuizState?,
        val roundStart: Map<Int, Int>,
        val exam: ExamState?,
        val lastExam: Exam?,
        val progress: ProgressData,
        /** The progress had to catch up with a finished exam whose answers never reached it. */
        val progressChanged: Boolean,
    )

    /**
     * What of a saved session can be continued against the current question bank. A round or exam is dropped
     * when any id is unknown or a saved option order, pick or answer does not fit its question (the bank
     * changed, or the file is damaged). A finished exam that is saved but not yet counted in [progress] is
     * counted now ([catchUp]; off when the progress itself could not be read).
     *
     * A round whose current question is already counted in [progress] (the app died after saving progress but
     * before saving the round) comes back answered the way it was counted. [shownAt] (monotonic ms) is when
     * the restored question comes up again.
     */
    fun restore(
        saved: SavedSession,
        progress: ProgressData,
        byId: Map<Int, Question>,
        catchUp: Boolean = true,
        shownAt: Long = 0,
    ): Restored {
        fun fits(q: Question?, order: List<Int>, selected: Set<Int>) =
            q != null && Options.isPermutation(order, q) && selected.all { it in q.options.indices }

        fun answersFit(e: Exam) = e.ids.isNotEmpty() && e.ids.all { it in byId } && e.ids.toSet().size == e.ids.size &&
            e.answers.all { (id, picks) -> id in e.ids && picks.all { it in byId.getValue(id).options.indices } }

        val quiz = saved.quiz?.takeIf { s ->
            val r = s.round
            r.queue.isNotEmpty() && r.ids.all { it in byId } && r.queue.all { it in byId } && !r.isFinished &&
                fits(r.current?.let(byId::get), s.order, s.selected)
        }?.let { s -> caughtUp(s, progress, byId).copy(shownAt = shownAt) }
        val exam = saved.exam?.takeIf { s ->
            !s.exam.isFinished && answersFit(s.exam) && s.exam.startedAt != progress.examApplied &&
                fits(s.exam.current?.let(byId::get), s.order, s.selected)
        }?.copy(shownAt = shownAt)
        val last = saved.lastExam?.takeIf { it.isFinished && answersFit(it) }
        var p = progress
        var changed = false
        if (last != null && catchUp && progress.examApplied != last.startedAt) {
            p = applyExam(progress, last, byId, last.finishedAt ?: last.startedAt)
            changed = true
        } else if (last != null && !catchUp) {
            // Progress starting over after a damaged file: this exam must not be replayed into it later.
            p = progress.copy(examApplied = last.startedAt)
        }
        return Restored(
            quiz = quiz,
            roundStart = if (quiz != null) saved.roundStartBoxes else emptyMap(),
            exam = exam,
            lastExam = last,
            progress = p,
            progressChanged = changed,
        )
    }

    /**
     * An unanswered round question whose answer progress already counts: shown answered with the counted
     * pick and outcome, so the screen agrees with what was recorded. When the counted answer belongs to some
     * other question (or an old save did not record it), the round just continues from the current count.
     */
    private fun caughtUp(s: QuizState, progress: ProgressData, byId: Map<Int, Question>): QuizState {
        if (s.answered || progress.seq <= s.seq) return s
        val id = s.round.current ?: return s
        val last = progress.last
        val q = byId.getValue(id)
        if (last == null || last.questionId != id || last.picked.any { it !in q.options.indices }) return s.copy(seq = progress.seq)
        return s.copy(round = s.round.answer(last.correct), selected = last.picked, answered = true, correct = last.correct)
    }

    /**
     * progress.json became readable again while the app ran on empty progress (the user tapped "Skúsiť znova"):
     * the open round and exam continue against [loaded]. The round takes the loaded count (else its next
     * answer would look already counted and be dropped) and its start boxes from the loaded cards; a finished
     * exam the loaded progress has not counted yet is counted, like on a start.
     */
    fun rebase(saved: SavedSession, loaded: ProgressData, byId: Map<Int, Question>): Restored {
        val r = restore(saved.copy(quiz = saved.quiz?.copy(seq = loaded.seq)), loaded, byId, catchUp = true)
        return r.copy(
            // Nothing new comes up: the questions keep the time they were shown.
            quiz = r.quiz?.copy(shownAt = saved.quiz?.shownAt ?: 0),
            exam = r.exam?.copy(shownAt = saved.exam?.shownAt ?: 0),
            roundStart = r.quiz?.round?.ids?.associateWith { loaded.card(it).box } ?: emptyMap(),
        )
    }
}
