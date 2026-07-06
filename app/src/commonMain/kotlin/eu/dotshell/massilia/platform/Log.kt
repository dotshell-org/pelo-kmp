package eu.dotshell.massilia.platform

/**
 * Multiplatform logging abstraction.
 */
expect object Log {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable?)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable?)
}
