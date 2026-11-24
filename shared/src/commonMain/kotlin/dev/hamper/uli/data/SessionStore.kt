package dev.hamper.uli.data

import dev.hamper.uli.ExamState
import dev.hamper.uli.QuizState
import dev.hamper.uli.engine.Exam
import kotlinx.serialization.Serializable

/**
 * What is open right now: a learning round (with the boxes its cards had when it started, for the
 * "newly mastered" count) and/or a mock exam, plus the last finished exam so its review survives a killed
 * process until the next exam starts. Saved on every step so a killed process loses nothing.
 */
@Serializable
data class SavedSession(
    val version: Int = 1,
    val quiz: QuizState? = null,
    val roundStartBoxes: Map<Int, Int> = emptyMap(),
    val exam: ExamState? = null,
    val lastExam: Exam? = null,
) {
    val isEmpty: Boolean get() = quiz == null && exam == null && lastExam == null
}

interface SessionStore {
    fun load(): Loaded<SavedSession>

    /** Throws when the write fails. */
    fun save(data: SavedSession)
}

class MemorySessionStore(private var data: SavedSession = SavedSession()) : SessionStore {
    override fun load(): Loaded<SavedSession> = Loaded.Ok(data)
    override fun save(data: SavedSession) {
        this.data = data
    }
}
