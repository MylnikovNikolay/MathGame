package mathhelper.games.matify.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eightbitlab.com.blurview.BlurView
import mathhelper.games.matify.GlobalScene
import mathhelper.games.matify.LevelScene
import mathhelper.games.matify.R
import mathhelper.games.matify.common.*
import mathhelper.games.matify.level.Level
import mathhelper.games.matify.level.LevelResult
import mathhelper.games.matify.level.StateType
import org.w3c.dom.Text
import kotlin.collections.ArrayList

class LevelsActivity: AppCompatActivity(), ConnectionListener {
    private val TAG = "LevelsActivity"
    private lateinit var levelViews: ArrayList<TextView>
    private lateinit var levelsList: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var divider: View
    private lateinit var offline: TextView
    lateinit var blurView: BlurView
    private val isLoading: Boolean
        get() = progress.visibility == View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        AndroidUtil.setLanguage(this)
        setTheme(ThemeController.shared.currentTheme.resId)
        setContentView(R.layout.activity_levels)
        progress = findViewById(R.id.progress)
        divider = findViewById(R.id.divider)
        offline = findViewById(R.id.offline)
        offline.visibility = View.GONE
        setLoading(true)
        if (Build.VERSION.SDK_INT < 24) {
            val settings = findViewById<TextView>(R.id.settings)
            settings.text = "\uD83D\uDD27"
        }
        blurView = findViewById(R.id.blurView)
        levelViews = ArrayList()
        levelsList = findViewById(R.id.levels_list)
        val title = findViewById<TextView>(R.id.levels)
        title.text = GlobalScene.shared.currentGame?.getNameByLanguage(resources.configuration.locale.language) ?: title.text
        initSwipeRefresher()
        LevelScene.shared.levelsActivity = this
        ConnectionChecker.shared.subscribe(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ConnectionChecker.shared.unsubscribe(this)
    }

    fun setLoading(flag: Boolean) {
        progress.visibility = if (flag) View.VISIBLE else View.INVISIBLE
        divider.visibility = if (flag) View.INVISIBLE else View.VISIBLE
    }

    private fun initSwipeRefresher() {
        val refresher = findViewById<SwipeRefreshLayout>(R.id.refresher)
        refresher.setOnRefreshListener {
            refresher.isRefreshing = false
            if (!isLoading && ConnectionChecker.shared.isConnected) {
                setLoading(true)
                Toast.makeText(this, R.string.refresh_taskset_message, Toast.LENGTH_LONG).show()
                LevelScene.shared.refreshGame()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (GlobalScene.shared.currentGame == null) {
            finishAffinity()
            startActivity(Intent(this, SplashActivity::class.java))
        }
    }

    override fun onConnectionChange(type: ConnectionChangeType) {
        runOnUiThread {
            if (type == ConnectionChangeType.ESTABLISHED) {
                offline.visibility = View.GONE
            } else {
                offline.visibility = View.VISIBLE
            }
        }
    }

    override fun connectionBannerClicked(v: View?) {
        ConnectionChecker.shared.connectionBannerClicked(this, blurView, ActivityType.LEVELS)
    }

    override fun connectionButtonClick(v: View) {
        ConnectionChecker.shared.connectionButtonClick(this, v)
    }

    fun onLevelsLoaded() {
        setLoading(false)
        generateList()
    }

    override fun onBackPressed() {
        if (!isLoading) {
            back(null)
        }
    }

    override fun finish() {
        LevelScene.shared.levelsActivity = null
        super.finish()
    }

    fun back(v: View?) {
        if (!isLoading) {
            LevelScene.shared.back()
        }
    }

    fun settings(v: View?) {
        if (!isLoading) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    fun updateResult(result: LevelResult?) {
        val i = LevelScene.shared.currentLevelIndex
        val currentLevel = LevelScene.shared.currentLevel
        val oldRes = currentLevel?.lastResult
        if (currentLevel?.isText() != true) {
            levelViews[i].text = "\uD83D\uDD8A️ ${currentLevel?.getNameByLanguage(resources.configuration.locale.language)}" +
                if (result != null) {
                    "\n${result}"
                } else {
                    ""
                }
        } else {
            levelViews[i].text = "\uD83D\uDCC4 ${currentLevel?.getNameByLanguage(resources.configuration.locale.language)}"
        }
        when (result?.state) {
            StateType.DONE -> {
                when (oldRes?.state) {
                    StateType.NOT_STARTED, null -> LevelScene.shared.levelsPassed += 1
                    StateType.PAUSED -> {
                        LevelScene.shared.levelsPassed += 1
                        LevelScene.shared.levelsPaused -= 1
                    }
                }
            }
            StateType.PAUSED -> {
                when (oldRes?.state) {
                    StateType.NOT_STARTED, null -> LevelScene.shared.levelsPaused += 1
                    StateType.DONE -> {
                        LevelScene.shared.levelsPaused += 1
                        LevelScene.shared.levelsPassed -= 1
                    }
                }
            }
            StateType.NOT_STARTED, null -> {
                when (oldRes?.state) {
                    StateType.DONE -> LevelScene.shared.levelsPassed -= 1
                    StateType.PAUSED -> LevelScene.shared.levelsPaused -= 1
                }
            }
        }
        AndroidUtil.setLeftDrawable(levelViews[i], AndroidUtil.getDrawableByLevelState(this, result?.state))
        LevelScene.shared.currentLevel?.save(this, result)
        LevelScene.shared.updateGameResult()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun generateList() {
        levelsList.removeAllViews()
        levelViews.clear()
        LevelScene.shared.levels.forEachIndexed { i, level ->
            val levelView = AndroidUtil.createButtonView(this)
            levelView.text = level.getNameByLanguage(resources.configuration.locale.language)
            if (level.isText()) {
                levelView.text = "\uD83D\uDCC4 ${levelView.text}"
            } else {
                if (level.lastResult != null) {
                    levelView.text = "${level.getNameByLanguage(resources.configuration.locale.language)}\n${level.lastResult!!}"
                }
                levelView.text = "\uD83D\uDD8A️ ${levelView.text}"
            }
            val themeName = Storage.shared.theme()
            levelView.setTextColor(ThemeController.shared.color(ColorName.TEXT_COLOR))
            levelView.background = getDrawable(R.drawable.button_rect)
            levelView.setOnClickListener {
                LevelScene.shared.currentLevelIndex = i
            }
            // TODO: make normal level info
            // levelView.isLongClickable = true
            // levelView.setOnLongClickListener { showInfo(level) }
            AndroidUtil.setLeftDrawable(levelView, AndroidUtil.getDrawableByLevelState(this, level.lastResult?.state))
            levelsList.addView(levelView)
            levelViews.add(levelView)
        }
    }

    private fun showInfo(lvl: Level): Boolean{
        val builder = AlertDialog.Builder(
            this, ThemeController.shared.alertDialogTheme
        )
        builder
            .setTitle("Level Info")
            .setMessage("Name: ${lvl.nameEn}")
            .setCancelable(true)
        val alert = builder.create()
        AndroidUtil.showDialog(alert, backMode = BackgroundMode.BLUR, blurView = blurView, activity = this)
        return true
    }

    private fun getBackgroundByDif(dif: Double): Drawable? {
        return when {
            dif < 3 -> getDrawable(R.drawable.level_easy)
            dif < 5 -> getDrawable(R.drawable.level_medium)
            dif < 9 -> getDrawable(R.drawable.level_hard)
            else -> getDrawable(R.drawable.level_insane)
        }
    }
}