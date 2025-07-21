package dev.stroe.netlens.preferences

import android.content.Context
import android.content.SharedPreferences
import dev.stroe.netlens.camera.Resolution
import dev.stroe.netlens.camera.CameraInfo
import dev.stroe.netlens.camera.FPSSetting
import androidx.core.content.edit

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
        private const val KEY_CAMERA_ID = "camera_id"
        private const val KEY_CAMERA_NAME = "camera_name"
        private const val KEY_CAMERA_FACING = "camera_facing"
        private const val KEY_FPS = "fps"
        private const val KEY_FPS_DELAY_MS = "fps_delay_ms"
        private const val KEY_FPS_NAME = "fps_name"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        
        // Default values
        private const val DEFAULT_PORT = "8080"
        private const val DEFAULT_RESOLUTION_WIDTH = 1280
        private const val DEFAULT_RESOLUTION_HEIGHT = 720
        private const val DEFAULT_RESOLUTION_NAME = "HD"
        private const val DEFAULT_FPS = 30
        private const val DEFAULT_FPS_DELAY_MS = 33L
        private const val DEFAULT_FPS_NAME = "30 FPS"
    }

    fun savePort(port: String) {
        preferences.edit { putString(KEY_SELECTED_PORT, port) }
    }

    fun getPort(): String {
        return preferences.getString(KEY_SELECTED_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
    }

    fun saveResolution(resolution: Resolution) {
        preferences.edit {
            putInt(KEY_RESOLUTION_WIDTH, resolution.width)
            putInt(KEY_RESOLUTION_HEIGHT, resolution.height)
            putString(KEY_RESOLUTION_NAME, resolution.name)
        }
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

    fun saveCamera(cameraInfo: CameraInfo) {
        preferences.edit {
            putString(KEY_CAMERA_ID, cameraInfo.id)
            putString(KEY_CAMERA_NAME, cameraInfo.name)
            putInt(KEY_CAMERA_FACING, cameraInfo.facing)
        }
    }

    fun getCamera(): CameraInfo? {
        val id = preferences.getString(KEY_CAMERA_ID, null)
        val name = preferences.getString(KEY_CAMERA_NAME, null)
        val facing = preferences.getInt(KEY_CAMERA_FACING, -1)
        
        return if (id != null && name != null && facing != -1) {
            CameraInfo(id, name, facing)
        } else {
            null
        }
    }

    fun hasCamera(): Boolean {
        return preferences.contains(KEY_CAMERA_ID) && 
               preferences.contains(KEY_CAMERA_NAME) && 
               preferences.contains(KEY_CAMERA_FACING)
    }

    fun saveFPS(fpsSetting: FPSSetting) {
        preferences.edit {
            putInt(KEY_FPS, fpsSetting.fps)
            putLong(KEY_FPS_DELAY_MS, fpsSetting.delayMs)
            putString(KEY_FPS_NAME, fpsSetting.name)
        }
    }

    fun getFPS(): FPSSetting {
        val fps = preferences.getInt(KEY_FPS, DEFAULT_FPS)
        val delayMs = preferences.getLong(KEY_FPS_DELAY_MS, DEFAULT_FPS_DELAY_MS)
        val name = preferences.getString(KEY_FPS_NAME, DEFAULT_FPS_NAME) ?: DEFAULT_FPS_NAME
        
        return FPSSetting(fps, delayMs, name)
    }

    fun hasFPS(): Boolean {
        return preferences.contains(KEY_FPS) && 
               preferences.contains(KEY_FPS_DELAY_MS) && 
               preferences.contains(KEY_FPS_NAME)
    }
    
    fun getKeepScreenOn(): Boolean {
        return preferences.getBoolean(KEY_KEEP_SCREEN_ON, true) // Default to true
    }
}
