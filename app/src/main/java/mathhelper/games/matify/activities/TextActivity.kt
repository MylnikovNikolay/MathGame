package mathhelper.games.matify.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import mathhelper.games.matify.R
import mathhelper.games.matify.TextScene
import mathhelper.games.matify.common.AndroidUtil

class TextActivity : AppCompatActivity() {
    private val TAG = "TextActivity"
    lateinit var mainView: ConstraintLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TextScene.shared.textActivity = this
        setViews()
    }

    private fun setViews() {

    }

    override fun onDestroy() {
        super.onDestroy()
        TextScene.shared.textActivity = null
    }
}