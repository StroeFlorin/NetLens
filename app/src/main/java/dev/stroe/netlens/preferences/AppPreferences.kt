package dev.stroe.netlens.preferences

import android.content.Context
import android.content.SharedPreferences
import dev.stroe.netlens.camera.Resolution

class AppPreferences(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "netlens_preferences"
        private const val KEY_SELECTED_PORT = "selected_port"
        private const val KEY_RESOLUTION_WIDTH = "resolution_width"
        private const val KEY_RESOLUTION_HEIGHT = "resolution_height"
        private const val KEY_RESOLUTION_NAME = "resolution_name"
        
        // Default values
        private const val DEFAULT_PORT = "8082"
        private const val DEFAULT_RESOLUTION_WIDTH = 1280
        private const val DEFAULT_RESOLUTION_HEIGHT = 720
        private const val DEFAULT_RESOLUTION_NAME = "HD"
    }

    fun savePort(port: String) {
        preferences.edit().putString(KEY_SELECTED_PORT, port).apply()
    }

    fun getPort(): String {
        return preferences.getString(KEY_SELECTED_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
    }

    fun saveResolution(resolution: Resolution) {
        preferences.edit().apply {
            putInt(KEY_RESOLUTION_WIDTH, resolution.width)
            putInt(KEY_RESOLUTION_HEIGHT, resolution.height)
            putString(KEY_RESOLUTION_NAME, resolution.name)
        }.apply()
    }

    fun getResolution(): Resolution {
        val width = preferences.getInt(KEY_RESOLUTION_WIDTH, DEFAULT_RESOLUTION_WIDTH)
        val height = preferences.getInt(KEY_RESOLUTION_HEIGHT, DEFAULT_RESOLUTION_HEIGHT)
        val name = preferences.getString(KEY_RESOLUTION_NAME, DEFAULT_RESOLUTION_NAME) ?: DEFAULT_RESOLUTION_NAME
        
        return Resolution(width, height, name)
    }

    fun hasResolution(): Boolean {
        return preferences.contains(KEY_RESOLUTION_WIDTH) && 
               preferences.contains(KEY_RESOLUTION_HEIGHT) && 
               preferences.contains(KEY_RESOLUTION_NAME)
    }
}
