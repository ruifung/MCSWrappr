package me.yrf.mcswrappr

fun <T> discardExceptions(block: () -> T): T? {
    try {
        return block()
    } catch (ex: Exception) {
        //DISCARD!
        return null
    }
}