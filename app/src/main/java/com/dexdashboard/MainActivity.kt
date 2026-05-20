package com.dexdashboard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dexdashboard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val SETTINGS_PKG = "com.android.settings"

    private object DeXActivity {
        const val DEX_MODE          = "com.android.settings.Settings\$DexModeActivity"
        const val DEX_ENTRY_SCREEN  = "com.android.settings.Settings\$DexEntryScreenSettingsActivity"
        const val KEYBOARD          = "com.android.settings.Settings\$DexModeKeyboardSettingsActivity"
        const val MOUSE             = "com.android.settings.Settings\$DexModeMouseSettingsActivity"
        const val SPEN              = "com.android.settings.Settings\$DexModeSpenSettingsActivity"
        const val GESTURE_CONTROLS  = "com.android.settings.Settings\$DexModeTouchGestureSettingsActivity"
        const val GESTURE_OPTIONS   = "com.android.settings.Settings\$DexModeTouchGestureOptionSelectionActivity"
        const val TALKBACK_STYLE    = "com.android.settings.Settings\$DexTalkbackSettingsDisplayStyleActivity"
        const val TALKBACK_POSITION = "com.android.settings.Settings\$DexTalkbackSettingsDisplayPositionActivity"
    }

    sealed class DashboardItem {
        data class Header(val title: String) : DashboardItem()
        data class Card(
            val title: String,
            val subtitle: String,
            val icon: String,
            val actionType: ActionType,
            val badge: String? = null
        ) : DashboardItem()
    }

    sealed class ActionType {
        data class DirectLaunch(val className: String) : ActionType()
        object GestureDialog : ActionType()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkDeviceCompatibility()
        setupDashboardList()
    }

    private fun checkDeviceCompatibility() {
        val isSamsung = Build.MANUFACTURER.lowercase().contains("samsung")
        if (!isSamsung) {
            binding.bannerNonSamsung.visibility = View.VISIBLE
        }
    }

    private fun setupDashboardList() {
        val items = listOf(
            DashboardItem.Header("Core System"),
            DashboardItem.Card("DeX Mode", "Main configuration panel", "💻", ActionType.DirectLaunch(DeXActivity.DEX_MODE)),
            DashboardItem.Card("DeX Landing Page", "Startup display behaviors", "🚀", ActionType.DirectLaunch(DeXActivity.DEX_ENTRY_SCREEN)),

            DashboardItem.Header("Peripherals & Input"),
            DashboardItem.Card("Keyboard Preferences", "Manage layout and shortcuts", "⌨️", ActionType.DirectLaunch(DeXActivity.KEYBOARD)),
            DashboardItem.Card("Pointer Controls", "Mouse speeds and scrolling", "🖱️", ActionType.DirectLaunch(DeXActivity.MOUSE)),
            DashboardItem.Card("S Pen Input", "Stylus behavior in desktop mode", "✍️", ActionType.DirectLaunch(DeXActivity.SPEN)),

            DashboardItem.Header("Gestures Suite"),
            DashboardItem.Card("Touchpad & Gestures", "Swipe settings and option mapping", "👆", ActionType.GestureDialog, "2 modules"),

            DashboardItem.Header("Accessibility Layouts"),
            DashboardItem.Card("Display Style", "Talkback visual adjustments", "🎨", ActionType.DirectLaunch(DeXActivity.TALKBACK_STYLE)),
            DashboardItem.Card("Display Position", "Talkback alignment settings", "📌", ActionType.DirectLaunch(DeXActivity.TALKBACK_POSITION))
        )

        val dashboardAdapter = DashboardAdapter(items) { cardItem ->
            when (val action = cardItem.actionType) {
                is ActionType.DirectLaunch -> safeLaunchActivity(action.className)
                is ActionType.GestureDialog -> showGestureDialog()
            }
        }

        val layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (dashboardAdapter.getItemViewType(position)) {
                    DashboardAdapter.VIEW_TYPE_HEADER -> 2 
                    else -> 1 
                }
            }
        }

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = dashboardAdapter
    }

    private fun showGestureDialog() {
        val options = arrayOf("Gesture Controls", "Advanced Option Mapping")
        AlertDialog.Builder(this)
            .setTitle("Touchpad & Gestures")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> safeLaunchActivity(DeXActivity.GESTURE_CONTROLS)
                    1 -> safeLaunchActivity(DeXActivity.GESTURE_OPTIONS)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun safeLaunchActivity(className: String) {
        try {
            val intent = Intent().apply {
                component = ComponentName(SETTINGS_PKG, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showLaunchError(className, "not found on this firmware")
        } catch (e: SecurityException) {
            showLaunchError(className, "blocked — may require elevated permissions on this ROM")
        } catch (e: Exception) {
            showLaunchError(className, "unavailable: ${e.localizedMessage}")
        }
    }

    private fun showLaunchError(className: String, reason: String) {
        val shortName = className.substringAfterLast("$").removeSuffix("Activity")
        Toast.makeText(
            this,
            "⚠️ $shortName: Sub-module $reason.\nThis module may be stripped from your carrier firmware.",
            Toast.LENGTH_LONG
        ).show()
    }

    private class DashboardAdapter(
        private val items: List<DashboardItem>,
        private val onCardClick: (DashboardItem.Card) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val VIEW_TYPE_HEADER = 0
            const val VIEW_TYPE_CARD = 1
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DashboardItem.Header -> VIEW_TYPE_HEADER
                is DashboardItem.Card -> VIEW_TYPE_CARD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                val view = TextView(parent.context).apply {
                    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(dpToPx(12, context), dpToPx(20, context), dpToPx(12, context), dpToPx(8, context))
                    }
                    setTextColor(context.getColor(R.color.samsung_blue))
                    val typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    setTypeface(typeface)
                    textSize = 13f
                }
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dex_card, parent, false)
                CardViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DashboardItem.Header -> (holder as HeaderViewHolder).bind(item)
                is DashboardItem.Card -> (holder as CardViewHolder).bind(item, onCardClick)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun dpToPx(dp: Int, context: Context): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(header: DashboardItem.Header) {
                (itemView as TextView).text = header.title.uppercase()
            }
        }

        class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val iconText: TextView = view.findViewById(R.id.textIcon)
            private val titleText: TextView = view.findViewById(R.id.textTitle)
            private val subtitleText: TextView = view.findViewById(R.id.textSubtitle)
            private val badgeText: TextView = view.findViewById(R.id.textBadge)

            fun bind(card: DashboardItem.Card, onClick: (DashboardItem.Card) -> Unit) {
                iconText.text = card.icon
                titleText.text = card.title
                subtitleText.text = card.subtitle
                
                if (card.badge != null) {
                    badgeText.text = card.badge
                    badgeText.visibility = View.VISIBLE
                } else {
                    badgeText.visibility = View.GONE
                }

                itemView.setOnClickListener { onClick(card) }
            }
        }
    }
}