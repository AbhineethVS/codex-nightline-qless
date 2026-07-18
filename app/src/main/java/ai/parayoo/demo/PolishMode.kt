package ai.parayoo.demo

import android.content.Context

object PolishMode {
    private const val PREFERENCES = "parayoo_preferences"
    private const val ENABLED = "polish_mode_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
            .apply()
    }
}
