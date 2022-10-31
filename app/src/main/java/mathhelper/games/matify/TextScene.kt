package mathhelper.games.matify

import mathhelper.games.matify.activities.TextActivity


class TextScene {
    companion object {
        val shared: TextScene = TextScene()
    }

    var textActivity: TextActivity? = null
}