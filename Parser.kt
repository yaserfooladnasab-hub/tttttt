package com.example.voicealarm

import android.icu.util.PersianCalendar
import java.util.Calendar

/** تشخیص تاریخ و ساعت از جمله‌ی فارسی، مثل «فردا ساعت ۷ و نیم صبح» */
object Parser {
    val months = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val words = mapOf(
        "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5, "شش" to 6,
        "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10, "یازده" to 11, "دوازده" to 12,
        "پانزده" to 15, "بیست" to 20, "سی" to 30
    )

    private val weekdays = mapOf(
        "شنبه" to Calendar.SATURDAY, "یکشنبه" to Calendar.SUNDAY,
        "دوشنبه" to Calendar.MONDAY, "سهشنبه" to Calendar.TUESDAY,
        "چهارشنبه" to Calendar.WEDNESDAY, "پنجشنبه" to Calendar.THURSDAY,
        "جمعه" to Calendar.FRIDAY
    )

    private fun normalize(t: String): String {
        val sb = StringBuilder()
        for (ch in t) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    '\u200c' -> ' '
                    else -> ch
                }
            )
        }
        var s = sb.toString().lowercase().replace(Regex("[،,!؟?]"), " ")
        val fixes = listOf(
            "سه شنبه" to "سهشنبه", "چهار شنبه" to "چهارشنبه", "پنج شنبه" to "پنجشنبه",
            "دو شنبه" to "دوشنبه", "یک شنبه" to "یکشنبه", "پس فردا" to "پسفردا",
            "بعد از ظهر" to "بعدازظهر", "نیمه شب" to "شب"
        )
        for ((a, b) in fixes) s = s.replace(a, b)
        s = s.replace(Regex("(^|\\s)دیگر(?=\\s|$)"), "\$1دیگه")
        return s.split(" ").filter { it.isNotEmpty() }
            .joinToString(" ") { words[it]?.toString() ?: it }
    }

    /** اگر چیزی پیدا نشد null برمی‌گرداند؛ وگرنه تقویم تنظیم‌شده را */
    fun parse(raw: String, base: Calendar): Calendar? {
        val s = normalize(raw)
        val tokens = s.split(" ")
        val now = Calendar.getInstance()
        val c = base.clone() as Calendar
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)

        // «۲ ساعت دیگه»، «۱۰ دقیقه دیگه»، «نیم ساعت دیگه»
        Regex("(\\d+)\\s*(ساعت|دقیقه)\\s*دیگه").find(s)?.let {
            val r = Calendar.getInstance()
            r.add(
                if (it.groupValues[2] == "ساعت") Calendar.HOUR_OF_DAY else Calendar.MINUTE,
                it.groupValues[1].toInt()
            )
            r.set(Calendar.SECOND, 0)
            r.set(Calendar.MILLISECOND, 0)
            return r
        }
        if (s.contains("نیم ساعت دیگه")) {
            val r = Calendar.getInstance()
            r.add(Calendar.MINUTE, 30)
            r.set(Calendar.SECOND, 0)
            r.set(Calendar.MILLISECOND, 0)
            return r
        }

        // ---------- تاریخ ----------
        var dateFound = false
        fun setDate(d: Calendar) {
            c.set(d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH))
            dateFound = true
        }
        fun inDays(n: Int) {
            val d = Calendar.getInstance()
            d.add(Calendar.DAY_OF_MONTH, n)
            setDate(d)
        }

        when {
            tokens.contains("پسفردا") -> inDays(2)
            tokens.contains("فردا") -> inDays(1)
            tokens.contains("امروز") || tokens.contains("امشب") -> inDays(0)
        }

        if (!dateFound) {
            Regex("(\\d+)\\s*(روز|هفته)\\s*دیگه").find(s)?.let {
                val n = it.groupValues[1].toInt()
                inDays(if (it.groupValues[2] == "هفته") n * 7 else n)
            }
        }

        // «۵ آبان»، «۱۵ دی ماه ۱۴۰۵»
        if (!dateFound) {
            val re = Regex("(\\d{1,2})\\s*(${months.joinToString("|")})(?=\\s|$)(?:\\s+ماه)?(?:\\s+(\\d{4}))?")
            re.find(s)?.let {
                val day = it.groupValues[1].toInt()
                val mi = months.indexOf(it.groupValues[2])
                val explicitYear = it.groupValues[3].toIntOrNull()
                val pc = PersianCalendar()
                pc.set(explicitYear ?: pc.get(Calendar.YEAR), mi, day)
                if (explicitYear == null &&
                    pc.timeInMillis < System.currentTimeMillis() - 12 * 3600_000L
                ) {
                    pc.set(Calendar.YEAR, pc.get(Calendar.YEAR) + 1)
                }
                val g = Calendar.getInstance()
                g.timeInMillis = pc.timeInMillis
                setDate(g)
            }
        }

        // «۱۴۰۵/۰۸/۱۵»
        if (!dateFound) {
            Regex("(\\d{4})/(\\d{1,2})/(\\d{1,2})").find(s)?.let {
                val y = it.groupValues[1].toInt()
                val m = it.groupValues[2].toInt() - 1
                val d = it.groupValues[3].toInt()
                val g = Calendar.getInstance()
                if (y < 1700) {
                    val pc = PersianCalendar()
                    pc.set(y, m, d)
                    g.timeInMillis = pc.timeInMillis
                } else {
                    g.set(y, m, d)
                }
                setDate(g)
            }
        }

        // «شنبه»، «پنجشنبه» → اولین بار بعد از امروز
        if (!dateFound) {
            tokens.firstOrNull { weekdays.containsKey(it) }?.let {
                var diff = (weekdays[it]!! - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (diff == 0) diff = 7
                inDays(diff)
            }
        }

        // ---------- ساعت ----------
        var h = -1
        var min = 0

        Regex("ربع\\s*به\\s*(\\d{1,2})").find(s)?.let {
            h = it.groupValues[1].toInt() - 1
            if (h == 0) h = 12
            min = 45
        }
        if (h < 0) {
            Regex("(\\d{1,2})\\s*دقیقه\\s*به\\s*(\\d{1,2})").find(s)?.let {
                h = it.groupValues[2].toInt() - 1
                if (h == 0) h = 12
                min = 60 - it.groupValues[1].toInt()
            }
        }
        if (h < 0) {
            Regex("ساعت\\s*(\\d{1,2})(?:[:.](\\d{1,2}))?").find(s)?.let {
                h = it.groupValues[1].toInt()
                min = it.groupValues[2].toIntOrNull() ?: 0
                val tail = s.substring(it.range.last + 1)
                when {
                    Regex("^\\s*و\\s*نیم").containsMatchIn(tail) -> min = 30
                    Regex("^\\s*و\\s*ربع").containsMatchIn(tail) -> min = 15
                    else -> Regex("^\\s*و\\s*(\\d{1,2})\\s*دقیقه").find(tail)?.let { m ->
                        min = m.groupValues[1].toInt()
                    }
                }
            }
        }
        if (h < 0) {
            Regex("(\\d{1,2}):(\\d{2})").find(s)?.let {
                h = it.groupValues[1].toInt()
                min = it.groupValues[2].toInt()
            }
        }
        val noon = tokens.contains("ظهر")
        if (h < 0 && noon) h = 12

        val timeFound = h >= 0
        if (!dateFound && !timeFound) return null

        val onlyTime = !dateFound
        if (onlyTime) inDays(0)

        if (timeFound) {
            val am = tokens.contains("صبح") || tokens.contains("بامداد")
            val pm = tokens.contains("عصر") || tokens.contains("بعدازظهر")
            val night = tokens.contains("شب") || tokens.contains("امشب")
            var addDay = 0
            when {
                am -> if (h == 12) h = 0
                noon -> if (h in 1..5) h += 12
                pm -> if (h < 12) h += 12
                night -> when (h) {
                    12 -> {
                        h = 0
                        addDay = 1
                    }
                    in 1..5 -> addDay = 1
                    in 6..11 -> h += 12
                }
                else -> if (h in 1..6) h += 12
            }
            c.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
            c.set(Calendar.MINUTE, min.coerceIn(0, 59))
            if (addDay > 0) c.add(Calendar.DAY_OF_MONTH, addDay)
            if (onlyTime && c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c
    }
}
