package com.example

object AppConfig {
    private fun getValidKey(envVal: String): String {
        return if (envVal.isBlank() || envVal.contains("placeholder") || envVal.contains("your_") || envVal.contains("api_key_here")) {
            ""
        } else {
            envVal
        }
    }

    val GEMINI_API_KEY: String = getValidKey(BuildConfig.GEMINI_API_KEY)
    val CHATGPT_API_KEY: String = getValidKey(BuildConfig.CHATGPT_API_KEY)
    val CLAUDE_API_KEY: String = getValidKey(BuildConfig.CLAUDE_API_KEY)
}
