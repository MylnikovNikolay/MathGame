package mathhelper.games.matify

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.text.Html
import android.text.SpannedString
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import mathhelper.twf.api.expressionToStructureString
import com.google.android.material.bottomsheet.BottomSheetBehavior
import mathhelper.twf.expressiontree.ExpressionNode
import mathhelper.twf.expressiontree.ExpressionSubstitution
import kotlinx.android.synthetic.main.activity_play.*
import mathhelper.games.matify.activities.PlayActivity
import mathhelper.games.matify.common.*
import mathhelper.games.matify.level.*
import mathhelper.games.matify.mathResolver.MathResolver
import mathhelper.games.matify.mathResolver.TaskType
import mathhelper.games.matify.statistics.Statistics

class PlayScene {
    companion object {
        private const val TAG = "PlayScene"
        const val messageTime: Long = 2000
        val shared: PlayScene = PlayScene()
    }

    var playActivity: PlayActivity? = null
        set(value) {
            field = value
            if (value != null) {
                //tutorialProcessing = false
                history = History()
            }
        }
    /** GAME STATE */
    var instrumetProcessing: Boolean = false
    var currentRuleView: RuleMathView? = null
    fun setCurrentRuleView(context: Context, value: RuleMathView?) {
        currentRuleView = value
        if (value != null) {
            Handler().postDelayed({
                try {
                    onRuleClicked(context)
                } catch (e: java.lang.Exception) {
                    Logger.e(TAG, "Error during rule usage: ${e.message}")
                    Toast.makeText(playActivity, R.string.misclick_happened_please_retry, Toast.LENGTH_LONG).show()
                }
            }, 100)
        }
    }
    var stepsCount: Double = 0.0
    var currentTime: Long = 0
    private lateinit var history: History
    /** TIMERS */
    private val messageTimer = MessageTimer()
    var downTimer: MathDownTimer? = null
        private set
    var upTimer: MathUpTimer? = null
        private set

    private fun onRuleClicked(context: Context) {
        Logger.d(TAG, "onRuleClicked")
        if (GlobalScene.shared.tutorialProcessing) {
            TutorialScene.shared.onRuleClicked(currentRuleView!!)
            return
        }
        if (playActivity == null) {
            return
        }
        val activity = playActivity!!
        val prev = activity.globalMathView.expression!!.clone()
        val places: List<ExpressionNode> = activity.globalMathView.currentAtoms.toList()
        val oldSteps = stepsCount
        var levelPassed = false
        if (currentRuleView!!.subst != null) {
            val res = activity.globalMathView.performSubstitutionForMultiselect(currentRuleView!!.subst!!)
            if (res != null) {
                stepsCount++
                history.saveState(stepsCount, currentTime, activity.globalMathView.expression!!)
                activity.previous.isEnabled = true
                if (LevelScene.shared.currentLevel!!.checkEnd(res)) {
                    levelPassed = true

                    Statistics.logRule(
                        oldSteps,
                        stepsCount,
                        prev,
                        activity.globalMathView.expression!!,
                        currentRuleView!!.subst,
                        places
                    )

                    onWin(context)
                }
                clearRules()
                activity.globalMathView.currentRulesToResult = null
            } else {
                showMessage(activity.getString(R.string.wrong_subs))
            }

        }
        if (!levelPassed) {
            Statistics.logRule(oldSteps, stepsCount, prev, activity.globalMathView.expression!!,
                currentRuleView!!.subst, places)
        }
    }

    fun onAtomClicked() {
        Logger.d(TAG, "onAtomClicked")
        if (GlobalScene.shared.tutorialProcessing) {
            TutorialScene.shared.onAtomClicked()
            return
        }
        val activity = playActivity!!
        if (instrumetProcessing && InstrumentScene.shared.currentProcessingInstrument?.type != InstrumentType.MULTI) {
            InstrumentScene.shared.choosenAtom(activity.globalMathView.currentAtoms)
        } else if (activity.globalMathView.currentAtoms.isNotEmpty()) {
            if (activity.globalMathView.multiselectionMode) {
                activity.previous.isEnabled = true
            }
            val substitutionApplication = LevelScene.shared.currentLevel!!.getSubstitutionApplication(
                activity.globalMathView.currentAtoms,
                activity.globalMathView.expression!!
            )
            if (substitutionApplication == null) {
                showMessage(activity.getString(R.string.no_rules))
                clearRules()
                if (!activity.globalMathView.multiselectionMode) {
                    activity.globalMathView.clearExpression()
                }
            } else {
                val rules =
                    LevelScene.shared.currentLevel!!.getRulesFromSubstitutionApplication(substitutionApplication)
                activity.globalMathView.currentRulesToResult =
                    LevelScene.shared.currentLevel!!.getResultFromSubstitutionApplication(substitutionApplication)
                activity.rulesScrollView.visibility = View.VISIBLE
                redrawRules(rules)
            }
        } else {
            activity.previous.isEnabled = (history.size > 1)
        }
        Statistics.logPlace(stepsCount, activity.globalMathView.expression!!, activity.globalMathView.currentAtoms)
    }

