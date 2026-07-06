package eu.dotshell.massilia.generic.utils.network

import kotlinx.coroutines.delay
import kotlinx.io.IOException

/**
 * Retry a suspend function with exponential backoff
 *
 * @param maxRetries Maximum number of retry attempts
 * @param initialDelayMs Initial delay in milliseconds before first retry
 * @param maxDelayMs Maximum delay in milliseconds between retries
 * @param factor Multiplier for exponential backoff
 * @param block The suspend function to retry
 * @return Result of the suspend function
 * @throws Exception if all retries fail
 */
suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 5000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e

            // Only retry on transient network errors
            val shouldRetry = when (e) {
                is IOException -> true
                else -> false
            }

            if (!shouldRetry || attempt == maxRetries - 1) {
                throw e
            }

            // Exponential backoff with jitter
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }

    // This should never be reached, but throw the last exception if it happens
    throw lastException ?: Exception("Retry failed with unknown error")
}
