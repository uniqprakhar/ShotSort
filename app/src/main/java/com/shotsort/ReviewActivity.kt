package com.shotsort

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale

/** Lists every event found in Calendar-folder screenshots; the user adds or skips each one. */
class ReviewActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private val titleFields = mutableMapOf<String, EditText>()

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!CalendarWriter.hasPermission(this)) toast("Calendar access is needed to add events")
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(container)
        setContentView(scroll)
        Digest.cancel(this)
        if (!CalendarWriter.hasPermission(this) && Proposals.all(this).isNotEmpty()) {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        container.removeAllViews()
        titleFields.clear()
        container.addView(TextView(this).apply { text = "Events from your screenshots"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        val items = Proposals.all(this)
        if (items.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Nothing waiting. Images that land in your Calendar folder are read automatically and their events show up here."
                textSize = 14f; setPadding(0, dp(12), 0, 0)
            })
            return
        }
        container.addView(TextView(this).apply {
            text = "Fix the title if needed, then add or skip each one."
            textSize = 13f; setPadding(0, dp(4), 0, dp(8))
        })
        for (p in items) container.addView(card(p))
        container.addView(Button(this).apply {
            text = "Add all (${items.size})"
            setOnClickListener { addAll() }
        })
    }

    private fun card(p: Proposal): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setStroke(dp(1), Color.GRAY); cornerRadius = dp(12).toFloat() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        val title = EditText(this).apply { setText(p.title); textSize = 17f; setSingleLine(false) }
        titleFields[p.id] = title
        box.addView(title)
        box.addView(TextView(this).apply { text = describe(p); textSize = 14f })
        if (p.location.isNotBlank()) box.addView(TextView(this).apply { text = "📍 ${p.location}"; textSize = 13f })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(Button(this).apply {
            text = "Add"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { approve(p) }
        })
        row.addView(Button(this).apply {
            text = "Skip"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { Proposals.remove(this@ReviewActivity, p.id); render() }
        })
        box.addView(row)
        return box
    }

    private fun approve(p: Proposal) {
        if (!CalendarWriter.hasPermission(this)) {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            toast("Allow calendar access, then tap Add again")
            return
        }
        val r = CalendarWriter.add(this, p, titleFields[p.id]?.text?.toString()?.ifBlank { p.title } ?: p.title)
        toast(r.message)
        if (r.ok) { Proposals.remove(this, p.id); render() }
    }

    private fun addAll() {
        if (!CalendarWriter.hasPermission(this)) {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            toast("Allow calendar access, then tap Add all again")
            return
        }
        var ok = 0
        for (p in Proposals.all(this)) {
            val r = CalendarWriter.add(this, p, titleFields[p.id]?.text?.toString()?.ifBlank { p.title } ?: p.title)
            if (r.ok) { Proposals.remove(this, p.id); ok++ }
        }
        toast("Added $ok event(s)")
        render()
    }

    private fun describe(p: Proposal): String {
        val day = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        val time = SimpleDateFormat("h:mm a", Locale.getDefault())
        return if (p.allDay) "${day.format(p.start)} (all day)"
        else "${day.format(p.start)}, ${time.format(p.start)} - ${time.format(p.end)}"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
