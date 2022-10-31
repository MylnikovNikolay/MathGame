package mathhelper.games.matify.level

import android.content.Context
import android.util.Log
import android.content.Context.MODE_PRIVATE
import mathhelper.twf.api.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import mathhelper.twf.config.CompiledConfiguration
import mathhelper.twf.expressiontree.*
import mathhelper.games.matify.common.Logger
import mathhelper.games.matify.common.Storage
import mathhelper.games.matify.game.*
import mathhelper.games.matify.mathResolver.TaskType
import mathhelper.games.matify.parser.GsonParser
import mathhelper.games.matify.parser.Required

data class CompiledConfigurationParams(
    var maxCalcComplexity: String? = null,
    var maxTenPowIterations: String? = null,
    var maxPlusArgRounded: String? = null,
    var maxMulArgRounded: String? = null,
    var maxDivBaseRounded: String? = null,
    var maxPowBaseRounded: String? = null,
    var maxPowDegRounded: String? = null,
    var maxLogBaseRounded: String? = null,
    var maxResRounded: String? = null
) {
    val paramsMap
        get() = hashMapOf(
            "simpleComputationRuleParamsMaxCalcComplexity" to maxCalcComplexity,
            "simpleComputationRuleParamsMaxTenPowIterations" to maxTenPowIterations,
            "simpleComputationRuleParamsMaxPlusArgRounded" to maxPlusArgRounded,
            "simpleComputationRuleParamsMaxMulArgRounded" to maxMulArgRounded,
            "simpleComputationRuleParamsMaxDivBaseRounded" to maxDivBaseRounded,
            "simpleComputationRuleParamsMaxPowBaseRounded" to maxPowBaseRounded,
            "simpleComputationRuleParamsMaxPowDegRounded" to maxPowDegRounded,
            "simpleComputationRuleParamsMaxPowResRounded" to maxLogBaseRounded,
            "simpleComputationRuleParamsMaxResRounded" to maxResRounded,
        )
}

