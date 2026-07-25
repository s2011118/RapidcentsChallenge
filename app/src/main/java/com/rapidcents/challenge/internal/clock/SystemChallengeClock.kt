package com.rapidcents.challenge.internal.clock


import com.rapidcents.challenge.contract.ChallengeClock
import kotlinx.coroutines.delay

/**
 * Production clock implementation.
 *
 * Uses the real system time and coroutine delay.
 */
class SystemChallengeClock : ChallengeClock {

    override fun nowEpochMs(): Long {
        return System.currentTimeMillis()
    }

    override suspend fun delay(ms: Long) {
        require(ms >= 0) {
            "Delay must not be negative."
        }

        delay(ms)
    }
}