package com.example.onepass.presentation.activity

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.onepass.R
import com.example.onepass.core.config.GlobalScaleManager
import com.example.onepass.service.BundledSpeechSupport
import com.example.onepass.service.SpeechEngineMode

class SettingsActivity : AppCompatActivity() {

    private val requestHomeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateDefaultLauncherButtonState()
        if (isAppDefaultLauncher()) {
            Toast.makeText(this, "已设为默认桌面", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var speechSupport: BundledSpeechSupport

    private lateinit var radioLunar: RadioButton
    private lateinit var radioSolar: RadioButton
    private lateinit var dateStyleGroup: RadioGroup

    private lateinit var radioSpeechAuto: RadioButton
    private lateinit var radioSpeechSystem: RadioButton
    private lateinit var radioSpeechBundled: RadioButton
    private lateinit var speechEngineGroup: RadioGroup
    private lateinit var textCurrentSpeechEngine: TextView
    private lateinit var textBundledSpeechModel: TextView

    private lateinit var seekBarIconSize: SeekBar
    private lateinit var textIconSize: TextView
    private lateinit var btnSetDefaultLauncher: Button
    private lateinit var btnClearDefaultLauncher: Button

    private lateinit var btnCommonApps: Button
    private lateinit var switchWeather: Switch
    private lateinit var seekBarWeatherVol: SeekBar
    private lateinit var textWeatherVol: TextView
    private lateinit var seekBarSpeechRate: SeekBar
    private lateinit var textSpeechRate: TextView
    private lateinit var commonAppsScrollView: HorizontalScrollView
    private lateinit var commonAppsContainer: LinearLayout
    private lateinit var textNoCommonApps: TextView
    private lateinit var btnContacts: Button

    private lateinit var textDateStyle: TextView
    private lateinit var textCommonAppsTitle: TextView
    private lateinit var textContactsTitle: TextView
    private lateinit var textIconSizeTitle: TextView
    private lateinit var textWeatherTitle: TextView
    private lateinit var textSpeechRateTitle: TextView
    private lateinit var textSpeechEngineTitle: TextView

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        speechSupport = BundledSpeechSupport(this)

        initViews()
        loadSettings()
        setupListeners()
        updateDefaultLauncherButtonState()
        refreshSpeechStatus()
    }

    override fun onResume() {
        super.onResume()

        val commonAppsSet = getSharedPreferences(COMMON_APPS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_COMMON_APPS, HashSet<String>())
        loadCommonApps(commonAppsSet)
        updateDefaultLauncherButtonState()
        refreshSpeechStatus()

        val scalePercentage = GlobalScaleManager.getScalePercentage(this)
        applyScaleEffects(scalePercentage)
    }

    private fun initViews() {
        radioLunar = findViewById(R.id.radioLunar)
        radioSolar = findViewById(R.id.radioSolar)
        dateStyleGroup = findViewById(R.id.dateStyleGroup)

        radioSpeechAuto = findViewById(R.id.radioSpeechAuto)
        radioSpeechSystem = findViewById(R.id.radioSpeechSystem)
        radioSpeechBundled = findViewById(R.id.radioSpeechBundled)
        speechEngineGroup = findViewById(R.id.speechEngineGroup)
        textCurrentSpeechEngine = findViewById(R.id.textCurrentSpeechEngine)
        textBundledSpeechModel = findViewById(R.id.textBundledSpeechModel)

        seekBarIconSize = findViewById(R.id.seekBarIconSize)
        textIconSize = findViewById(R.id.textIconSize)
        btnSetDefaultLauncher = findViewById(R.id.btnSetDefaultLauncher)
        btnClearDefaultLauncher = findViewById(R.id.btnClearDefaultLauncher)

        btnCommonApps = findViewById(R.id.btnCommonApps)
        switchWeather = findViewById(R.id.switchWeather)
        seekBarWeatherVol = findViewById(R.id.seekBarWeatherVol)
        textWeatherVol = findViewById(R.id.textWeatherVol)
        seekBarSpeechRate = findViewById(R.id.seekBarSpeechRate)
        textSpeechRate = findViewById(R.id.textSpeechRate)
        commonAppsScrollView = findViewById(R.id.commonAppsScrollView)
        commonAppsContainer = findViewById(R.id.commonAppsContainer)
        textNoCommonApps = findViewById(R.id.textNoCommonApps)
        btnContacts = findViewById(R.id.btnContacts)

        textDateStyle = findViewById(R.id.textDateStyle)
        textCommonAppsTitle = findViewById(R.id.textCommonAppsTitle)
        textContactsTitle = findViewById(R.id.textContactsTitle)
        textIconSizeTitle = findViewById(R.id.textIconSizeTitle)
        textWeatherTitle = findViewById(R.id.textWeatherTitle)
        textSpeechRateTitle = findViewById(R.id.textSpeechRateTitle)
        textSpeechEngineTitle = findViewById(R.id.textSpeechEngineTitle)

        seekBarIconSize.min = 60
        seekBarIconSize.max = 100
    }

    private fun loadSettings() {
        val dateStyle = prefs.getString(KEY_DATE_STYLE, VALUE_SOLAR)
        if (dateStyle == VALUE_LUNAR) {
            radioLunar.isChecked = true
        } else {
            radioSolar.isChecked = true
        }

        when (speechSupport.getMode()) {
            SpeechEngineMode.AUTO -> radioSpeechAuto.isChecked = true
            SpeechEngineMode.SYSTEM -> radioSpeechSystem.isChecked = true
            SpeechEngineMode.BUNDLED_MATCHA -> radioSpeechBundled.isChecked = true
        }

        val weatherEnabled = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
        switchWeather.isChecked = weatherEnabled

        val weatherVolume = prefs.getInt(KEY_WEATHER_VOLUME, 50)
        seekBarWeatherVol.progress = weatherVolume
        textWeatherVol.text = "$weatherVolume%"
        seekBarWeatherVol.isEnabled = weatherEnabled

        val speechRateProgress = prefs.getInt(KEY_SPEECH_RATE, 50)
        seekBarSpeechRate.progress = speechRateProgress
        textSpeechRate.text = String.format("%.1fx", 0.5f + (speechRateProgress / 50f))

        val scalePercentage = GlobalScaleManager.getScalePercentage(this)
        seekBarIconSize.progress = scalePercentage
        textIconSize.text = "$scalePercentage%"
        applyScaleEffects(scalePercentage)

        val commonAppsSet = getSharedPreferences(COMMON_APPS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_COMMON_APPS, HashSet<String>())
        loadCommonApps(commonAppsSet)
    }

    private fun setupListeners() {
        dateStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val isLunar = checkedId == R.id.radioLunar
            prefs.edit()
                .putString(KEY_DATE_STYLE, if (isLunar) VALUE_LUNAR else VALUE_SOLAR)
                .apply()
            Toast.makeText(
                this,
                if (isLunar) "已切换到农历显示" else "已切换到阳历显示",
                Toast.LENGTH_SHORT
            ).show()
        }

        speechEngineGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioSpeechSystem -> SpeechEngineMode.SYSTEM
                R.id.radioSpeechBundled -> SpeechEngineMode.BUNDLED_MATCHA
                else -> SpeechEngineMode.AUTO
            }
            speechSupport.setMode(mode)
            speechSupport.syncResolvedEngineLabel()
            refreshSpeechStatus()
            Toast.makeText(
                this,
                when (mode) {
                    SpeechEngineMode.AUTO -> "已切换到自动语音引擎"
                    SpeechEngineMode.SYSTEM -> "已切换到系统 TTS"
                    SpeechEngineMode.BUNDLED_MATCHA -> "已切换到内置 Matcha"
                },
                Toast.LENGTH_SHORT
            ).show()
        }

        seekBarIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(60, 100)
                textIconSize.text = "$clamped%"
                applyScaleEffects(clamped)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val clamped = seekBarIconSize.progress.coerceIn(60, 100)
                GlobalScaleManager.setScalePercentage(this@SettingsActivity, clamped)
                Toast.makeText(
                    this@SettingsActivity,
                    "图标大小已调整为 $clamped%",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        btnSetDefaultLauncher.setOnClickListener { setAsDefaultLauncher() }
        btnClearDefaultLauncher.setOnClickListener { clearDefaultLauncher() }

        btnCommonApps.setOnClickListener {
            startActivity(Intent(this, CommonAppsActivity::class.java))
        }

        btnContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        switchWeather.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WEATHER_ENABLED, isChecked).apply()
            seekBarWeatherVol.isEnabled = isChecked
            Toast.makeText(
                this,
                if (isChecked) "已开启天气播报" else "已关闭天气播报",
                Toast.LENGTH_SHORT
            ).show()
        }

        seekBarWeatherVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textWeatherVol.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_WEATHER_VOLUME, seekBarWeatherVol.progress).apply()
                Toast.makeText(
                    this@SettingsActivity,
                    "天气播报音量已调整为 ${seekBarWeatherVol.progress}%",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        seekBarSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress / 50f)
                textSpeechRate.text = String.format("%.1fx", rate)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_SPEECH_RATE, seekBarSpeechRate.progress).apply()
                val rate = 0.5f + (seekBarSpeechRate.progress / 50f)
                Toast.makeText(
                    this@SettingsActivity,
                    "语速已调整为 ${String.format("%.1fx", rate)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun refreshSpeechStatus() {
        val bundledStatus = speechSupport.getBundledVoiceStatus()
        val bundledModelLabel = if (bundledStatus.isInstalled) {
            "内置语音模型：${speechSupport.getPreferredVoiceDisplayName()}"
        } else {
            "内置语音模型：未检测到（缺少 ${bundledStatus.missingAssets.joinToString("、")}）"
        }

        textCurrentSpeechEngine.text = "当前实际引擎：${speechSupport.getResolvedEngineLabel()}"
        textBundledSpeechModel.text = bundledModelLabel

        radioSpeechBundled.isEnabled = bundledStatus.isInstalled
        if (!bundledStatus.isInstalled && speechSupport.getMode() == SpeechEngineMode.BUNDLED_MATCHA) {
            speechSupport.setMode(SpeechEngineMode.AUTO)
            speechSupport.syncResolvedEngineLabel()
            radioSpeechAuto.isChecked = true
        }
    }

    private fun setAsDefaultLauncher() {
        if (isAppDefaultLauncher()) {
            Toast.makeText(this, "当前已是默认桌面", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    requestHomeRoleLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    )
                    return
                }
            }

            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Toast.makeText(this, "请在系统设置中选择 MoreTalk 作为默认桌面", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开桌面设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearDefaultLauncher() {
        if (!isAppDefaultLauncher()) {
            Toast.makeText(this, "当前未设为默认桌面", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            @Suppress("DEPRECATION")
            packageManager.clearPackagePreferredActivities(packageName)
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Toast.makeText(this, "请改选其他桌面应用以取消默认桌面", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开桌面设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDefaultLauncherButtonState() {
        val isDefaultLauncher = isAppDefaultLauncher()
        btnSetDefaultLauncher.isEnabled = !isDefaultLauncher
        btnSetDefaultLauncher.alpha = if (isDefaultLauncher) 0.6f else 1f
        btnSetDefaultLauncher.text = if (isDefaultLauncher) {
            "已设为默认桌面"
        } else {
            "设为默认桌面"
        }

        btnClearDefaultLauncher.isEnabled = isDefaultLauncher
        btnClearDefaultLauncher.alpha = if (isDefaultLauncher) 1f else 0.6f
        btnClearDefaultLauncher.text = "取消默认桌面"
    }

    private fun isAppDefaultLauncher(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return false

        return resolveInfo.activityInfo?.packageName == packageName
    }

    private fun loadCommonApps(commonAppsSet: Set<String>?) {
        commonAppsContainer.removeAllViews()

        if (commonAppsSet.isNullOrEmpty()) {
            commonAppsScrollView.visibility = View.GONE
            textNoCommonApps.visibility = View.VISIBLE
            return
        }

        commonAppsScrollView.visibility = View.VISIBLE
        textNoCommonApps.visibility = View.GONE

        val savedOrders = getSharedPreferences(COMMON_APPS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_ORDERS, null)
        val appOrders = if (savedOrders != null) {
            runCatching { parseAppOrders(savedOrders) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val sortedApps = commonAppsSet.sortedWith(
            compareBy<String> { appOrders[it] ?: Int.MAX_VALUE }
        )

        for (packageName in sortedApps) {
            runCatching {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val appIcon = packageInfo.applicationInfo?.loadIcon(packageManager) ?: return@runCatching
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(16, 0, 16, 0)
                    }
                }

                val iconView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(
                        GlobalScaleManager.getScaledValue(this@SettingsActivity, 120),
                        GlobalScaleManager.getScaledValue(this@SettingsActivity, 120)
                    )
                }

                item.addView(iconView)
                item.setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this, "无法打开该应用", Toast.LENGTH_SHORT).show()
                    }
                }
                commonAppsContainer.addView(item)
            }
        }
    }

    private fun parseAppOrders(ordersString: String): Map<String, Int> {
        val orders = mutableMapOf<String, Int>()
        for (pair in ordersString.split(",")) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                parts[1].toIntOrNull()?.let { order ->
                    orders[parts[0]] = order
                }
            }
        }
        return orders
    }

    private fun applyScaleEffects(scalePercentage: Int) {
        val scaledTitleSize = GlobalScaleManager.getScaledValue(this, 27f)
        val scaledOptionSize = GlobalScaleManager.getScaledValue(this, 24f)
        val scaledButtonSize = GlobalScaleManager.getScaledValue(this, 20f)

        textDateStyle.textSize = scaledTitleSize
        textCommonAppsTitle.textSize = scaledTitleSize
        textContactsTitle.textSize = scaledTitleSize
        textIconSizeTitle.textSize = scaledTitleSize
        textWeatherTitle.textSize = scaledTitleSize
        textSpeechEngineTitle.textSize = scaledTitleSize
        textSpeechRateTitle.textSize = scaledTitleSize

        radioLunar.textSize = scaledOptionSize
        radioSolar.textSize = scaledOptionSize
        radioSpeechAuto.textSize = scaledOptionSize
        radioSpeechSystem.textSize = scaledOptionSize
        radioSpeechBundled.textSize = scaledOptionSize
        textIconSize.textSize = scaledOptionSize
        textWeatherVol.textSize = scaledOptionSize
        textCurrentSpeechEngine.textSize = scaledOptionSize
        textBundledSpeechModel.textSize = scaledOptionSize
        textNoCommonApps.textSize = scaledOptionSize

        btnSetDefaultLauncher.textSize = scaledOptionSize
        btnClearDefaultLauncher.textSize = scaledOptionSize
        btnCommonApps.textSize = scaledButtonSize
        btnContacts.textSize = scaledButtonSize

        // 语速显示固定大小，不随图标缩放变化。
        textSpeechRate.textSize = 24f
    }

    companion object {
        private const val PREFS_NAME = "OnePassPrefs"
        private const val COMMON_APPS_PREFS = "common_apps_prefs"
        private const val KEY_DATE_STYLE = "date_style"
        private const val VALUE_LUNAR = "lunar"
        private const val VALUE_SOLAR = "solar"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_WEATHER_VOLUME = "weather_volume"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_COMMON_APPS = "common_apps"
        private const val KEY_APP_ORDERS = "app_orders"
    }
}
