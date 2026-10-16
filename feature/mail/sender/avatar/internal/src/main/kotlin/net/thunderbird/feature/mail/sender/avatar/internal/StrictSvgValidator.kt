package net.thunderbird.feature.mail.sender.avatar.internal

import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal class StrictSvgValidator {
    fun isValid(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > SafeBimiTransport.MAX_SVG_BYTES) return false
        val text = bytes.toString(Charsets.UTF_8)
        if (FORBIDDEN_TEXT.containsMatchIn(text)) return false

        return runCatching {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
            val handler = SvgHandler()
            factory.newSAXParser().xmlReader.apply {
                entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(ByteArrayInputStream(ByteArray(0))) }
                contentHandler = handler
                errorHandler = handler
                parse(InputSource(ByteArrayInputStream(bytes)))
            }
            handler.valid && handler.finished
        }.getOrDefault(false)
    }

    private class SvgHandler : DefaultHandler() {
        var valid = true
        var finished = false
        private var depth = 0
        private var elements = 0
        private var rootSeen = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            depth++
            elements++
            val name = (localName?.takeIf(String::isNotEmpty) ?: qName.orEmpty()).lowercase()
            if (depth > 64 || elements > 5000 || name in FORBIDDEN_ELEMENTS || uri != SVG_NAMESPACE) valid = false
            if (!rootSeen) {
                rootSeen = true
                valid = valid && name == "svg" && validSquareViewBox(attributes.getValue("viewBox"))
            }
            for (index in 0 until attributes.length) {
                val attributeName = (attributes.getLocalName(index).ifEmpty { attributes.getQName(index) }).lowercase()
                val value = attributes.getValue(index).trim()
                if (attributeName.startsWith("on") ||
                    attributeName == "href" || attributeName.endsWith(":href") ||
                    value.contains("url(", ignoreCase = true) ||
                    value.contains("@import", ignoreCase = true)
                ) {
                    valid = false
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            depth--
            if (depth == 0) finished = true
        }

        override fun processingInstruction(target: String?, data: String?) {
            valid = false
        }

        private fun validSquareViewBox(value: String?): Boolean {
            val parts = value?.trim()?.split(Regex("[ ,]+"))?.mapNotNull(String::toDoubleOrNull) ?: return false
            return parts.size == 4 && parts[2] > 0.0 && parts[2] == parts[3]
        }
    }

    companion object {
        private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
        private val FORBIDDEN_ELEMENTS = setOf(
            "script", "foreignobject", "image", "use", "a", "style", "animate", "animatemotion", "animatetransform",
            "set", "audio", "video", "iframe", "canvas",
        )
        private val FORBIDDEN_TEXT = Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE)
    }
}
