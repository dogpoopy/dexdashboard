package com.dexdashboard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dexdashboard.databinding.ActivityMainBinding
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

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

    sealed class ActionType {
        data class DirectLaunch(val className: String) : ActionType()
        object GestureDialog : ActionType()
    }

    data class SettingEntry(
        val title: String,
        val subtitle: String? = null,
        val actionType: ActionType
    )

    data class SettingsSection(val label: String, val entries: List<SettingEntry>)

    enum class BlockPosition { SINGLE, TOP, MIDDLE, BOTTOM }

    sealed class DashboardItem {
        data class SectionLabel(val title: String) : DashboardItem()
        data class BlockItem(
            val entry: SettingEntry,
            val blockPosition: BlockPosition,
            val showDivider: Boolean
        ) : DashboardItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = bars.top)
            binding.recyclerView.updatePadding(bottom = bars.bottom + dp(24))
            insets
        }

        setupList()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildFlatList(sections: List<SettingsSection>): List<DashboardItem> =
        buildList {
            for (section in sections) {
                add(DashboardItem.SectionLabel(section.label))
                val entries = section.entries
                entries.forEachIndexed { i, entry ->
                    val pos = when {
                        entries.size == 1 -> BlockPosition.SINGLE
                        i == 0            -> BlockPosition.TOP
                        i == entries.lastIndex -> BlockPosition.BOTTOM
                        else              -> BlockPosition.MIDDLE
                    }
                    add(DashboardItem.BlockItem(entry, pos, showDivider = i < entries.lastIndex))
                }
            }
        }

    private fun setupList() {
        val sections = listOf(
            SettingsSection("General", listOf(
                SettingEntry("Samsung DeX", "Start DeX mode", ActionType.DirectLaunch(DeXActivity.DEX_MODE)),
                SettingEntry("About DeX", "Introduction to Samsung DeX", ActionType.DirectLaunch(DeXActivity.DEX_ENTRY_SCREEN))
            )),
            SettingsSection("Input", listOf(
                SettingEntry("Keyboard", null, ActionType.DirectLaunch(DeXActivity.KEYBOARD)),
                SettingEntry("Mouse and trackpad", null, ActionType.DirectLaunch(DeXActivity.MOUSE)),
                SettingEntry("S Pen", null, ActionType.DirectLaunch(DeXActivity.SPEN)),
                SettingEntry("Touchpad gestures", null, ActionType.GestureDialog)
            )),
            SettingsSection("Accessibility", listOf(
                SettingEntry("Display style", null, ActionType.DirectLaunch(DeXActivity.TALKBACK_STYLE)),
                SettingEntry("Display position", null, ActionType.DirectLaunch(DeXActivity.TALKBACK_POSITION))
            ))
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = DashboardAdapter(buildFlatList(sections)) { entry ->
                when (val a = entry.actionType) {
                    is ActionType.DirectLaunch -> safeLaunchActivity(a.className)
                    is ActionType.GestureDialog -> showGestureDialog()
                }
            }
        }
    }

    private fun showGestureDialog() {
        val options = arrayOf(
            getString(R.string.gesture_touchpad),
            getString(R.string.gesture_advanced)
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.gesture_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> safeLaunchActivity(DeXActivity.GESTURE_CONTROLS)
                    1 -> safeLaunchActivity(DeXActivity.GESTURE_OPTIONS)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun safeLaunchActivity(className: String) {
        try {
            startActivity(Intent().apply {
                component = ComponentName(SETTINGS_PKG, className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.error_permission, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class DashboardAdapter(
        private val items: List<DashboardItem>,
        private val onItemClick: (SettingEntry) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) = when (items[position]) {
            is DashboardItem.SectionLabel -> 0
            is DashboardItem.BlockItem    -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                LabelViewHolder(inflater.inflate(R.layout.item_section_label, parent, false))
            } else {
                BlockViewHolder(inflater.inflate(R.layout.item_block_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is DashboardItem.SectionLabel -> (holder as LabelViewHolder).bind(item)
                is DashboardItem.BlockItem    -> (holder as BlockViewHolder).bind(item, onItemClick)
            }
        }

        override fun getItemCount() = items.size
    }

    class LabelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.textSectionLabel)
        fun bind(item: DashboardItem.SectionLabel) { label.text = item.title }
    }

    class BlockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.textTitle)
        private val subtitleText: TextView = view.findViewById(R.id.textSubtitle)
        private val divider: View = view.findViewById(R.id.divider)

        fun bind(item: DashboardItem.BlockItem, onClick: (SettingEntry) -> Unit) {
            titleText.text = item.entry.title
            subtitleText.text = item.entry.subtitle
            subtitleText.visibility = if (item.entry.subtitle != null) View.VISIBLE else View.GONE
            divider.visibility = if (item.showDivider) View.VISIBLE else View.GONE
            itemView.background = buildBlockBackground(itemView.context, item.blockPosition)
            itemView.setOnClickListener { onClick(item.entry) }
        }

        private fun buildBlockBackground(ctx: android.content.Context, pos: BlockPosition): Drawable {
            val corner = 16f * ctx.resources.displayMetrics.density
            val shape = ShapeAppearanceModel.builder().apply {
                when (pos) {
                    BlockPosition.SINGLE -> setAllCornerSizes(corner)
                    BlockPosition.TOP    -> { setTopLeftCornerSize(corner); setTopRightCornerSize(corner) }
                    BlockPosition.BOTTOM -> { setBottomLeftCornerSize(corner); setBottomRightCornerSize(corner) }
                    BlockPosition.MIDDLE -> {}
                }
            }.build()

            val tv = TypedValue()
            ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true)
            val bgColor = tv.data
            ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorControlHighlight, tv, true)
            val rippleColor = tv.data

            val bg   = MaterialShapeDrawable(shape).apply { fillColor = ColorStateList.valueOf(bgColor) }
            val mask = MaterialShapeDrawable(shape)
            return RippleDrawable(ColorStateList.valueOf(rippleColor), bg, mask)
        }
    }
}