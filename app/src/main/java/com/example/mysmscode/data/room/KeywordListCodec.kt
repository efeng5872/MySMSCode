package com.example.mysmscode.data

object KeywordListCodec {
    const val SEPARATOR: String = "\u001F"

    fun encode(keywords: List<String>): String = keywords.joinToString(separator = SEPARATOR)

    fun decode(keywordBlob: String): List<String> {
        if (keywordBlob.isBlank()) {
            return emptyList()
        }
        return keywordBlob.split(SEPARATOR)
    }
}