package io.cybershield.app

import io.cybershield.app.security.FindingCode
import io.cybershield.app.security.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the translation files structurally. This verifies completeness and placeholder safety;
 * it cannot verify that a translation is idiomatic, which needs native-speaker review.
 */
class LocalizationTest {
    private fun load(qualifier: String): Map<String, String> {
        val file = File("src/main/res/values$qualifier/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private val placeholder = Regex("""%\d+\$[sd]""")
    private val english = load("")

    @Test
    fun hindiAndMarathiAreComplete() {
        for (q in listOf("-hi", "-mr")) {
            val translated = load(q)
            assertEquals("missing in values$q", emptySet<String>(), english.keys - translated.keys)
            assertEquals("unknown keys in values$q", emptySet<String>(), translated.keys - english.keys)
            for ((key, value) in english) {
                assertEquals("placeholders differ for $key in values$q",
                    placeholder.findAll(value).map { it.value }.toSet(), placeholder.findAll(translated.getValue(key)).map { it.value }.toSet())
                assertTrue("empty $key in values$q", translated.getValue(key).isNotBlank())
            }
        }
    }

    @Test
    fun translationsAreActuallyInDevanagari() {
        for (q in listOf("-hi", "-mr")) {
            val translated = load(q)
            val devanagari = translated.values.count { v -> v.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.DEVANAGARI } }
            // Product names (ScamLens, URLs, "SMS") stay in Latin script; nearly everything else must be translated.
            assertTrue("values$q looks untranslated: $devanagari of ${translated.size}", devanagari * 100 >= translated.size * 85)
        }
    }

    @Test
    fun everyEngineCodeHasAString() {
        FindingCode.entries.forEach { assertTrue(it.name, "f_" + it.name.lowercase() in english) }
        Recommendation.entries.forEach { assertTrue(it.name, "rec_" + it.name.lowercase() in english) }
    }
}
