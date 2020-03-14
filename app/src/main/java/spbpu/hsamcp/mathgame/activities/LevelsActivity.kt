package spbpu.hsamcp.mathgame.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import spbpu.hsamcp.mathgame.level.Level
import spbpu.hsamcp.mathgame.MathScene
import spbpu.hsamcp.mathgame.R
import spbpu.hsamcp.mathgame.common.AndroidUtil
import spbpu.hsamcp.mathgame.common.Constants
import java.lang.ref.WeakReference

class LevelsActivity: AppCompatActivity() {
    private val TAG = "LevelsActivity"
    private lateinit var levels: ArrayList<Level>
    private lateinit var levelViews: ArrayList<TextView>
    private lateinit var levelsList: LinearLayout
    private var currentLevelIndex = -1
    private var levelTouched: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_levels)
        MathScene.levelsActivity = WeakReference(this)
        val levelNames = assets.list("")!!
            .filter { """level\d+.json""".toRegex().matches(it) }
        levels = ArrayList()
        for (name in levelNames) {
            val loadedLevel = Level.create(name, this)
            if (loadedLevel != null) {
                levels.add(loadedLevel)
            }
        }
        levels.sortBy { it.difficulty }
        window.decorView.setOnSystemUiVisibilityChangeListener { v: Int ->
            if ((v and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                AndroidUtil.makeFullScreen(window)
            }
        }
        levelViews = ArrayList()
        levelsList = findViewById(R.id.levels_list)
        generateList()
    }

    override fun onResume() {
        super.onResume()
        AndroidUtil.makeFullScreen(window)
    }

    fun reset(v: View?) {
        resetAlert()
    }

    fun getNextLevel(): Level {
        if (currentLevelIndex + 1 == levels.size) {
            return levels[currentLevelIndex]
        }
        return levels[++currentLevelIndex]
    }

    fun getPrevLevel(): Level {
        if (currentLevelIndex == 0) {
            return levels[0]
        }
        return levels[--currentLevelIndex]
    }

    fun updateResult() {
        levelViews[currentLevelIndex].text = levels[currentLevelIndex].name + "\n" +
            levels[currentLevelIndex].lastResult!!.award.value.str
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun generateList() {
        levels.forEachIndexed { i, level ->
            val levelView = createLevelView()
            levelView.text = level.name
            if (level.lastResult != null) {
                levelView.text = level.name + "\n" + level.lastResult!!.award.value.str
            }
            levelView.setOnTouchListener { v, event ->
                super.onTouchEvent(event)
                when {
                    event.action == MotionEvent.ACTION_DOWN && levelTouched == null -> {
                        levelTouched = v
                        v.background = getDrawable(R.drawable.rect_shape_clicked)
                    }
                    event.action == MotionEvent.ACTION_UP && levelTouched == v -> {
                        v.background = getDrawable(R.drawable.rect_shape)
                        if (AndroidUtil.touchUpInsideView(v, event)) {
                            MathScene.currentLevel = level
                            currentLevelIndex = i
                            startActivity(Intent(this, PlayActivity::class.java))
                        }
                        levelTouched = null
                    }
                }
                true
            }
            levelsList.addView(levelView)
            levelViews.add(levelView)
        }
    }

    private fun createLevelView(): TextView {
        val levelView = TextView(this)
        levelView.typeface = Typeface.MONOSPACE
        levelView.textSize = Constants.levelDefaultSize
        levelView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        levelView.setLineSpacing(0f, Constants.levelLineSpacing)
        levelView.setPadding(
            Constants.defaultPadding, Constants.defaultPadding * 2,
            Constants.defaultPadding, Constants.defaultPadding * 2)
        val layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(0, Constants.defaultPadding, 0, Constants.defaultPadding)
        levelView.layoutParams = layoutParams
        levelView.background = getDrawable(R.drawable.rect_shape)
        levelView.setTextColor(Constants.textColor)
        return levelView
    }

    private fun resetAlert() {
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder
            .setTitle("ARE YOU SURE?")
            .setMessage("This action will reset all your achievements")
            .setPositiveButton("Yes \uD83D\uDE22") { dialog: DialogInterface, id: Int ->
                val prefs = getSharedPreferences(Constants.storage, Context.MODE_PRIVATE)
                val prefEdit = prefs.edit()
                prefEdit.clear()
                prefEdit.commit()
                recreate()
            }
            .setNegativeButton("Cancel ☺") { dialog: DialogInterface, id: Int ->
            }
            .setCancelable(true)
        val dialog = builder.create()
        dialog.show()
        AndroidUtil.makeFullScreen(dialog.window!!)
        dialog.window!!.setBackgroundDrawableResource(R.color.gray)
        dialog.window!!.findViewById<TextView>(android.R.id.message).typeface = Typeface.MONOSPACE
    }
}