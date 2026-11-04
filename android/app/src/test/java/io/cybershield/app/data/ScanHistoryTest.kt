package io.cybershield.app.data

import io.cybershield.app.security.QrGuard
import io.cybershield.app.security.RiskAnalyzer
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanHistoryTest {
    private val store = InMemoryHistoryStore()
    private var now = 1_000L
    private val repo = ScanHistoryRepository(store) { now++ }

    @Test
    fun savesAndRetrievesScans() {
        val message = RiskAnalyzer.message("Your account will be blocked. Share OTP immediately")
        repo.add(message, ScanKind.MESSAGE, target = null)
        repo.addUrl(RiskAnalyzer.url("https://www.google.com"), "https://www.google.com/search?q=x")
        val all = repo.all()
        assertEquals(2, all.size)
        assertEquals(ScanKind.URL, all[0].kind) // newest first
        assertEquals("www.google.com", all[0].target)
        assertEquals(ScanKind.MESSAGE, all[1].kind)
        assertEquals(message.score, all[1].score)
        assertTrue(all[1].codes.isNotEmpty())
        assertEquals(1000L, all[1].checkedAt)
    }

    @Test
    fun clearsHistory() {
        repo.add(RiskAnalyzer.message("hello"), ScanKind.MESSAGE, null)
        repo.clear()
        assertTrue(repo.all().isEmpty())
        assertEquals(0, repo.stats().total)
    }

    @Test
    fun deletesSingleRecord() {
        val a = repo.add(RiskAnalyzer.message("a"), ScanKind.MESSAGE, null)
        repo.add(RiskAnalyzer.message("b"), ScanKind.MESSAGE, null)
        repo.delete(a.id)
        assertEquals(1, repo.all().size)
        assertFalse(repo.all().any { it.id == a.id })
    }

    @Test
    fun doesNotStoreSensitiveContent() {
        val secretText = "Your OTP is 482913. Send UPI PIN 7788 to rahul@okaxis, card 4111111111111111, call 9876543210"
        repo.add(RiskAnalyzer.message(secretText), ScanKind.MESSAGE, target = null)
        val tokenUrl = "https://evil.example/login?session=SECRET-TOKEN-123&phone=9876543210#frag"
        repo.addUrl(RiskAnalyzer.url(tokenUrl), tokenUrl)
        repo.addQr(QrGuard.analyze("upi://pay?pa=rahul.sharma@okaxis&pn=Rahul%20Sharma&am=5000"))
        // A careless caller passing raw content as target must still not leak it.
        repo.add(RiskAnalyzer.message("x"), ScanKind.MESSAGE, target = "https://a.example/p?otp=482913")
        repo.add(RiskAnalyzer.message("x"), ScanKind.MESSAGE, target = "call 9876543210")

        val raw = store.read()!!
        listOf(
            "482913", "7788", "rahul", "4111111111111111", "9876543210", "SECRET-TOKEN", "session", "login",
            "Rahul Sharma", "5000", "frag", "OTP is",
        ).forEach { assertFalse("history leaked '$it': $raw", raw.contains(it)) }
        assertTrue(raw.contains("evil.example"))
        assertTrue(raw.contains("@okaxis"))
    }

    @Test
    fun capsHistorySize() {
        repeat(ScanHistoryRepository.MAX_ITEMS + 15) { repo.add(RiskAnalyzer.message("hi"), ScanKind.MESSAGE, null) }
        assertEquals(ScanHistoryRepository.MAX_ITEMS, repo.all().size)
    }

    @Test
    fun searchesFiltersAndStats() {
        repo.addUrl(RiskAnalyzer.url("http://sbi-kyc.xyz/login"), "http://sbi-kyc.xyz/login")
        repo.addUrl(RiskAnalyzer.url("https://www.google.com"), "https://www.google.com")
        repo.add(RiskAnalyzer.message("Share OTP now, account will be blocked"), ScanKind.MESSAGE, null)
        assertEquals(1, repo.search(query = "sbi").size)
        assertEquals(2, repo.search(kind = ScanKind.URL).size)
        assertEquals(2, repo.search(minLevel = RiskLevel.HIGH).size)
        val stats = repo.stats()
        assertEquals(3, stats.total)
        assertEquals(2, stats.highRisk)
        assertEquals(2, stats.suspicious)
    }

    @Test
    fun readsLegacyV02Records() {
        val legacy = InMemoryHistoryStore("""[{"type":"Link","score":65,"verdict":"High risk signals","checkedAt":42}]""")
        val record = ScanHistoryRepository(legacy).all().single()
        assertEquals(ScanKind.URL, record.kind)
        assertEquals(RiskLevel.HIGH, record.level)
        assertEquals(42L, record.checkedAt)
    }

    @Test
    fun survivesCorruptStorage() {
        assertTrue(ScanHistoryRepository(InMemoryHistoryStore("{not json")).all().isEmpty())
    }

    @Test
    fun confirmedMaliciousIsAlwaysHighRisk() {
        val record = repo.addUrl(RiskAnalyzer.url("https://example.com"), "https://example.com", confirmedMalicious = true)
        assertEquals(RiskLevel.HIGH, record.level)
        assertEquals(1, repo.stats().confirmed)
    }
}
