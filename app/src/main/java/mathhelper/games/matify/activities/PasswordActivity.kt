package mathhelper.games.matify.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import mathhelper.games.matify.AuthStatus
import mathhelper.games.matify.GlobalScene
import mathhelper.games.matify.R
import mathhelper.games.matify.common.*

class PasswordActivity: AppCompatActivity() {
    private val TAG = "PasswordActivity"
    private lateinit var oldPassView: TextView
    private lateinit var oldPassInputLayout: TextInputLayout
    private lateinit var newPassView: TextView
    private lateinit var newPassInputLayout: TextInputLayout
    private lateinit var repeatPassView: TextView
    private lateinit var repeatPassInputLayout: TextInputLayout
    private lateinit var confirmButton: Button

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        AndroidUtil.setLanguage(this)
        setTheme(ThemeController.shared.currentTheme.resId)
        setContentView(R.layout.activity_change_pass)
        oldPassView = findViewById(R.id.old_password)
        oldPassInputLayout = findViewById(R.id.oldInputLayout)
        newPassView = findViewById(R.id.new_password)
        newPassInputLayout = findViewById(R.id.newInputLayout)
        newPassInputLayout.visibility = View.GONE
        repeatPassView = findViewById(R.id.repeat_password)
        repeatPassInputLayout = findViewById(R.id.repeatInputLayout)
        repeatPassInputLayout.visibility = View.GONE
        confirmButton = findViewById(R.id.confirm)
        if (GlobalScene.shared.authStatus == AuthStatus.GUEST) {
            oldPassInputLayout.visibility = View.GONE
            newPassInputLayout.visibility = View.VISIBLE
            repeatPassInputLayout.visibility = View.VISIBLE
        }
        GlobalScene.shared.loadingElement = findViewById(R.id.progress)
    }

    fun back(v: View?) {
        finish()
    }

    fun confirm(v: View?) {
        if (oldPassInputLayout.visibility == View.VISIBLE) {
            val oldPass = Storage.shared.password()
            if (oldPassView.text.toString() == oldPass) {
                oldPassInputLayout.visibility = View.GONE
                newPassInputLayout.visibility = View.VISIBLE
                repeatPassInputLayout.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            }
        } else if (newPassView.text.toString() == repeatPassView.text.toString()) {
            if (Storage.shared.serverToken().isNullOrBlank()){
                val userData = Storage.shared.getUserInfoBase()
                userData.password = newPassView.text.toString()
                GlobalScene.shared.signUp(this, userData)
            } else {
                val requestRoot = JSONObject()
                requestRoot.put("login", Storage.shared.getUserInfoBase().login)
                requestRoot.put("password", newPassView.text.toString())
                val req = RequestData(RequestPage.EDIT, Storage.shared.serverToken(), body = requestRoot.toString())
                GlobalScene.shared.asyncTask(this, background = {
                    Request.editRequest(req)
                    Storage.shared.setUserInfo(
                        AuthInfoObjectBase(
                            password = newPassView.text.toString(),
                            authStatus = AuthStatus.MATH_HELPER
                        )
                    )
                }, foreground = {
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    finish()
                }, errorground = {})
            }
        } else {
            Toast.makeText(this, R.string.different_password, Toast.LENGTH_SHORT).show()
        }
    }
}