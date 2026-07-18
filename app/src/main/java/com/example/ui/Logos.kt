package com.example.ui

object Logos {
    const val GOOGLE = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/120px-Google_%22G%22_logo.svg.png"
    const val GITHUB = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Octicons-mark-github.svg/120px-Octicons-mark-github.svg.png"
    const val DRIVE = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Google_Drive_icon_%282020%29.svg/120px-Google_Drive_icon_%282020%29.svg.png"
    const val SLACK = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/Slack_icon_2019.svg/120px-Slack_icon_2019.svg.png"
    const val NOTION = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Notion-logo.svg/120px-Notion-logo.svg.png"
    const val TRELLO = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Trello-logo-blue.svg/120px-Trello-logo-blue.svg.png"

    // Logos oficiales de marca de modelos (PNG renderizado desde SVG en Wikimedia).
    // Coil 2.7.0 NO decodifica SVG; por eso se usan miniaturas PNG (...svg.png).
    // Verificadas HTTP 200 (image/png) el 2026-07-18. Badge de letra como fallback.
    const val MODEL_META = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Meta_Platforms_Inc._logo.svg/120px-Meta_Platforms_Inc._logo.svg.png"      // Llama
    const val MODEL_GOOGLE = GOOGLE                                                                                                                            // Gemma / Gemini
    const val MODEL_DEEPSEEK = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/DeepSeek-icon.svg/120px-DeepSeek-icon.svg.png"                       // DeepSeek
    const val MODEL_OPENAI = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4d/OpenAI_Logo.svg/120px-OpenAI_Logo.svg.png"                             // GPT / OpenAI
    const val MODEL_MISTRAL = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/Mistral_AI_logo_%282025%E2%80%93%29.svg/120px-Mistral_AI_logo_%282025%E2%80%93%29.svg.png" // Mistral / Mixtral
    const val MODEL_QWEN = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Qwen_logo.svg/120px-Qwen_logo.svg.png"                                  // Qwen
    const val MODEL_MICROSOFT = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/44/Microsoft_logo.svg/120px-Microsoft_logo.svg.png"                   // Phi
}
