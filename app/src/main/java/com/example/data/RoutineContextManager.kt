package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RoutineContextManager(private val context: Context) {
    private val firestore get() = FirebaseHelper.getFirestore(context)
    private val _routineSummary = MutableStateFlow<String>("")
    val routineSummary: StateFlow<String> = _routineSummary.asStateFlow()

    init {
        fetchAllFirestoreData()
    }

    private fun fetchAllFirestoreData() {
        try {
            val db = firestore ?: return
            db.collection("routines").get().addOnSuccessListener { routinesSnapshot ->
                db.collection("logs").get().addOnSuccessListener { logsSnapshot ->
                    db.collection("stats").get().addOnSuccessListener { statsSnapshot ->
                        try {
                            val routinesDetails = mutableListOf<String>()
                            for (doc in routinesSnapshot.documents) {
                                val data = doc.data ?: continue
                                val title = data["title"] ?: data["name"] ?: data["routineName"] ?: data["text"] ?: doc.id
                                val time = data["time"] ?: data["hour"] ?: data["scheduleTime"] ?: data["reminderTime"] ?: data["clock"] ?: "Belirtilmemiş saat"
                                val frequency = data["frequency"] ?: data["days"] ?: data["repeat"] ?: ""
                                routinesDetails.add("- Rutin: $title | Zaman/Saat: $time ${if (frequency.toString().isNotBlank()) "| Sıklık: $frequency" else ""}")
                            }

                            val logsList = mutableListOf<String>()
                            for (doc in logsSnapshot.documents.sortedByDescending { it.getString("date") ?: "" }.take(25)) {
                                val date = doc.getString("date") ?: ""
                                val status = doc.getString("status") ?: ""
                                val note = doc.getString("note") ?: ""
                                if (date.isNotBlank()) {
                                    logsList.add("Tarih: $date | Durum: $status ${if (note.isNotBlank()) "| Not: $note" else ""}")
                                }
                            }

                            val statsList = mutableListOf<String>()
                            for (doc in statsSnapshot.documents) {
                                val data = doc.data ?: continue
                                statsList.add(data.entries.joinToString(", ") { "${it.key}: ${it.value}" })
                            }

                            val summary = buildString {
                                append("KULLANICININ TÜM RUTİN, ZAMAN VE PERFORMANS VERİLERİ (Ortak Firestore):\n")
                                append("1. TANIMLI RUTİNLER VE SAATLERİ:\n")
                                if (routinesDetails.isNotEmpty()) {
                                    routinesDetails.forEach { append("$it\n") }
                                } else {
                                    append("• Kayıtlı rutin tanımı bulunamadı.\n")
                                }

                                append("\n2. SON RUTİN LOGLARI VE GEÇMİŞ DURUMLAR:\n")
                                if (logsList.isNotEmpty()) {
                                    logsList.forEach { append("• $it\n") }
                                }

                                if (statsList.isNotEmpty()) {
                                    append("\n3. İSTATİSTİKLER VE PERFORMANS:\n")
                                    statsList.forEach { append("• $it\n") }
                                }

                                append("\nTALİMAT: Kullanıcı ile gün planı yaparken veya rutinler hakkında konuşurken, rutinlerin saatlerini, sıklıklarını ve geçmiş tamamlama durumlarını tam olarak göz önünde bulundur. Birlikte en gerçekçi ve verimli zaman planını oluştur.\n")
                            }
                            _routineSummary.value = summary
                        } catch (e: Throwable) {
                            Log.e("RoutineContextManager", "Error parsing routines data", e)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("RoutineContextManager", "Error fetching Firestore routines data", e)
        }
    }
}
