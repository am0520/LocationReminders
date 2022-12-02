package com.udacity.project4.authentication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.udacity.project4.R
import com.udacity.project4.databinding.ActivityAuthenticationBinding
import com.udacity.project4.locationreminders.RemindersActivity

private const val TAG = "AuthenticationActivity"

/**
 * This class should be the starting point of the app, It asks the users to sign in / register, and redirects the
 * signed in users to the RemindersActivity.
 */
class AuthenticationActivity : AppCompatActivity(),
    ActivityResultCallback<FirebaseAuthUIAuthenticationResult> {

    private lateinit var binding: ActivityAuthenticationBinding

    private val signInLauncher =
        registerForActivityResult(FirebaseAuthUIActivityResultContract(), this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            launchSignInFlow()
        }
    }

    // Start login flow using FirebaseUI
    private fun launchSignInFlow() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(), AuthUI.IdpConfig.GoogleBuilder().build()
        )

        val intent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setTheme(R.style.AppTheme)
            .setLogo(R.drawable.map)
            .build()

        signInLauncher.launch(intent)
    }

    // Handle response
    private fun handleSignInResponse(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse

        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            startRemindersActivity()
        } else {
            // Sign in failed
            if (response == null) {
                // User pressed back button
                showSnackbar(R.string.sign_in_cancelled)
                return
            }

            if (response.error!!.errorCode == ErrorCodes.NO_NETWORK) {
                showSnackbar(R.string.no_internet_connection)
                return
            }

            showSnackbar(R.string.unknown_error)

            Log.e(TAG, "Sign-in error: ", response.error)
        }
    }

    override fun onActivityResult(result: FirebaseAuthUIAuthenticationResult?) {
        // Successfully signed in
        result?.let { handleSignInResponse(result) }
    }

    // On resuming the activity, check if user is logged-in and navigate to Reminders if so
    override fun onResume() {
        super.onResume()
        val auth = FirebaseAuth.getInstance()

        if(auth.currentUser != null)
            startRemindersActivity()
    }

    private fun startRemindersActivity() {
        startActivity(Intent(this, RemindersActivity::class.java))
        finish()
    }

    private fun showSnackbar(@StringRes message: Int) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        snackbar.show()
    }
}
