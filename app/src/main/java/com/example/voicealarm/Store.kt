package com.example.voicealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(val id: Int, val text: String, val time: Long)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Reminder> {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        return MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Reminder(o.getInt("id"), o.getString("text"), o.getLong("time"))
        }
    }

    private fun save(c: Context, l: List<Reminder>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
        prefs(c).edit().putString("list", a.toString()).apply()
    }

    fun add(c: Context, r: Reminder) {
        save(c, load(c) + r)
        schedule(c, r)
    }

    fun delete(c: Context, r: Reminder) {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c, r))
        remove(c, r.id)
    }

    fun remove(c: Context, id: Int) = save(c, load(c).filter { it.id != id })

    fun schedule(c: Context, r: Reminder) {
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        c.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(r.time, show), pending(c, r))
    }

    private fun pending(c: Context, r: Reminder) = PendingIntent.getBroadcast(
        c, r.id,
        Intent(c, AlarmReceiver::class.java).putExtra("text", r.text).putExtra("id", r.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