data class Level(
    /** Required values **/
    @property:Required
    var namespaceCode: String = "",
    @property:Required
    var code: String = "",
    @property:Required
    var version: Int = 0,
    @property:Required
    var nameEn: String = "",
    @property:Required
    var originalExpressionStructureString: String = "",
    var goalType: String = "",
    var goalExpressionStructureString: String? = null,
    /** Optional values **/
    var nameRu: String = "",
    var descriptionShortEn: String = "",
    var descriptionShortRu: String = "",
    var descriptionEn: String = "",
    var descriptionRu: String = "",
    var subjectType: String = "",
    var goalPattern: String? = null,
    var otherGoalData: JsonElement? = null,
    var rulePacks: List<RulePackLink>? = null,
    var rules: List<JsonObject>? = null,
    var stepsNumber: Int = 0,
    var time: Int = 0,
    var difficulty: Double = 0.0,
    var solutionsStepsTree: JsonElement? = null,
    var hints: JsonElement? = null,
    var interestingFacts: JsonElement? = null,
    var nextRecommendedTasks: JsonElement? = null,
    var otherCheckSolutionData: JsonElement? = null,
    var otherAwardData: JsonElement? = null,
    var otherData: JsonElement? = null,
) {
    lateinit var game: Game
    lateinit var startExpression: ExpressionNode
    var endExpression: ExpressionNode? = null
    var goalPatternExpr: ExpressionStructureConditionNode? = null
    var currentStepNum = 0
    var endless = true
    var lastResult: LevelResult? = null
    var additionalParamsMap = mutableMapOf<String, String>()
    var compiledConfiguration: CompiledConfiguration? = null

    fun getNameByLanguage(languageCode: String) = if (languageCode.equals("ru", true)) nameRu else nameEn
    fun getDescriptionByLanguage(languageCode: String, full: Boolean = false) = if (languageCode.equals("ru", true)) {
        if (full && descriptionRu.isNotEmpty()) descriptionRu else descriptionShortRu
    } else {
        if (full && descriptionEn.isNotEmpty()) descriptionEn else descriptionShortEn
    }

    companion object {
        private val TAG = "Level"

        fun parse(game: Game, levelJson: JsonObject, context: Context): Level? {
            Logger.d(TAG, "parse")
            val res = GsonParser.parse<Level>(levelJson) ?: return null
            res.game = game
            if (res.load()) {
                //res.setGlobalCoeffs(context)
                res.loadResult(context)
            }
            return res
        }
    }

    fun load(): Boolean {
        Logger.d(TAG, "load")
        if (subjectType.isBlank()) {
            subjectType = game.subjectType
        }
        startExpression = structureStringToExpression(originalExpressionStructureString)
        if (goalExpressionStructureString != null) {
            endExpression = structureStringToExpression(goalExpressionStructureString!!)
            Logger.d(TAG, "load", "task '${startExpression} -> ${endExpression}' parsed from " +
                "'${originalExpressionStructureString} -> ${goalExpressionStructureString}'")
        }
        if (goalPattern != null) {
            goalPatternExpr = when (subjectType) {
                TaskType.SET.str -> stringToExpressionStructurePattern(goalPattern!!, TaskType.SET.str)
                else -> stringToExpressionStructurePattern(goalPattern!!)
            }
            Logger.d(TAG, "load", "task '${startExpression} with pattern = ${goalPatternExpr}' parsed from " +
                "'${originalExpressionStructureString} with pattern = ${goalPattern}'")
        }
        var allRules = ArrayList<ExpressionSubstitution>()
        if (rulePacks != null) {
            for (pack in rulePacks!!) {
                val rulesFromPack = game.rulePacks[pack.rulePackCode]?.getAllRules()
                if (rulesFromPack != null) {
                    allRules = (allRules + rulesFromPack) as ArrayList<ExpressionSubstitution>
                }
            }
        }
        if (rules != null) {
            for (ruleJson in rules!!) {
                val rule = RulePackage.parseRule(ruleJson) ?: continue
                allRules.add(rule.substitution)
            }
        }
        if (otherCheckSolutionData != null && !otherCheckSolutionData!!.isJsonNull) {
            val params = GsonParser.parse<CompiledConfigurationParams>(otherCheckSolutionData!!.asJsonObject)
            if (params != null) {
                additionalParamsMap = params.paramsMap.filter { !it.value.isNullOrBlank() } as MutableMap<String, String>
            }
        }
        compiledConfiguration = createCompiledConfigurationFromExpressionSubstitutionsAndParams(
            allRules.toTypedArray(),
            additionalParamsMap
        )
        return true
    }

    /*
    private fun setGlobalCoeffs(context: Context) {
        if (Storage.shared.isUserAuthorized(context)) {
            val coeffs = Storage.shared.getUserCoeffs(context)
            undoPolicy = UndoPolicy.values()[coeffs.undoCoeff ?: 0]
            timeMultCoeff = coeffs.timeCoeff ?: timeMultCoeff
            time = (time * timeMultCoeff).toLong()
            awardMultCoeff = coeffs.awardCoeff ?: awardMultCoeff
        }
    }*/

    fun checkEnd(expression: ExpressionNode): Boolean {
        Logger.d(TAG, "checkEnd")
        return if (goalPattern.isNullOrBlank()) {
            val currStr = expressionToStructureString(expression)
            Logger.d(TAG, "current: $currStr | end: $goalExpressionStructureString")
            currStr == goalExpressionStructureString
        } else {
            val currStr = expressionToStructureString(expression)
            Logger.d(TAG, "current: $currStr | pattern: $goalPatternExpr")
            compareByPattern(expression, goalPatternExpr!!)
        }
    }

    /*fun getAward(context:Context, resultTime: Long, resultStepsNum: Double): Award {
        Logger.d(TAG, "getAward")
        val mark = when {
            resultStepsNum < stepsNum -> 1.0
//            resultTime > time -> 0.0
            else -> (
                1 - resultTime.toDouble() / (10 * time) +
                3 * stepsNum.toDouble() / resultStepsNum
                ) / (1 + 3)
        }
        return Award(context, getAwardByCoeff(mark), mark)
    }

    private fun getAwardByCoeff(mark: Double): AwardType {
        val awards = awardCoeffs.split(" ").map { it.toDouble() * awardMultCoeff }
        return when {
            mark.equals(1.0) -> AwardType.PLATINUM
            mark >= awards[0] -> AwardType.GOLD
            awards[1] <= mark && mark < awards[0] -> AwardType.SILVER
            awards[2] <= mark && mark < awards[1] -> AwardType.BRONZE
            mark.equals(-1.0) -> AwardType.PAUSED
            else -> AwardType.NONE
        }
    }*/

    fun getSubstitutionApplication(
        nodes: List<ExpressionNode>,
        expression: ExpressionNode
    ): List<SubstitutionApplication>? {
        val nodeIds = nodes.map{it.nodeId}
        Logger.d(TAG, "getSubstitutionApplication of '${expression.toString()}' in '(${nodeIds.joinToString()})'; detail: '${expression.toStringsWithNodeIds()}'")

        val list = findApplicableSubstitutionsInSelectedPlace(expression, nodeIds.toTypedArray(), compiledConfiguration!!, withReadyApplicationResult = true)
        if (list.isEmpty()) {
            return null
        }

        return list
    }

    fun getRulesFromSubstitutionApplication(substitutionApplication: List<SubstitutionApplication>): List<ExpressionSubstitution> {
        Logger.d(TAG, "getRulesFromSubstitutionApplication")

        var rules = substitutionApplication.map {
            Logger.d(TAG, "expressionSubstitution code: '${it.expressionSubstitution.code}'; priority: '${it.expressionSubstitution.priority}'")
            it.expressionSubstitution
        }
        rules = rules.distinctBy { Pair(it.left.identifier, it.right.identifier) }.toMutableList()
        rules.sortByDescending { it.left.toString().length }
        rules.sortBy { it.priority }
        return rules
    }

    fun getResultFromSubstitutionApplication(substitutionApplication: List<SubstitutionApplication>):
        Map<ExpressionSubstitution, ExpressionNode> {
        Logger.d(TAG, "getResultFromSubstitutionApplication")
        return substitutionApplication.map { it.expressionSubstitution to it.resultExpression }.toMap()
    }

    fun save(context: Context, result: LevelResult?) {
        Logger.d(TAG, "save", lastResult?.saveString())
        lastResult = result
        Storage.shared.saveResult(lastResult?.saveString(), game.code, code)
    }

    private fun loadResult(context: Context) {
        Logger.d(TAG, "loadResult")
        val resultStr = Storage.shared.loadResult(game.code, code)
        Logger.d(TAG, "loadResult", "loaded result = $resultStr")
        if (resultStr.isNotBlank()) {
            val resultVals = resultStr.split(" ", limit = 4)
            lastResult = LevelResult(resultVals[0].toDouble(), resultVals[1].toLong(),
                //Award(context, getAwardByCoeff(resultVals[2].toDouble()), resultVals[2].toDouble()))
                StateType.valueOf(resultVals[2].toString()))
            if (resultVals.size == 4) {
                lastResult!!.expression = resultVals[3]
            }
        }
    }

    fun isText() = descriptionShortEn.startsWith("text")
}