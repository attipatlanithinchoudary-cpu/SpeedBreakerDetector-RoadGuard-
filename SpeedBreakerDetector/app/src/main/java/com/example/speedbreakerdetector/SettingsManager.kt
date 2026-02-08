package com.example.speedbreakerdetector

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    // SharedPreferences is a simple key-value store.
    // We create a private file named "App_Settings" that only our app can access.
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "App_Settings",
        Context.MODE_PRIVATE
    )

    // Define keys for each setting. Using constants prevents typos.
    companion object {
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        // We can add more keys here later for other settings
    }

    // --- Vibration Setting ---

    /**
     * Saves the state of the vibration setting.
     * @param isEnabled The value to save (true or false).
     */
    fun setVibrationEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VIBRATION_ENABLED, isEnabled).apply()
    }

    /**
     * Loads the state of the vibration setting.
     * @return The saved value. Defaults to 'true' if no value has been saved yet.
     */
    fun isVibrationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true) // Default is ON
    }


    // --- Sound Setting ---

    /**
     * Saves the state of the sound setting.
     * @param isEnabled The value to save (true or false).
     */
    fun setSoundEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SOUND_ENABLED, isEnabled).apply()
    }

    /**
     * Loads the state of the sound setting.
     * @return The saved value. Defaults to 'true' if no value has been saved yet.
     */
    fun isSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SOUND_ENABLED, true) // Default is ON
    }
}
