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
        val GEMINI_31_FLASH_LITE = GeminiModel(
            id = "gemini-2.5-flash-lite",
            name = "Gemini Flash Lite",
            tag = "Ultra Fast",
            category = ModelCategory.FAST,
            description = "Lightweight and instant response generation"
        )
        val ALL_MODELS = listOf(
            GEMINI_38_FLASH,
            GEMINI_4_ARGON,
            GEMINI_31_FLASH_LITE
        )
    }
}
