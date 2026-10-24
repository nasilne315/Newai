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
            description = "En güncel ve ultra hızlı Flash Lite sürümü",
            supportsSearch = true
        )
        val GEMINI_31_FLASH_LITE = GEMINI_FLASH_LITE_LATEST
        val GEMINI_35_FLASH = GEMINI_FLASH_LITE_LATEST
        val GEMINI_38_FLASH = GEMINI_FLASH_LITE_LATEST
        val GEMINI_4_ARGON = GEMINI_FLASH_LITE_LATEST

        val ALL_MODELS = listOf(
            GEMINI_FLASH_LITE_LATEST
        )
    }
}
