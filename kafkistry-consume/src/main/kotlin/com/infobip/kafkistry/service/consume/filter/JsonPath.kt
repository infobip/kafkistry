package com.infobip.kafkistry.service.consume.filter

import com.infobip.kafkistry.service.consume.JsonPathDef
import org.springframework.stereotype.Component

sealed class KeyPathElement

/**
 * example values:
 *  - `myKey`              -> exact key
 *  - `*`                  -> any key
 *  - `com\.org\.MyClass`  -> exact key with dots escaped
 */
data class MapKeyPathElement(
        val keyName: String?
) : KeyPathElement() {
    companion object {
        val ALL = MapKeyPathElement(null)
    }
}

/**
 * example values:
 *  - `[3]` -> exact array reference with index 3
 *  - `[*]` -> any index array reference
 */
data class ListIndexPathElement(
        val index: Int?
) : KeyPathElement() {
    companion object {
        val ALL = ListIndexPathElement(null)
    }
}

@Component
class JsonPathParser {

    private val nonEscapedDot = Regex("""(?<!\\)\.""")
    private val nonEscapedListPattern = Regex("""(?<!\\|^)\[(\d+|\*)]""")
    private val escapedListPattern = Regex("""\\(\[(\d+|\*)])""")
    private val indexedListPattern = Regex("\\[\\d+]")

    private operator fun Regex.contains(text: CharSequence): Boolean = matches(text)

    /**
     * Parse examples:
     * foo           => Key(foo)
     * foo.bar       => Key(foo),Key(bar)
     * foo[*]       => Key(foo),List(null)
     * foo[3]       => Key(foo),List(3)
     * [*]foo       => List(null),Key(foo)
     * org\.foo\.Bar => Key(org.foo.Bar)
     */
    fun parseJsonKeyPath(path: JsonPathDef): List<KeyPathElement> {
        if (path.isEmpty()) {
            return emptyList()
        }
        return path
                .replace(nonEscapedListPattern, ".$0")
                .split(nonEscapedDot)
                .map { it.replace("\\.", ".") }
                .map {
                    when (it) {
                        "[*]" -> ListIndexPathElement.ALL
                        "*" -> MapKeyPathElement.ALL
                        in indexedListPattern -> it.substring(1 until (it.length - 1))
                                .toInt()
                                .let { index -> ListIndexPathElement(index) }
                        else -> MapKeyPathElement(it.replace(escapedListPattern, "$1"))
                    }
                }
    }

}