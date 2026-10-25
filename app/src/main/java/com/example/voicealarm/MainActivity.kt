package com.example.voicealarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.icu.util.PersianCalendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.widget.*
import java.util.Calendar
import java.util.Date

class MainActivity : Activity() {

    private lateinit var etText: EditText
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var listBox: LinearLayout
    private val cal: Calendar = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
    }

    private val dateFmt = SimpleDateFormat("EEEE d MMMM yyyy", ULocale("fa_IR@calendar=persian"))
    private val fullFmt = SimpleDateFormat("EEEE d MMMM yyyy - HH:mm", ULocale("fa_IR@calendar=persian"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        etText = EditText(this).apply {
            hint = "مثلاً: فردا ساعت ۷ و نیم صبح جلسه دارم"
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }
        val mic = Button(this).apply {
            text = "🎤 گفتن"
            setOnClickListener { listen() }
        }
        btnDate = Button(this).apply { setOnClickListener { pickDate() } }
        btnTime = Button(this).apply { setOnClickListener { pickTime() } }
        val detect = Button(this).apply {
            text = "🔍 تشخیص زمان از متن"
            setOnClickListener { detectFrom(etText.text.toString()) }
        }
        val save = Button(this).apply {
            text = "➕ ذخیره و تنظیم آلارم"
            setOnClickListener { saveReminder() }
        }
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        listOf(etText, mic, detect, btnDate, btnTime, save, listBox).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })

        refreshLabels()
        refreshList()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "بگو...")
        try {
            startActivityForResult(i, 100)
        } catch (e: Exception) {
            Toast.makeText(this, "تشخیص گفتار روی این گوشی در دسترس نیست", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (spoken != null) {
                val old = etText.text.toString()
                etText.setText(if (old.isBlank()) spoken else "$old $spoken")
                detectFrom(spoken)
            }
        }
    }

    /** تاریخ و ساعت را از متن در می‌آورد و روی دکمه‌ها می‌گذارد */
    private fun detectFrom(text: String) {
        val r = Parser.parse(text, cal)
        if (r == null) {
            Toast.makeText(this, "زمانی در متن پیدا نشد؛ دستی انتخاب کن", Toast.LENGTH_LONG).show()
            return
        }
        cal.timeInMillis = r.timeInMillis
        refreshLabels()
        Toast.makeText(
            this,
            "⏰ " + fullFmt.format(cal.time) + "\nاگر درست نیست، تغییرش بده و ذخیره کن",
            Toast.LENGTH_LONG
        ).show()
    }

    /** انتخاب تاریخ شمسی */
    private fun pickDate() {
        val pc = PersianCalendar()
        pc.timeInMillis = cal.timeInMillis
        val curYear = PersianCalendar().get(Calendar.YEAR)

        val yPick = NumberPicker(this).apply {
            minValue = curYear
            maxValue = curYear + 10
            value = pc.get(Calendar.YEAR).coerceIn(curYear, curYear + 10)
            wrapSelectorWheel = false
        }
        val mPick = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = Parser.months.toTypedArray()
            value = pc.get(Calendar.MONTH)
        }
        val dPick = NumberPicker(this).apply {
            minValue = 1
            maxValue = 31
            value = pc.get(Calendar.DAY_OF_MONTH)
        }

        fun updateMaxDay() {
            val t = PersianCalendar()
            t.set(yPick.value, mPick.value, 1)
            dPick.maxValue = t.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        updateMaxDay()
        yPick.setOnValueChangedListener { _, _, _ -> updateMaxDay() }
        mPick.setOnValueChangedListener { _, _, _ -> updateMaxDay() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(dPick)
            addView(mPick)
            addView(yPick)
        }

        AlertDialog.Builder(this)
            .setTitle("تاریخ شمسی")
            .setView(row)
            .setPositiveButton("تأیید") { _, _ ->
                val t = PersianCalendar()
                t.timeInMillis = cal.timeInMillis
                t.set(yPick.value, mPick.value, dPick.value)
                cal.timeInMillis = t.timeInMillis
                refreshLabels()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
            refreshLabels()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun refreshLabels() {
        btnDate.text = "📅 " + dateFmt.format(cal.time)
        btnTime.text = "🕒 " + String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    private fun saveReminder() {
        val text = etText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "اول متن را بنویس یا بگو", Toast.LENGTH_SHORT).show()
            return
        }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val time = cal.timeInMillis
        if (time <= System.currentTimeMillis()) {
            Toast.makeText(this, "این زمان گذشته است", Toast.LENGTH_SHORT).show()
            return
        }
        Store.add(this, Reminder(System.currentTimeMillis().toInt(), text, time))
        etText.setText("")
        Toast.makeText(this, "آلارم تنظیم شد ✅", Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val now = System.currentTimeMillis()
        Store.load(this).filter { it.time > now }.sortedBy { it.time }.forEach { r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 24, 0, 24)
            }
            val tv = TextView(this).apply {
                text = "⏰ ${fullFmt.format(Date(r.time))}\n${r.text}"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = Button(this).apply {
                text = "🗑"
                setOnClickListener {
                    Store.delete(this@MainActivity, r)
                    refreshList()
                }
            }
            row.addView(tv)
            row.addView(del)
            listBox.addView(row)
        }
    }
}
