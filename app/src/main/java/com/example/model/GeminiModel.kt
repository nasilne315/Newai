package com.example.model

enum class ModelCategory {
    GENERAL,
    THINKING,
    FAST,
    IMAGE_CREATION,
    HIGH_QUALITY_IMAGE
}

data class GeminiModel(
    val id: String,
    val name: String,
    val tag: String,
    val category: ModelCategory,
    val description: String,
    val supportsThinking: Boolean = false,
    val supportsSearch: Boolean = false,
    val isImageGenerator: Boolean = false,
    val allowedResolutions: List<String> = emptyList()
) {
    companion object {
        val GEMINI_FLASH_LITE_LATEST = GeminiModel(
            id = "gemini-2.0-flash-lite",
            name = "Gemini Flash Lite (Latest)",
            tag = "Ultra Fast",
            category = ModelCategory.FAST,
            description = "En güncel ve ultra hızlı Flash Lite sürümü"
        )
        val GEMINI_31_FLASH_LITE = GEMINI_FLASH_LITE_LATEST
        val GEMINI_35_FLASH = GeminiModel(
            id = "gemini-2.0-flash",
            name = "Gemini 2.0 Flash",
            tag = "Streaming Flash",
            category = ModelCategory.GENERAL,
            description = "High speed, reliable general text intelligence",
            supportsSearch = true
        )
        val GEMINI_38_FLASH = GEMINI_35_FLASH
        val GEMINI_4_ARGON = GeminiModel(
            id = "gemini-2.5-pro",
            name = "Gemini Pro Reasoning",
            tag = "High Thinking",
            category = ModelCategory.THINKING,
            description = "Complex reasoning and math with thinking",
            supportsThinking = true
        )
        val ALL_MODELS = listOf(
            GEMINI_FLASH_LITE_LATEST,
            GEMINI_38_FLASH,
            GEMINI_4_ARGON
        )
    }
}
