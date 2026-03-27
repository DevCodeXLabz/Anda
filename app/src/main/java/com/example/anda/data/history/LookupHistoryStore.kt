package com.example.anda.data.history

import android.content.Context
import com.example.anda.core.security.LocalDataProtection
import org.json.JSONArray
import org.json.JSONObject

class LookupHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        LocalDataProtection.initialize(context.applicationContext)
    }

    fun readAll(): List<LookupHistoryItem> {
        val stored = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val json = LocalDataProtection.decryptString(stored)
            val array = JSONArray(json)
            val items = mutableListOf<LookupHistoryItem>()
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                items += LookupHistoryItem(
                    cnpj = obj.optString("cnpj"),
                    legalName = obj.optString("legalName"),
                    cnae = obj.optString("cnae"),
                    riskGrade = obj.optInt("riskGrade", 2),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            }
            items
        }.getOrElse {
            // Corrupted or unreadable entry (e.g. key loss after reinstall).
            // Clear it so future appends write fresh data.
            prefs.edit().remove(KEY_ITEMS).apply()
            emptyList()
        }
    }

    fun append(item: LookupHistoryItem) {
        val current = readAll().toMutableList()
        val recentSame = current.firstOrNull { it.cnpj == item.cnpj }
        if (recentSame != null) {
            val unchanged = recentSame.legalName == item.legalName &&
                recentSame.cnae == item.cnae &&
                recentSame.riskGrade == item.riskGrade
            val inDedupWindow = (item.timestamp - recentSame.timestamp) in 0..DEDUP_WINDOW_MS
            if (unchanged && inDedupWindow) {
                return
            }
        }

        current.add(0, item)

        // Mantem historico enxuto e rapido para leitura no startup.
        val limited = current.distinctBy { it.cnpj }.take(MAX_ITEMS)

        val array = JSONArray()
        limited.forEach {
            array.put(
                JSONObject().apply {
                    put("cnpj", it.cnpj)
                    put("legalName", it.legalName)
                    put("cnae", it.cnae)
                    put("riskGrade", it.riskGrade)
                    put("timestamp", it.timestamp)
                }
            )
        }
        val encrypted = LocalDataProtection.encryptString(array.toString())
        prefs.edit().putString(KEY_ITEMS, encrypted).apply()
    }

    companion object {
        private const val PREFS_NAME = "anda_lookup_history"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 20
        private const val DEDUP_WINDOW_MS = 5L * 60L * 1000L
    }
}

data class LookupHistoryItem(
    val cnpj: String,
    val legalName: String,
    val cnae: String,
    val riskGrade: Int,
    val timestamp: Long
)

