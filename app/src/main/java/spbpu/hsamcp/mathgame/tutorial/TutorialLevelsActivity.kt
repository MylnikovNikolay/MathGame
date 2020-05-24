package spbpu.hsamcp.mathgame.tutorial

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import spbpu.hsamcp.mathgame.R
import spbpu.hsamcp.mathgame.TutorialScene
import spbpu.hsamcp.mathgame.common.AndroidUtil

class TutorialLevelsActivity: AppCompatActivity() {
    private val TAG = "TutorialLevelsActivity"
    private lateinit var pointer: TextView
    lateinit var dialog: AlertDialog
    lateinit var leave: AlertDialog
    lateinit var button: Button
    lateinit var progress: ProgressBar
    var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_activity_levels)
        pointer = findViewById(R.id.pointer_level)
        dialog = TutorialScene.shared.createTutorialDialog(this)
        leave = TutorialScene.shared.createLeaveDialog(this)
        button = findViewById(R.id.tutorial_level)
        button.visibility = View.GONE
        progress = findViewById(R.id.progress)
        loading = true
        TutorialScene.shared.tutorialLevelsActivity = this
    }

    override fun onBackPressed() {
        back(null)
    }

    override fun finish() {
        TutorialScene.shared.tutorialLevelsActivity = null
        super.finish()
    }

    fun back(v: View?) {
        if (!loading) {
            AndroidUtil.showDialog(leave)
        }
    }

    fun onLoad() {
        progress.visibility = View.GONE
        button.visibility = View.VISIBLE
    }

    fun startLevel(v: View?) {
        startActivity(Intent(this, TutorialPlayActivity::class.java))
    }

    fun tellAboutLevelLayout() {
        Log.d(TAG, "tellAboutLevelLayout")
        dialog.setMessage("Here you can choose level to play!\n\nGot it?")
        AndroidUtil.showDialog(dialog, false)
    }

    fun waitForLevelClick() {
        Log.d(TAG, "waitForLevelClick")
        pointer.visibility = View.VISIBLE
        TutorialScene.shared.animateLeftUp(pointer)
    }
}