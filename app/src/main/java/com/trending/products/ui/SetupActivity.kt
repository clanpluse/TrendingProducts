package com.trending.products.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.trending.products.R
import com.trending.products.data.network.ApiConfig

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "api_keys"
        private const val KEY_EBAY = "ebay_app_id"
        private const val KEY_ALI_KEY = "ali_key"
        private const val KEY_ALI_SECRET = "ali_secret"
        private const val KEY_SETUP_DONE = "setup_done"

        fun isSetupDone(ctx: Context): Boolean {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SETUP_DONE, false)
        }

        fun loadSavedKeys(ctx: Context) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Inject saved keys into ApiConfig at runtime using reflection
            val ebay = prefs.getString(KEY_EBAY, "") ?: ""
            val aliKey = prefs.getString(KEY_ALI_KEY, "") ?: ""
            val aliSecret = prefs.getString(KEY_ALI_SECRET, "") ?: ""

            if (ebay.isNotEmpty()) ApiConfig.runtimeEbayAppId = ebay
            if (aliKey.isNotEmpty()) ApiConfig.runtimeAliKey = aliKey
            if (aliSecret.isNotEmpty()) ApiConfig.runtimeAliSecret = aliSecret
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etEbay = findViewById<TextInputEditText>(R.id.etEbayAppId)
        val etAliKey = findViewById<TextInputEditText>(R.id.etAliKey)
        val etAliSecret = findViewById<TextInputEditText>(R.id.etAliSecret)
        val btnSave = findViewById<Button>(R.id.btnSaveKeys)
        val btnSkip = findViewById<Button>(R.id.btnSkip)

        // Pre-fill if already saved
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        etEbay.setText(prefs.getString(KEY_EBAY, ""))
        etAliKey.setText(prefs.getString(KEY_ALI_KEY, ""))
        etAliSecret.setText(prefs.getString(KEY_ALI_SECRET, ""))

        btnSave.setOnClickListener {
            val ebay = etEbay.text.toString().trim()
            val aliKey = etAliKey.text.toString().trim()
            val aliSecret = etAliSecret.text.toString().trim()

            prefs.edit()
                .putString(KEY_EBAY, ebay)
                .putString(KEY_ALI_KEY, aliKey)
                .putString(KEY_ALI_SECRET, aliSecret)
                .putBoolean(KEY_SETUP_DONE, true)
                .apply()

            if (ebay.isNotEmpty()) ApiConfig.runtimeEbayAppId = ebay
            if (aliKey.isNotEmpty()) ApiConfig.runtimeAliKey = aliKey
            if (aliSecret.isNotEmpty()) ApiConfig.runtimeAliSecret = aliSecret

            launchMain()
        }

        btnSkip.setOnClickListener {
            prefs.edit().putBoolean(KEY_SETUP_DONE, true).apply()
            launchMain()
        }
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
