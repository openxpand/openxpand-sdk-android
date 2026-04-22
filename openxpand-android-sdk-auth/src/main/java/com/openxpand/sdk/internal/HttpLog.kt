package com.openxpand.sdk.internal

import android.util.Log

/**
 * OkHttp messages can exceed Android's single-call log limit; split so nothing is dropped.
 */
internal object HttpLog {

    const val TAG = "OpenXpandHttp"

    private const val MAX_CHUNK = 3500

    fun println(message: String) {
        for (line in message.lineSequence()) {
            if (line.length <= MAX_CHUNK) {
                Log.i(TAG, line)
            } else {
                var i = 0
                while (i < line.length) {
                    val end = minOf(i + MAX_CHUNK, line.length)
                    Log.i(TAG, line.substring(i, end))
                    i = end
                }
            }
        }
    }
}
