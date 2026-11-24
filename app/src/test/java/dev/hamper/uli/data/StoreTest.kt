package dev.hamper.uli.data

import dev.hamper.uli.engine.CardState
import dev.hamper.uli.engine.ProgressData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun onlyAMissingFileMeansEmpty() {
        val store = FileProgressStore(tmp.root) { 42L }
        assertEquals(Loaded.Missing, store.load())
        val data = ProgressData(cards = mapOf(3 to CardState(box = 2)), seq = 7)
        store.save(data)
        assertEquals(Loaded.Ok(data), store.load())
    }

    @Test
    fun aDamagedFileIsBackedUpAndReportedNeverTreatedAsEmpty() {
        val file = File(tmp.root, "progress.json")
        file.writeText("{\"cards\": {\"3\": {\"box\": 2")
        val loaded = FileProgressStore(tmp.root) { 1234L }.load()
        assertTrue(loaded is Loaded.Corrupt)
        val backup = (loaded as Loaded.Corrupt).backup
        assertNotNull(backup)
        assertEquals("progress.corrupt-1234.json", backup)
        assertEquals(file.readText(), File(tmp.root, backup!!).readText())
        // Read again on the next start: the same copy, not a second one.
        val again = FileProgressStore(tmp.root) { 9999L }.load() as Loaded.Corrupt
        assertEquals(backup, again.backup)
        // The original is untouched.
        assertEquals("{\"cards\": {\"3\": {\"box\": 2", file.readText())
    }

    @Test
    fun aFailedWriteIsReportedNotSwallowed() {
        val notADir = tmp.newFile("blocked")
        try {
            FileProgressStore(notADir).save(ProgressData())
            fail("expected the write to throw")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun aDamagedSessionIsDroppedAndAnEmptyOneDeletesTheFile() {
        File(tmp.root, "session.json").writeText("not json")
        val store = FileSessionStore(tmp.root) { 5L }
        assertTrue(store.load() is Loaded.Corrupt)
        store.save(SavedSession())
        assertEquals(Loaded.Missing, store.load())
    }
}
