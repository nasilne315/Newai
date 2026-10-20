package com.example.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RoutineContextManager(private val context: Context) {
    private val tag = "RoutineContextManager"
    private val firestore get() = FirebaseHelper.getFirestore(context)

    private val _routineSummary = MutableStateFlow<String>("")
    val routineSummary: StateFlow<String> = _routineSummary.asStateFlow()

    private val _lastFetchTime = MutableStateFlow<Long>(0L)
    val lastFetchTime: StateFlow<Long> = _lastFetchTime.asStateFlow()

    init {
        registerRealtimeListeners()
    }

    private fun registerRealtimeListeners() {
        val db = firestore ?: return
        try {
            db.collection("routines").addSnapshotListener { _, error ->
                if (error != null) {
                    Log.w(tag, "Routines listener error", error)
                    return@addSnapshotListener
                }
                triggerBackgroundRefresh()
            }
            db.collection("logs").addSnapshotListener { _, error ->
                if (error != null) {
                    Log.w(tag, "Logs listener error", error)
                    return@addSnapshotListener
                }
                triggerBackgroundRefresh()
            }
            db.collection("stats").addSnapshotListener { _, error ->
                if (error != null) {
                    Log.w(tag, "Stats listener error", error)
                    return@addSnapshotListener
                }
                triggerBackgroundRefresh()
            }
            db.collection("noro_tasks").addSnapshotListener { _, error ->
                if (error != null) {
                    Log.w(tag, "Tasks listener error", error)
                    return@addSnapshotListener
                }
                triggerBackgroundRefresh()
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error registering Firestore listeners", e)
        }
    }

    private fun triggerBackgroundRefresh() {
        val db = firestore ?: return
        try {
            db.collection("routines").get().addOnSuccessListener { routinesSnap ->
                db.collection("logs").get().addOnSuccessListener { logsSnap ->
                    db.collection("stats").get().addOnSuccessListener { statsSnap ->
                        db.collection("noro_tasks").get().addOnSuccessListener { tasksSnap ->
                            buildAndEmitSummary(routinesSnap, logsSnap, statsSnap, tasksSnap)
                        }.addOnFailureListener {
                            buildAndEmitSummary(routinesSnap, logsSnap, statsSnap, null)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error in triggerBackgroundRefresh", e)
        }
    }

    /**
     * Actively fetches fresh Firestore data from the server or cache every time it is needed,
     * ensuring the AI assistant always interprets and analyzes the most up-to-date user records.
     */
    suspend fun fetchFreshFirestoreData(): String = withContext(Dispatchers.IO) {
        val db = firestore
        if (db == null) {
            return@withContext _routineSummary.value
        }

        try {
            val routinesTask = withTimeoutOrNull(3500) { db.collection("routines").get().awaitResult() }
            val logsTask = withTimeoutOrNull(3500) { db.collection("logs").get().awaitResult() }
            val statsTask = withTimeoutOrNull(3500) { db.collection("stats").get().awaitResult() }
            val tasksTask = withTimeoutOrNull(3500) { db.collection("noro_tasks").get().awaitResult() }

            val summary = buildSummaryFromSnapshots(routinesTask, logsTask, statsTask, tasksTask)
            if (summary.isNotBlank()) {
                _routineSummary.value = summary
                _lastFetchTime.value = System.currentTimeMillis()
                return@withContext summary
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error fetching fresh Firestore data", e)
        }

        return@withContext _routineSummary.value
    }

    private fun buildAndEmitSummary(
        routinesSnap: QuerySnapshot?,
        logsSnap: QuerySnapshot?,
        statsSnap: QuerySnapshot?,
        tasksSnap: QuerySnapshot?
    ) {
        val summary = buildSummaryFromSnapshots(routinesSnap, logsSnap, statsSnap, tasksSnap)
        if (summary.isNotBlank()) {
            _routineSummary.value = summary
            _lastFetchTime.value = System.currentTimeMillis()
        }
    }

    private fun buildSummaryFromSnapshots(
        routinesSnap: QuerySnapshot?,
        logsSnap: QuerySnapshot?,
        statsSnap: QuerySnapshot?,
        tasksSnap: QuerySnapshot?
    ): String {
        val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR"))
        val nowStr = timeFormat.format(Date())

        val routinesDetails = mutableListOf<String>()
        routinesSnap?.documents?.forEach { doc ->
            val data = doc.data ?: return@forEach
            val title = data["title"] ?: data["name"] ?: data["routineName"] ?: data["text"] ?: doc.id
            val time = data["time"] ?: data["hour"] ?: data["scheduleTime"] ?: data["reminderTime"] ?: data["clock"] ?: "Saat belirtilmedi"
            val frequency = data["frequency"] ?: data["days"] ?: data["repeat"] ?: ""
            val category = data["category"] ?: data["tag"] ?: ""
            val target = data["target"] ?: data["goal"] ?: ""
            val desc = buildString {
                append("• Rutin: $title | Zaman: $time")
                if (frequency.toString().isNotBlank()) append(" | Sıklık: $frequency")
                if (category.toString().isNotBlank()) append(" | Kategori: $category")
                if (target.toString().isNotBlank()) append(" | Hedef: $target")
            }
            routinesDetails.add(desc)
        }

        val logsList = mutableListOf<String>()
        val sortedLogs = logsSnap?.documents
            ?.sortedByDescending { it.getString("date") ?: it.getLong("timestamp")?.toString() ?: "" }
            ?.take(30)
            ?: emptyList()

        sortedLogs.forEach { doc ->
            val date = doc.getString("date") ?: ""
            val status = doc.getString("status") ?: doc.get("completed")?.toString() ?: ""
            val note = doc.getString("note") ?: doc.getString("description") ?: ""
            val title = doc.getString("routineTitle") ?: doc.getString("title") ?: ""
            val duration = doc.get("duration")?.toString() ?: ""
            if (date.isNotBlank() || title.isNotBlank()) {
                val logEntry = buildString {
                    append("• [${date.ifBlank { "Tarihsiz" }}] ")
                    if (title.isNotBlank()) append("Rutin: $title | ")
                    append("Durum: ${status.ifBlank { "Kayıtlı" }}")
                    if (duration.isNotBlank()) append(" | Süre: $duration")
                    if (note.isNotBlank()) append(" | Not: $note")
                }
                logsList.add(logEntry)
            }
        }

        val statsList = mutableListOf<String>()
        statsSnap?.documents?.forEach { doc ->
            val data = doc.data ?: return@forEach
            val entryStr = data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            if (entryStr.isNotBlank()) {
                statsList.add("• [${doc.id}] $entryStr")
            }
        }

        val tasksList = mutableListOf<String>()
        tasksSnap?.documents?.forEach { doc ->
            val title = doc.getString("title") ?: ""
            val isCompleted = doc.getBoolean("isCompleted") ?: false
            val category = doc.getString("category") ?: "Genel"
            if (title.isNotBlank()) {
                val mark = if (isCompleted) "[X] Tamamlandı" else "[ ] Beklemede"
                tasksList.add("• $mark: $title ($category)")
            }
        }

        return buildString {
            append("KULLANICININ EN GÜNCEL CANLI FIRESTORE VERİLERİ (Son Çekilme: $nowStr):\n")

            append("\n1. TANIMLI RUTİNLER & PROGRAM (Firestore 'routines'):\n")
            if (routinesDetails.isNotEmpty()) {
                routinesDetails.forEach { append("$it\n") }
            } else {
                append("• Henüz kayıtlı rutin tanımı bulunmuyor.\n")
            }

            append("\n2. GERÇEKLEŞEN GÜNLÜK LOGLAR & AKTİVİTE GEÇMİŞİ (Firestore 'logs' - Son 30 Kayıt):\n")
            if (logsList.isNotEmpty()) {
                logsList.forEach { append("$it\n") }
            } else {
                append("• Henüz geçmiş rutin logu bulunmuyor.\n")
            }

            if (statsList.isNotEmpty()) {
                append("\n3. GENEL İSTATİSTİKLER, SERİLER & PERFORMANS (Firestore 'stats'):\n")
                statsList.forEach { append("$it\n") }
            }

            if (tasksList.isNotEmpty()) {
                append("\n4. YAPILACAKLAR & GÖREV LİSTESİ (Firestore 'noro_tasks'):\n")
                tasksList.forEach { append("$it\n") }
            }

            append("\nCANLI FIRESTORE ANALİZ VE YORUMLAMA TALİMATI:\n")
            append("1. Yukarıdaki veriler kullanıcının Firestore bulut veritabanından doğrudan ve canlı olarak çekilmiştir.\n")
            append("2. Kullanıcı rutinlerini, geçmiş performansını, alışkanlıklarını, hedeflerini, gününü veya ne yapması gerektiğini sorduğunda bu güncel verileri derinlemesine incele.\n")
            append("3. Hangi rutinleri aksattığını, hangilerini istikrarlı yaptığını analiz et, serileri (streak) ve başarı oranlarını tespit ederek kullanıcıya somut, motive edici ve gerçekçi geri bildirimler sun.\n")
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
}