    fun loadLevel(context: Context, continueGame: Boolean, languageCode: String): Boolean {
        Logger.d(TAG, "loadLevel")
        val currentLevel = LevelScene.shared.currentLevel!!
        val activity = playActivity!!
        clearRules()
        cancelTimers()
        val text = activity.getString(R.string.end_expression_opened, currentLevel.getDescriptionByLanguage(languageCode))
        activity.endExpressionViewLabel.text = Html.fromHtml(
            String.format(
                Html.toHtml(SpannedString(activity.getText(R.string.end_expression_opened))),
                currentLevel.getDescriptionByLanguage(languageCode)
            )
        )
        activity.endExpressionViewLabel.visibility = View.VISIBLE
        activity.endExpressionMathView.visibility = View.GONE
        if (!currentLevel.goalExpressionStructureString.isNullOrBlank()) {
            activity.endExpressionMathView.setExpression(currentLevel.goalExpressionStructureString!!, null)
            activity.endExpressionMathView.visibility = View.VISIBLE
        }
        if (currentLevel.endless) {
            loadEndless(context, continueGame)
        } else {
            loadFinite()
        }
        history.clear()
        history.saveState(stepsCount, currentTime, activity.globalMathView.expression!!)
        showMessage("\uD83C\uDF40 ${currentLevel.getNameByLanguage(languageCode)} \uD83C\uDF40")
        Statistics.setStartTime()
        Statistics.logStart()
        return true
    }

    private fun loadFinite() {
        val currentLevel = LevelScene.shared.currentLevel!!
        playActivity!!.globalMathView.setExpression(currentLevel.startExpression.clone(), currentLevel.subjectType)
        playActivity!!.globalMathView.center()
        stepsCount = 0.0
        currentTime = 0
        downTimer = MathDownTimer(currentLevel.time, 1)
        downTimer!!.start()
    }

    private fun loadEndless(context: Context, continueGame: Boolean) {
        val activity = playActivity!!
        val currentLevel = LevelScene.shared.currentLevel!!
        if (continueGame && currentLevel.lastResult != null &&
            currentLevel.lastResult!!.state == StateType.PAUSED) {
            stepsCount = currentLevel.lastResult!!.steps
            currentTime = currentLevel.lastResult!!.time
            activity.globalMathView.setExpression(currentLevel.lastResult!!.expression, currentLevel.subjectType)
            activity.globalMathView.center()
        } else {
            LevelScene.shared.levelsActivity?.updateResult(null)
            activity.globalMathView.setExpression(currentLevel.startExpression.clone(), currentLevel.subjectType)
            activity.globalMathView.center()
            stepsCount = 0.0
            currentTime = 0
        }
        upTimer = MathUpTimer(1)
        upTimer!!.start(context)
    }

    fun previousStep() {
        Logger.d(TAG, "previousStep")
        if (instrumetProcessing) {
            InstrumentScene.shared.turnOffCurrentInstrument(playActivity!!)
        } else {
            val state = history.getPreviousStep()
            val activity = playActivity!!
            val oldExpression = activity.globalMathView.expression!!
            val oldSteps = stepsCount
            if (state != null) {
                clearRules()
                val currentLevel = LevelScene.shared.currentLevel!!
                activity.globalMathView.setExpression(state.expression, currentLevel.subjectType, false)
                //val penalty = UndoPolicyHandler.getPenalty(currentLevel.undoPolicy, state.depth)
                //stepsCount = stepsCount - 1 + penalty
                if (history.size < 2) {
                    activity.previous.isEnabled = false
                }
            }
            Statistics.logUndo(
                oldSteps, stepsCount, oldExpression,
                activity.globalMathView.expression!!, activity.globalMathView.currentAtoms
            )
        }
    }

    fun setMultiselectionMode(multi: Boolean) {
        playActivity?.previous?.isEnabled = !history.empty
        if (multi) {
            playActivity?.globalMathView?.multiselectionMode = true
            playActivity?.globalMathView?.recolorCurrentAtom(ThemeController.shared.color(ColorName.MULTISELECTION_COLOR))
        } else {
            clearRules()
            playActivity?.globalMathView?.clearExpression()
            playActivity?.globalMathView?.multiselectionMode = false
        }
    }

