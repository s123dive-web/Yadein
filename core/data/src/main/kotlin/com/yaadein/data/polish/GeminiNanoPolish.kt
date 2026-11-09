package com.yaadein.data.polish

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.yaadein.domain.polish.PolishResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Smart polish with Gemini Nano through Android AICore (ML Kit GenAI Prompt API), on phones that have the model.
 * The text goes to the system's on-device model only; Yaadein has no internet permission. Only used when the model is
 * already on the phone ([FeatureStatus.AVAILABLE]): the app never asks AICore to download it. Any problem keeps the
 * rule-based result.
 */
class GeminiNanoPolish : SmartPolishProvider {
    private val model: GenerativeModel? by lazy { runCatching { Generation.getClient() }.getOrNull() }

    override suspend fun isAvailable(): Boolean =
        runCatching { model?.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    override suspend fun polish(input: String, base: PolishResult): PolishResult? {
        val m = model ?: return null
        val prompt = """
            You help someone keep a family memory book. Rewrite their note as a short title and a warm two-sentence
            description in the same language they used (Hindi, Marathi, English or Hinglish). Keep every name, place and
            fact; add nothing that is not in the note. Answer in exactly two lines:
            TITLE: <at most 6 words>
            STORY: <one or two sentences>
            Note: $input
        """.trimIndent()
        val text = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { m.generateContent(prompt).candidates.firstOrNull()?.text }.getOrNull()
        } ?: return null
        return parse(text, base)
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L

        /** Pulls TITLE/STORY out of the model's answer; null if it did not follow the format. */
        fun parse(text: String, base: PolishResult): PolishResult? {
            val title = Regex("(?im)^\\s*TITLE:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim()?.trim('"')
            val story = Regex("(?ims)^\\s*STORY:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()?.trim('"')
            if (title.isNullOrBlank() || story.isNullOrBlank()) return null
            return base.copy(title = title.take(80), description = story.take(600))
        }
    }
}
