package dev.hamper.uli.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One learning round. A wrong card comes back [gap] positions later and keeps coming back
 * until it is answered right; only the first attempt counts for the score and the boxes.
 */
@Serializable
data class Round(
    val ids: List<Int>,
    val queue: List<Int> = ids,
    val index: Int = 0,
    val firstAttempt: Map<Int, Boolean> = emptyMap(),
    val gap: Int = 3,
) {
    val current: Int? get() = queue.getOrNull(index)
    val isFinished: Boolean get() = index >= queue.size
    /** The current card was already shown earlier in this round (a correction pass). */
    val isRetry: Boolean get() = current?.let { it in queue.subList(0, index) } ?: false
    val score: Int get() = firstAttempt.values.count { it }
    val total: Int get() = ids.size
    val mistakes: List<Int> get() = ids.filter { firstAttempt[it] == false }
    /** Right first attempts in a row, counting back from the latest. */
    val streak: Int get() = firstAttempt.values.reversed().takeWhile { it }.size

    /**
     * Cards still waiting to be fixed: missed on the first try and not yet answered right. Counts cards, not
     * passes, so it drops by one with every fix and never grows while you work through corrections (a miss
     * on a correction keeps the same card waiting). [answered]: the current card has been answered.
     */
    fun toFix(answered: Boolean): Int {
        val from = (if (answered) index + 1 else index).coerceAtMost(queue.size)
        return queue.subList(from, queue.size).filter { firstAttempt[it] == false }.distinct().size
    }

    /** Position of the current card among the round's original cards, 1-based, for "4/10". */
    val displayNumber: Int get() = queue.take(index + 1).distinct().size.coerceAtMost(total)

    fun answer(correct: Boolean): Round {
        val id = current ?: return this
        val first = if (id in firstAttempt) firstAttempt else firstAttempt + (id to correct)
        val nextQueue = if (correct) {
            queue
        } else {
            queue.toMutableList().apply { add((index + gap).coerceAtMost(size), id) }
        }
        return copy(queue = nextQueue, firstAttempt = first)
    }

    fun next(): Round = copy(index = index + 1)
}

/** The mock exam: [size] random questions, one pass, no feedback until the end. */
@Serializable
data class Exam(
    val ids: List<Int>,
    val index: Int = 0,
    val answers: Map<Int, Set<Int>> = emptyMap(),
    val startedAt: Long,
    val finishedAt: Long? = null,
) {
    val current: Int? get() = ids.getOrNull(index)
    val isFinished: Boolean get() = index >= ids.size

    fun answer(selected: Set<Int>, now: Long): Exam {
        val id = current ?: return this
        val next = index + 1
        return copy(
            answers = answers + (id to selected),
            index = next,
            finishedAt = if (next >= ids.size) now else finishedAt,
        )
    }

    companion object {
        const val SIZE = 50
        const val POINTS_PER_QUESTION = 1.6
        const val MAX_POINTS = 80.0
        const val PASS_POINTS = 64.0

        fun start(pool: List<Int>, now: Long, random: Random, size: Int = SIZE): Exam =
            Exam(ids = pool.shuffled(random).take(size), startedAt = now)
    }
}

data class ExamResult(val correct: Int, val total: Int, val wrong: List<Int>) {
    val points: Double get() = correct * Exam.POINTS_PER_QUESTION
    val passed: Boolean get() = points >= Exam.PASS_POINTS - 1e-9
}

fun Exam.result(questions: Map<Int, Question>): ExamResult {
    val wrong = ids.filter { id -> !questions.getValue(id).isCorrect(answers[id].orEmpty()) }
    return ExamResult(correct = ids.size - wrong.size, total = ids.size, wrong = wrong)
}