    fun restart(context: Context, languageCode: String) {
        Logger.d(TAG, "restart")
        val activity = playActivity!!
        Statistics.logRestart(stepsCount, activity.globalMathView.expression!!, activity.globalMathView.currentAtoms)
        loadLevel(context,false, languageCode)
    }

    fun menu(logAndSave: Boolean = true) {
        Logger.d(TAG, "menu")
        val activity = playActivity!!
        setMultiselectionMode(false)
        if (logAndSave) { // TODO: && stepsCount > 0 (server is now saving state even if stepsCount == 0)
            history.saveState(stepsCount, currentTime, activity.globalMathView.expression!!)
            Statistics.logInterim(stepsCount, activity.globalMathView.expression!!)
        }
        Statistics.logMenu(stepsCount, activity.globalMathView.expression!!, activity.globalMathView.currentAtoms)
    }

    fun info(languageCode: String) {
        val currentLevel = LevelScene.shared.currentLevel!!
        val multi = playActivity!!.globalMathView.multiselectionMode
        val builder = AlertDialog.Builder(
            playActivity, ThemeController.shared.alertDialogTheme
        )
        val v = playActivity!!.layoutInflater.inflate(R.layout.level_info, null)
        v.findViewById<TextView>(R.id.game)?.text = currentLevel.game.getNameByLanguage(languageCode)
        v.findViewById<TextView>(R.id.name)?.text = currentLevel.getNameByLanguage(languageCode)
        v.findViewById<TextView>(R.id.description)?.text = currentLevel.getDescriptionByLanguage(languageCode)
        if (!currentLevel.goalExpressionStructureString.isNullOrBlank()) {
            v.findViewById<SimpleMathView>(R.id.info_end_math_view).setExpression(currentLevel.goalExpressionStructureString!!, null)
            v.findViewById<View>(R.id.info_end_math_view_row).visibility = View.VISIBLE
        }
        v.findViewById<TextView>(R.id.steps)?.text = stepsCount.toInt().toString()
        v.findViewById<TextView>(R.id.mode)?.text = if (multi) playActivity!!.getString(R.string.multiselection_mode_is_on)
        else playActivity!!.getString(R.string.multiselection_mode_is_off)
        builder.setView(v)
        val alert = builder.create()
        AndroidUtil.showDialog(alert, bottomGravity = false, backMode = BackgroundMode.BLUR,
            blurView = playActivity!!.blurView, activity = playActivity!!)
    }

    fun clearRules() {
        val activity = playActivity!!
        activity.rulesScrollView.visibility = View.GONE
        activity.rulesMsg.text = activity.getString(R.string.no_rules_msg)
        if (!instrumetProcessing) {
            activity.collapseBottomSheet()
        }
    }

    private fun redrawRules(rules: List<ExpressionSubstitution>) {
        Logger.d(TAG, "redrawRules")
        val activity = playActivity!!
        activity.rulesLinearLayout.removeAllViews()
        for (r in rules) {
            try {
                val rule = RuleMathView(activity)
                rule.setSubst(r, LevelScene.shared.currentLevel!!.subjectType)
                activity.rulesLinearLayout.addView(rule)
            } catch (e: Exception) {
                Logger.e(TAG, "Rule draw Error: $e")
            }
        }
        activity.halfExpandBottomSheet()
        activity.rulesMsg.text = if (rules.isEmpty()) activity.getString(R.string.no_rules_msg)
        else activity.getString(R.string.rules_found_msg)
    }

    fun onWin(context: Context) {
        Logger.d(TAG, "onWin")
        val activity = playActivity!!
        val currentLevel = LevelScene.shared.currentLevel!!
        //val award = currentLevel.getAward(context, currentTime, stepsCount)
        val newRes = LevelResult(stepsCount, currentTime, StateType.DONE)
        if (newRes.isBetter(currentLevel.lastResult)) {
            LevelScene.shared.levelsActivity!!.updateResult(newRes)
        }
        activity.onWin(stepsCount, currentTime, StateType.DONE)
        Statistics.logWin(stepsCount, activity.globalMathView.expression!!)
    }

    fun onLoose() {
        Logger.d(TAG, "onLoose")
        val activity = playActivity!!
        activity.onLoose()
        Statistics.logLoose(stepsCount, activity.globalMathView.expression!!, activity.globalMathView.currentAtoms)
    }

    private fun showMessage(msg: String) {
        val activity = playActivity!!
        activity.messageView.text = msg
        activity.messageView.visibility = View.VISIBLE
        messageTimer.cancel()
        messageTimer.start()
    }

    fun cancelTimers() {
        if (!LevelScene.shared.currentLevel!!.endless) {
            downTimer?.cancel()
        } else {
            upTimer?.cancel()
        }
        upTimer = null
        downTimer = null
    }
}