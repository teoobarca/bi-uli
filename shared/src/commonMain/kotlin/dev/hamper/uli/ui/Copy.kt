package dev.hamper.uli.ui

import dev.hamper.uli.RoundKind
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Srs
import kotlin.math.roundToInt

// Copy that depends on the data, kept free of Compose so it is unit tested.

/** "o 6 minút", "o hodinu", "o 4 hodiny", "zajtra", "o 3 dni", "o týždeň": a wait from now, rounded up to whole minutes. */
fun inTime(ms: Long): String {
    val min = ((ms + 59_999) / 60_000).coerceAtLeast(1)
    if (min < 60) return if (min == 1L) "o minútu" else "o $min " + plural(min, "minútu", "minúty", "minút")
    val h = (ms + 30 * 60_000) / 3_600_000
    if (h < 24) return if (h == 1L) "o hodinu" else "o $h " + plural(h, "hodinu", "hodiny", "hodín")
    val d = (ms + 12 * 3_600_000) / 86_400_000
    return when (d) {
        1L -> "zajtra"
        7L -> "o týždeň"
        else -> "o $d " + plural(d, "deň", "dni", "dní")
    }
}

/** "Znova o 4 hodiny", "Znova zajtra": when a card answered right comes due again. */
fun comesBackIn(ms: Long): String? = if (ms <= 0) null else "Znova " + inTime(ms)

/** "Vráti sa o 3 otázky": when a missed card comes back in this round. */
fun comesBackAfter(questions: Int): String = when {
    questions <= 1 -> "Vráti sa hneď ako ďalšia"
    else -> "Vráti sa o $questions " + plural(questions.toLong(), "otázku", "otázky", "otázok")
}

fun plural(n: Long, one: String, few: String, many: String) = when (n) {
    1L -> one
    in 2L..4L -> few
    else -> many
}

/**
 * The line under the round score: when the cards you missed actually come back (the earliest one) and what
 * the next step after that is, from their real due times and boxes, never a fixed promise.
 */
fun roundEndNote(kind: RoundKind, ids: List<Int>, mistakes: List<Int>, progress: ProgressData, now: Long): String {
    fun whenBack(cards: List<Int>, many: Boolean): String {
        val first = cards.minBy { progress.card(it).dueAt }
        val card = progress.card(first)
        val wait = card.dueAt - now
        val subject = if (many) "Prvá" else "Otázka"
        val start = if (wait <= 0) "$subject je na rade hneď" else "$subject sa vráti ${inTime(wait)}"
        val step = Srs.interval((card.box + 1).coerceAtMost(Srs.MAX_BOX))
        return "$start, po správnej odpovedi ${inTime(step)}."
    }
    if (mistakes.isNotEmpty()) {
        val lead = if (kind == RoundKind.Mistakes) "Opravené." else "Chyby si hneď opravil."
        return "$lead ${whenBack(mistakes, mistakes.size > 1)}"
    }
    if (kind != RoundKind.Mistakes) return "Všetko na prvý pokus."
    val left = ids.filter { Srs.isMistake(progress.card(it)) }
    if (left.isEmpty()) return "Všetko na prvý pokus a z Chýb zmizli."
    return "Všetko na prvý pokus. Z Chýb zmiznú, keď ich trafíš aj neskôr. ${whenBack(left, left.size > 1)}"
}

/** "~57 %", with the ends said in words: a bare "pod 1 %" read as a verdict, not a forecast. */
fun chanceText(p: Double): String {
    val pct = (p * 100).roundToInt()
    return when {
        p < 0.05 -> "zatiaľ nízka"
        p > 0.99 -> "nad 99 %"
        else -> "~$pct %"
    }
}

/** "Dobré ráno", "Dobrý deň", "Dobrý večer" by the local hour. */
fun greeting(hour: Int): String = when (hour) {
    in 4..9 -> "Dobré ráno"
    in 10..17 -> "Dobrý deň"
    else -> "Dobrý večer"
}

/** The local clock at a moment: its hour and when its day began (epoch ms). */
data class LocalDay(val hour: Int, val midnight: Long)

/** [LocalDay] of [now] (epoch ms) in the device's time zone. */
expect fun localDay(now: Long): LocalDay

/** Distinct questions answered since local midnight (each card remembers when it was last answered). */
fun answeredToday(progress: ProgressData, now: Long, day: LocalDay = localDay(now)): Int {
    return progress.cards.values.count { it.lastSeenAt in day.midnight..now }
}

/** The header's today line: "Dobrý večer · dnes 12 otázok", or an invitation when today is still empty. */
fun todayLine(progress: ProgressData, now: Long, day: LocalDay = localDay(now)): String {
    val n = answeredToday(progress, now, day)
    val today = when {
        n == 0 -> "dnes ešte nič"
        else -> "dnes $n " + plural(n.toLong(), "otázka", "otázky", "otázok")
    }
    return "${greeting(day.hour)} · $today"
}
