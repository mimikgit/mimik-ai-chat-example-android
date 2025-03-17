package com.mimik.aichat

import com.mimik.mimoeclient.milm.model.ModelDownload

object Constants {

    // Configuration values for mimik Edge developer project
    const val CLIENT_ID = "your-client-id"
    const val DEVELOPER_ID_TOKEN = "your-developer-id-token"
    const val MIM_OE_LICENSE = "your-mim-oe-license"
    const val MID_URL = "https://devconsole-mid.mimik.com"

    // Default Model to use
    val DEFAULT_MODEL = ModelDownload(
        id = "hugging-quants/Llama-3.2-1B-Instruct-Q8_0-GGUF",
        obj = "model",
        url = "https://huggingface.co/hugging-quants/Llama-3.2-1B-Instruct-Q8_0-GGUF/resolve/main/llama-3.2-1b-instruct-q8_0.gguf?download=true",
        ownedBy = "hugging-quants"
    )

    // Example Model
    val SECOND_MODEL = ModelDownload(
        "lmstudio-ai/gemma-2b-it-GGUF",
        "model",
        "https://huggingface.co/lmstudio-community/gemma-1.1-2b-it-GGUF/resolve/main/gemma-1.1-2b-it-Q4_K_M.gguf?download=true",
        "lmstudio-ai"
    )
}