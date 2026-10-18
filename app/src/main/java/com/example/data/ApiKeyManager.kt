package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiKeyStatus(
    val key1: String = "",
    val key2: String = "",
    val key3: String = "",
    val activeIndex: Int = 0,
    val selectedModel: String = "gemini-2.0-flash",
    val lastFailoverNotice: String? = null
)

class ApiKeyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemini_api_pool_prefs", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(loadInitialStatus())
    val status: StateFlow<ApiKeyStatus> = _status.asStateFlow()

    private fun loadInitialStatus(): ApiKeyStatus {
        val storedKey1 = prefs.getString("api_key_1", "")?.trim().orEmpty()
        val storedKey2 = prefs.getString("api_key_2", "")?.trim().orEmpty()
        val storedKey3 = prefs.getString("api_key_3", "")?.trim().orEmpty()

        val buildKey1 = getBuildConfigString("GEMINI_API_KEY_1")
            .ifEmpty { getBuildConfigString("GEMINI_API_KEY") }
        val buildKey2 = getBuildConfigString("GEMINI_API_KEY_2")
        val buildKey3 = getBuildConfigString("GEMINI_API_KEY_3")

        val key1 = storedKey1.ifEmpty { buildKey1 }
        val key2 = storedKey2.ifEmpty { buildKey2 }
        val key3 = storedKey3.ifEmpty { buildKey3 }

        val active = prefs.getInt("active_key_index", 0)
        val model = prefs.getString("selected_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"

        return ApiKeyStatus(
            key1 = key1,
            key2 = key2,
            key3 = key3,
            activeIndex = active.coerceIn(0, 2),
            selectedModel = model
        )
    }

    private fun getBuildConfigString(fieldName: String): String {
        return try {
            val field = BuildConfig::class.java.getField(fieldName)
            val value = field.get(null) as? String ?: ""
            if (value.startsWith("MY_GEMINI_API_KEY") || value.isBlank()) "" else value.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun saveKeys(k1: String, k2: String, k3: String, model: String? = null) {
        val clean1 = k1.trim()
        val clean2 = k2.trim()
        val clean3 = k3.trim()
        val targetModel = (model ?: _status.value.selectedModel).trim().ifEmpty { "gemini-2.0-flash" }

        prefs.edit()
            .putString("api_key_1", clean1)
            .putString("api_key_2", clean2)
            .putString("api_key_3", clean3)
            .putString("selected_model", targetModel)
            .apply()

        _status.value = _status.value.copy(
            key1 = clean1,
            key2 = clean2,
            key3 = clean3,
            selectedModel = targetModel,
            lastFailoverNotice = null
        )
    }

    fun setSelectedModel(model: String) {
        val cleanModel = model.trim().ifEmpty { "gemini-2.0-flash" }
        prefs.edit().putString("selected_model", cleanModel).apply()
        _status.value = _status.value.copy(selectedModel = cleanModel)
    }

    fun getValidKeysWithIndices(): List<Pair<Int, String>> {
        val s = _status.value
        val list = mutableListOf<Pair<Int, String>>()
        if (isValidKey(s.key1)) list.add(0 to s.key1)
        if (isValidKey(s.key2)) list.add(1 to s.key2)
        if (isValidKey(s.key3)) list.add(2 to s.key3)
        return list
    }

    fun getActiveKey(): Pair<Int, String>? {
        val valid = getValidKeysWithIndices()
        if (valid.isEmpty()) return null
        val currentIdx = _status.value.activeIndex
        return valid.find { it.first == currentIdx } ?: valid.first()
    }

    fun switchToNextKey(failedIndex: Int, reason: String = "Quota exceeded"): Pair<Int, String>? {
        val valid = getValidKeysWithIndices()
        if (valid.size <= 1) {
            _status.value = _status.value.copy(
                lastFailoverNotice = "Anahtar #${failedIndex + 1} tükendi ve başka yedek anahtar bulunamadı."
            )
            return null
        }

        val currentPos = valid.indexOfFirst { it.first == failedIndex }
        val nextPos = if (currentPos != -1) (currentPos + 1) % valid.size else 0
        val next = valid[nextPos]

        prefs.edit().putInt("active_key_index", next.first).apply()

        val notice = "Anahtar #${failedIndex + 1} ($reason). Otomatik olarak Anahtar #${next.first + 1}'e geçildi."
        _status.value = _status.value.copy(
            activeIndex = next.first,
            lastFailoverNotice = notice
        )
        return next
    }

    fun clearNotice() {
        _status.value = _status.value.copy(lastFailoverNotice = null)
    }

    private fun isValidKey(key: String): Boolean {
        return key.isNotBlank() && !key.startsWith("MY_GEMINI_API_KEY") && key.length > 10
    }
}
