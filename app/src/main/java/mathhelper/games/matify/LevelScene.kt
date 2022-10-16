package mathhelper.games.matify

import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mathhelper.games.matify.activities.GamesActivity
import mathhelper.games.matify.activities.LevelsActivity
import mathhelper.games.matify.activities.PlayActivity
import mathhelper.games.matify.activities.TextActivity
import mathhelper.games.matify.common.ConnectionChecker
import mathhelper.games.matify.common.Logger
import mathhelper.games.matify.game.GameResult
import mathhelper.games.matify.level.*
import java.util.*

class LevelScene {
    companion object {
        private const val TAG = "LevelScene"
        val shared: LevelScene = LevelScene()
    }

    var levels: ArrayList<Level> = ArrayList()
    var levelsActivity: LevelsActivity? = null
        set(value) {
            field = value
            if (value != null && GlobalScene.shared.currentGame != null) {
                GlobalScene.shared.requestGameForPlay(GlobalScene.shared.currentGame!!, success = {
                    levels = GlobalScene.shared.currentGame!!.levels
                    levelsPassed = GlobalScene.shared.currentGame!!.lastResult?.levelsPassed ?: 0
                    levelsPaused = GlobalScene.shared.currentGame!!.lastResult?.levelsPaused ?: 0
                    value.onLevelsLoaded()
                }, error = {
                    value.setLoading(false)
                    Logger.e(TAG, "Error while LevelsActivity initializing")
                    value.finish()
                })
            }
        }
    var currentLevel: Level? = null
        private set
    var currentLevelIndex = 0
        set(value) {
            when {
                value >= 0 && value < levels.size -> {
                    field = value
                    currentLevel = levels[value]

                    if (currentLevel!!.descriptionShortEn.startsWith("text")) {
                        Handler().postDelayed({
                            if (TextScene.shared.textActivity == null) {
                                levelsActivity?.startActivity(Intent(levelsActivity, TextActivity::class.java))
                            } else {
                                //TextScene.shared.textActivity!!.startCreatingLevelUI()
                            }
                        }, 100)
                    } else {
                        Handler().postDelayed({
                            if (PlayScene.shared.playActivity == null) {
                                levelsActivity?.startActivity(Intent(levelsActivity, PlayActivity::class.java))
                            } else {
                                PlayScene.shared.playActivity!!.startCreatingLevelUI()
                            }
                        }, 100)
                    }
                }
                value < 0 -> {
                }
                value >= levels.size -> {
                }
            }
        }
    var levelsPassed: Int = 0
    var levelsPaused: Int = 0

    fun wasLevelPaused(): Boolean {
        return currentLevel?.endless == true && currentLevel?.lastResult?.state == StateType.PAUSED
    }

    fun nextLevel(): Boolean {
        if (currentLevelIndex + 1 == levels.size) {
            return false
        }
        currentLevelIndex++
        return true
    }

    fun updateGameResult() {
        val newRes = GameResult(levelsPassed, levelsPaused)
        if (GlobalScene.shared.currentGame?.lastResult != newRes) {
            GlobalScene.shared.gamesActivity?.updateResult(newRes)
        }
    }

    fun back() {
        GlobalScene.shared.currentGame?.rulePacks?.clear()
        GlobalScene.shared.currentGame?.levels?.clear()
        GlobalScene.shared.currentGame?.loaded = false
        levelsActivity?.finish()
    }

    fun refreshGame() {
        if (!ConnectionChecker.shared.isConnected) return
        val activity = levelsActivity ?: return
        GlobalScene.shared.requestGameForPlay(GlobalScene.shared.currentGame!!, forceRefresh = true, success = {
            levels = GlobalScene.shared.currentGame!!.levels
            levelsPassed = GlobalScene.shared.currentGame!!.lastResult?.levelsPassed ?: 0
            levelsPaused = GlobalScene.shared.currentGame!!.lastResult?.levelsPaused ?: 0
            activity.onLevelsLoaded()
        }, error = {
            activity.setLoading(false)
            Logger.e(TAG, "Error while LevelsActivity initializing")
            activity.finish()
        })
    }
}