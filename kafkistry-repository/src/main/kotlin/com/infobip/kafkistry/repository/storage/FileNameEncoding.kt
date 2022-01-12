package com.infobip.kafkistry.repository.storage

class FileNameEncoding {

    private val escapeChar = '#'
    private val illegalChars = """/\?%*:|"<> """
    private val charsToEscape = illegalChars + escapeChar
    private val decodeRegex = Regex("""#(\d+)#""")

    fun encode(name: String): String = name.toCharArray()
            .map {
                if (it in charsToEscape) {
                    "#${it.code}#"
                } else {
                    "$it"
                }
            }
            .joinToString("") { it }

    fun decode(encodedName: String): String = encodedName.replace(decodeRegex) {
        val char = it.groups[1]!!.value.toInt().toChar()
        "$char"
    }

}