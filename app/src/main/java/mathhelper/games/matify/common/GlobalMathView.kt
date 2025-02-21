package mathhelper.games.matify.common

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import mathhelper.twf.api.findLowestSubtreeTopOfSelectedNodesInExpression
import mathhelper.twf.api.structureStringToExpression
import mathhelper.twf.expressiontree.ExpressionNode
import mathhelper.twf.expressiontree.ExpressionSubstitution
import mathhelper.games.matify.PlayScene
import mathhelper.games.matify.R
import mathhelper.games.matify.mathResolver.MathResolver
import mathhelper.games.matify.mathResolver.MathResolverPair
import mathhelper.games.matify.mathResolver.MatifySpan
import mathhelper.games.matify.mathResolver.TaskType
import kotlin.math.*

class GlobalMathView: androidx.appcompat.widget.AppCompatTextView {
    private val TAG = "GlobalMathView"
    var expression: ExpressionNode? = null
        private set
    var currentAtoms: MutableList<ExpressionNode> = mutableListOf()
        private set
    var multiselectionMode = false
    var currentRulesToResult : Map<ExpressionSubstitution, ExpressionNode>? = null
    var scale = 1.0f
    private var mathPair: MathResolverPair? = null
    private var type: String? = ""
    private var dX = 0f
    private var dY = 0f
    private var centerX: Float? = null
    private var centerY: Float? = null
    private var scaleListener = MathScaleListener()
    private lateinit var scaleDetector: ScaleGestureDetector
    private var ignoreUpAfterDrag = 0
    private var dragged = 0f

    /** INITIALIZATION **/
    constructor(context: Context): super(context) {
        Logger.d(TAG, "constructor from context")
        setDefaults(context)
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        Logger.d(TAG, "constructor from attrs")
        setDefaults(context)
    }

    private fun setDefaults(context: Context) {
        Logger.d(TAG, "setDefaults")
        scaleDetector = ScaleGestureDetector(context, scaleListener)
        setTextColor(ThemeController.shared.color(ColorName.TEXT_COLOR))
        includeFontPadding = false
        typeface = ResourcesCompat.getFont(context, R.font.roboto)
        textSize = Constants.centralExpressionDefaultSize
        setLineSpacing(0f, Constants.mathLineSpacing)
        //letterSpacing = 1.1f
        /*setPadding(
            Constants.defaultPadding, Constants.defaultPadding,
            Constants.defaultPadding, Constants.defaultPadding)*/
    }

    fun setExpression(expressionStr: String, type: String?) {
        Logger.d(TAG, "setExpression from str")
        if (expressionStr.isNotEmpty()) {
            expression = structureStringToExpression(expressionStr)
            setExpression(expression!!, type, true)
        }
    }

    fun setExpression(expressionNode: ExpressionNode, type: String?, resetSize: Boolean = true) {
        Logger.d(TAG, "setExpression from node")
        this.type = type
        expression = expressionNode
        if (resetSize) {
            scale = 1f
            scaleX = 1f
            scaleY = 1f
        }
        currentAtoms = arrayListOf()
        setTextFromExpression()
    }

    /** Scene interaction **/

