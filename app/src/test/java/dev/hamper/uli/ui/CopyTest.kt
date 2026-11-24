package dev.hamper.uli.ui

import dev.hamper.uli.RoundKind
import dev.hamper.uli.engine.CardState
import dev.hamper.uli.engine.ProgressData
import dev.hamper.uli.engine.Srs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class CopyTest {
    private val min = 60_000L
    private val hour = 60 * min
    private val now = 1_800_000_000_000L

    @Test
    fun waitsAreSaidInWords() {
        assertEquals("o 6 minút", inTime(6 * min))
        assertEquals("o minútu", inTime(30_000))
        assertEquals("o 3 minúty", inTime(3 * min))
        assertEquals("o hodinu", inTime(hour))
        assertEquals("o 4 hodiny", inTime(4 * hour))
        assertEquals("zajtra", inTime(24 * hour))
        assertEquals("o 3 dni", inTime(72 * hour))
        assertEquals("o týždeň", inTime(7 * 24 * hour))
    }

    @Test
    fun theRoundEndSaysWhenTheFirstMissReallyComesBack() {
        // Missed four minutes ago: back in six, then the next step is box 2's four hours.
        val missed = Srs.answer(CardState(box = 2, timesRight = 2), false, now - 4 * min)
        val later = Srs.answer(CardState(), false, now - 1 * min)
        val p = ProgressData(cards = mapOf(1 to missed, 2 to later))
        assertEquals(
            "Chyby si hneď opravil. Prvá sa vráti o 6 minút, po správnej odpovedi o 4 hodiny.",
            roundEndNote(RoundKind.Smart, listOf(1, 2, 3), listOf(1, 2), p, now),
        )
        assertEquals(
            "Opravené. Otázka sa vráti o 9 minút, po správnej odpovedi o 4 hodiny.",
            roundEndNote(RoundKind.Mistakes, listOf(2), listOf(2), p, now),
        )
        assertEquals("Všetko na prvý pokus.", roundEndNote(RoundKind.Smart, listOf(1), emptyList(), p, now))
    }

    @Test
    fun aCleanChybyRoundSaysWhatIsLeft() {
        val stillMistake = CardState(box = 2, timesWrong = 1, timesRight = 2, dueAt = now + 3 * hour, lastSeenAt = now)
        val gone = CardState(box = 3, timesWrong = 1, timesRight = 3, dueAt = now + 24 * hour, lastSeenAt = now)
        val p = ProgressData(cards = mapOf(1 to stillMistake, 2 to gone))
        assertEquals(
            "Všetko na prvý pokus. Z Chýb zmiznú, keď ich trafíš aj neskôr. Otázka sa vráti o 3 hodiny, po správnej odpovedi zajtra.",
            roundEndNote(RoundKind.Mistakes, listOf(1, 2), emptyList(), p, now),
        )
        assertEquals("Všetko na prvý pokus a z Chýb zmizli.", roundEndNote(RoundKind.Mistakes, listOf(2), emptyList(), p, now))
    }

    @Test
    fun chanceEndsAreWords() {
        assertEquals("zatiaľ nízka", chanceText(0.001))
        assertEquals("zatiaľ nízka", chanceText(0.04))
        assertEquals("~57 %", chanceText(0.567))
        assertEquals("nad 99 %", chanceText(0.999))
    }

    @Test
    fun todayLineCountsQuestionsSinceMidnight() {
        val zone = ZoneId.of("Europe/Bratislava")
        // 2027-01-15 19:00 local.
        val evening = java.time.ZonedDateTime.of(2027, 1, 15, 19, 0, 0, 0, zone).toInstant().toEpochMilli()
        val p = ProgressData(
            cards = mapOf(
                1 to CardState(box = 1, lastSeenAt = evening - 2 * hour),
                2 to CardState(box = 1, lastSeenAt = evening - 20 * hour), // yesterday, 23:00
                3 to CardState(box = 2, lastSeenAt = evening - 10 * min),
            ),
        )
        assertEquals(2, answeredToday(p, evening, localDay(evening, zone)))
        assertEquals("Dobrý večer · dnes 2 otázky", todayLine(p, evening, localDay(evening, zone)))
        assertEquals("Dobré ráno · dnes ešte nič", todayLine(p, evening + 12 * hour, localDay(evening + 12 * hour, zone)))
    }
}
