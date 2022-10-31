package mathhelper.games.matify.level

import android.util.Log
import mathhelper.twf.api.expressionToStructureString
import mathhelper.twf.expressiontree.ExpressionNode
import mathhelper.games.matify.LevelScene
import mathhelper.games.matify.common.Logger

data class State(var expression: ExpressionNode, var result: LevelResult)

class History {
    private val TAG = "History"
    var states = ArrayList<State>()

    val empty
        get() = states.isEmpty()
    val size
        get() = states.size

    fun saveState(steps: Double, time: Long, expression: ExpressionNode) {
        Logger.d(TAG, "saveState")
        val state = State(expression, LevelResult(steps, time, StateType.PAUSED, expressionToStructureString(expression)))
        states.add(state)
        LevelScene.shared.levelsActivity?.updateResult(state.result)
    }

    fun getPreviousStep(): State? {
        Logger.d(TAG, "getPreviousStep")
        if (states.size < 2) {
            LevelScene.shared.levelsActivity?.updateResult(null)
            return null
        }
        val res = states[states.size - 2]
        states.removeAt(states.size - 1)
        LevelScene.shared.levelsActivity?.updateResult(res.result)
        return res
    }

    fun clear() {
        Logger.d(TAG, "clear")
        states.clear()
    }
}