package eu.dotshell.massilia.platform

import kotlin.random.Random

fun randomId(): String {
    val chars = "0123456789abcdef"
    return buildString(32) {
        repeat(32) { append(chars[Random.nextInt(chars.length)]) }
    }
}
