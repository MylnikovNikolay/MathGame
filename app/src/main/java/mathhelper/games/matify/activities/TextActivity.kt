package mathhelper.games.matify.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import mathhelper.games.matify.LevelScene
import mathhelper.games.matify.PlayScene
import mathhelper.games.matify.R
import mathhelper.games.matify.TextScene
import mathhelper.games.matify.common.AndroidUtil
import mathhelper.games.matify.common.ThemeController
import mathhelper.games.matify.level.LevelResult
import mathhelper.games.matify.level.StateType

class TextActivity : AppCompatActivity() {
    private val TAG = "TextActivity"
    lateinit var mainView: ConstraintLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextScene.shared.textActivity = this
        AndroidUtil.setLanguage(this)
        setTheme(ThemeController.shared.currentTheme.resId)
        setContentView(R.layout.activity_text)

        val currentLevel = LevelScene.shared.currentLevel!!
        val newRes = LevelResult(.0, 0, StateType.DONE)
        if (newRes.isBetter(currentLevel.lastResult)) {
            LevelScene.shared.levelsActivity!!.updateResult(LevelResult(.0, 0, StateType.DONE))
        }
    }

    override fun onDestroy() {
        TextScene.shared.textActivity = null
        super.onDestroy()
    }

    override fun finish() {
        TextScene.shared.textActivity = null
        super.finish()
    }

    override fun onBackPressed() {
        finish()
    }

    fun back(v : View?) {
        finish()
    }
}