package com.yaadein.data.polish

import com.yaadein.domain.polish.PolishResult

/**
 * Optional on-device "Smart polish" (Gemini Nano via AICore / ML Kit GenAI) that can rewrite the
 * rule-based result with a warmer description. Never required: when unavailable the rule-based
 * engine is used, and the app never touches the network either way.
 */
interface SmartPolishProvider {
    suspend fun isAvailable(): Boolean
    /** Returns an improved result, or null to keep [base]. */
    suspend fun polish(input: String, base: PolishResult): PolishResult?
}

/** Used on devices without an on-device model. */
object NoSmartPolish : SmartPolishProvider {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun polish(input: String, base: PolishResult): PolishResult? = null
}