    fun performSubstitutionForMultiselect(subst: ExpressionSubstitution): ExpressionNode? {
        Logger.d(TAG, "performSubstitution")
        var res: ExpressionNode? = null
        if (expression == null || currentRulesToResult == null) {
            Toast.makeText(context, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
        } else {
            res = currentRulesToResult!![subst]
            expression = res!!.clone()
            setTextFromExpression()
            currentRulesToResult = null
            currentAtoms = arrayListOf()
        }
        return res
    }

    fun recolorCurrentAtom(color: Int) {
        MatifySpan.recolor(color)
        setTextFromMathPair(true)
    }

    fun clearExpression() {
        var animated = false
        if (currentAtoms.isNotEmpty()) {
            animated = true
        }
        currentAtoms = arrayListOf()
        currentRulesToResult = null
        MatifySpan.clearSelected()
        setTextFromMathPair(animated)
    }

    /** TextView OVERRIDES **/
    override fun onTouchEvent(event: MotionEvent): Boolean {
        Logger.d(TAG, "onTouchEvent")
        if (centerX == null || centerY == null) {
            centerX = x
            centerY = y
        }
        var res = false
        scaleDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragged = 0f
                dX = event.x
                dY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val difX = event.x - dX
                val difY = event.y - dY
                dragged += max(abs(difX), abs(difY))
                if (dragged > 100) {
                    ignoreUpAfterDrag = event.pointerCount
                    val newx = x + difX
                    val newy = y + difY
                    if (abs(newx - x) < 100 && abs(newy - y) < 100 &&
                        AndroidUtil.insideParentWithOffset(this, difX, difY)) {
                        animate()
                            .x(newx)
                            .y(newy)
                            .setDuration(0)
                            .start()
                        dX = event.x
                        dY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (ignoreUpAfterDrag > 0) {
                    ignoreUpAfterDrag--
                    res = true
                } else if (expression != null && AndroidUtil.exteriorClickInside(this, event)) {
                    selectCurrentAtom(event)
                    res = true
                }
            }
        }
        return res
    }

    fun center() {
        if (centerX != null && centerY != null) {
            animate()
                .x(centerX!!)
                .y(centerY!!)
                .setDuration(100)
                .start()
        }
    }

    /** View OVERRIDES **/
    private fun getMathViewCoord(event: MotionEvent): Pair<Int, Int> {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val evx = event.x - loc[0]
        val evy = event.y - loc[1]
        val x = floor(evx / (MatifySpan.widthGlobal * scale)).toInt()
        val n = mathPair!!.height + 1f
        val msh = MatifySpan.heightGlobal
        val offset = (n * msh - height) / ((n - 1) * msh * 2)
        val spacing = 1 - 2 * offset
        val y = floor((evy / (MatifySpan.heightGlobal * scale) - offset) / spacing).toInt()
        return Pair(x, y)
    }

    private fun selectCurrentAtom(event: MotionEvent) {
        Logger.d(TAG, "selectCurrentAtom")
        if (layout != null) {
            val (x, y) = getMathViewCoord(event)
            val atomColor = ThemeController.shared.color(ColorName.TEXT_HIGHLIGHT_COLOR)
            val atomMultiColor = ThemeController.shared.color(ColorName.MULTISELECTION_COLOR)
            val atom = mathPair!!.getColoredAtom(x, y, multiselectionMode, atomColor)
            if (atom != null) {
                if (multiselectionMode) {
                    if (currentAtoms.any { it.nodeId == atom.nodeId }) {
                        currentAtoms = currentAtoms.filter { it.nodeId != atom.nodeId }.toMutableList()
                    } else {
                        currentAtoms.add(atom)
                    }
                    val topNode = findLowestSubtreeTopOfSelectedNodesInExpression(expression!!, currentAtoms)
                    mathPair!!.recolorExpressionInMultiSelectionMode(currentAtoms, topNode, atomMultiColor)//, atomSecondColor)
                } else {
                    currentAtoms.clear()
                    currentAtoms.add(atom)
                }
                setTextFromMathPair()
                PlayScene.shared.onAtomClicked()
            } else if(!multiselectionMode) {
                clearExpression()
            }
        }
        if (currentAtoms.isEmpty()) {
            PlayScene.shared.clearRules()
        }
    }

    fun deleteLastSelect() {
        val atom = currentAtoms.lastOrNull()
        if (atom != null) {
            currentAtoms.remove(atom)
            mathPair!!.deleteSpanForAtom(atom)
        }
        setTextFromMathPair(true)
        PlayScene.shared.onAtomClicked()
        if (currentAtoms.isEmpty()) {
            PlayScene.shared.clearRules()
        }
    }

    private fun setTextFromExpression() {
        Logger.d(TAG, "setTextFromExpression")
        MatifySpan.clearSelected()
        mathPair = when(type) {
            TaskType.SET.str -> MathResolver.resolveToPlain(expression!!, taskType = TaskType.SET)
            else -> MathResolver.resolveToPlain(expression!!)
        }
        setTextFromMathPair(true)
    }

    private fun setTextFromMathPair(animated: Boolean = false) {
        typeface = ResourcesCompat.getFont(context, R.font.roboto_mono_regular)
        val curw = paint.measureText(mathPair!!.matrix.toString().substringBefore("\n"))
        var parw = (parent as ConstraintLayout).width * 1f
        parw -= parw / 20
        if (curw > parw) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, floor(textSize * parw / curw))
        }
        if (animated) {
            animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    setText(mathPair!!.matrix, BufferType.SPANNABLE)
                    animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                }
                .start()
        } else {
            setText(mathPair!!.matrix, BufferType.SPANNABLE)
        }
    }

    inner class MathScaleListener: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale *= detector.scaleFactor
            scale = min(max(scale, 0.3f), 7f)
            animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(0)
                .start()
            if (!AndroidUtil.insideParentWithOffset(this@GlobalMathView)) {
                scale /= detector.scaleFactor
                scale = min(max(scale, 0.3f), 7f)
                animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(0)
                    .start()
            }
            return true
        }
    }
}