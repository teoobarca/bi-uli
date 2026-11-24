package dev.hamper.uli.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class Kind {
    @SerialName("single") Single,
    @SerialName("multi") Multi,
    @SerialName("truefalse") TrueFalse,
}

@Serializable
data class Option(
    val text: String,
    val correct: Boolean,
    val note: String? = null,
)

@Serializable
data class Question(
    val id: Int,
    val topic: String,
    val kind: Kind,
    val choose: Int,
    val question: String,
    val questionSk: String,
    val options: List<Option>,
    val explanation: String,
    val hook: String? = null,
    val demo: String? = null,
    val source: String,
) {
    val correctIndices: Set<Int> get() = options.indices.filter { options[it].correct }.toSet()

    /** Exam key and real-world Linux disagree; the explanation already covers both. */
    val disputed: Boolean get() = source != "ok"

    val topicEnum: Topic get() = Topic.of(topic)

    /** The Slovak line adds nothing when it is the English verbatim (e.g. "Microsoft Windows:"). */
    val hasDistinctSk: Boolean get() = questionSk.isNotBlank() && questionSk.trim() != question.trim()

    /** The real-world side of a disputed question, as a sentence ("disputed: v GNU tar ..." in the source). */
    val disputedNote: String?
        get() = if (!disputed) null else source.removePrefix("disputed:").trim()
            .replaceFirstChar { it.uppercase() }
            .let { if (it.endsWith(".")) it else "$it." }
}

/** Topics in course order, with the Slovak names the UI shows. */
enum class Topic(val key: String, val title: String) {
    Intro("intro", "Úvod do Linuxu"),
    OpenSource("opensource", "Open source a licencie"),
    Cli("cli", "Príkazový riadok"),
    Help("help", "Pomoc a manuály"),
    Files("files", "Súbory a adresáre"),
    Archives("archives", "Archívy a kompresia"),
    Pipes("pipes", "Presmerovanie a grep"),
    Scripts("scripts", "Skripty a editor"),
    Hardware("hardware", "Hardvér a systém"),
    Network("network", "Sieť"),
    Users("users", "Používatelia a skupiny"),
    Perms("perms", "Práva"),
    Links("links", "Odkazy"),
    ;

    companion object {
        fun of(key: String): Topic = entries.first { it.key == key }
    }
}

object Content {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<Question> = json.decodeFromString(text)
}

/** Exact-set scoring: a multi question counts only when every correct option and nothing else is picked. */
fun Question.isCorrect(selected: Set<Int>): Boolean = selected == correctIndices

/** Display order of a question's options. */
object Options {
    /** "None of the above", "All are correct": options that talk about the others, so they stay last. */
    private val aboutTheOthers = Regex(
        "(?i)^(none|all|neither|both)( of)?( the)? ?(above|of these|of them|these|are correct|are true|the above)?[.!]?$",
    )

    fun refersToOthers(text: String): Boolean {
        val t = text.trim()
        return t.split(' ').size <= 4 && aboutTheOthers.matches(t)
    }

    /** Shuffled, but options that refer to the others keep their place at the end (in their original order). */
    fun order(q: Question, random: kotlin.random.Random): List<Int> {
        val (pinned, free) = q.options.indices.partition { refersToOthers(q.options[it].text) }
        return free.shuffled(random) + pinned
    }

    /** A saved order is usable only if it is exactly a reordering of this question's options. */
    fun isPermutation(order: List<Int>, q: Question): Boolean = order.sorted() == q.options.indices.toList()
}
