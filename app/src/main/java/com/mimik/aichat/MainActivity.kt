package com.mimik.aichat

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.mimik.mimoeclient.MimOEClient
import com.mimik.mimoeclient.MimOERequestError
import com.mimik.mimoeclient.MimOERequestResponse
import com.mimik.mimoeclient.MimOEResponseHandler
import com.mimik.mimoeclient.Util
import com.mimik.mimoeclient.authobject.DeveloperTokenLoginConfig
import com.mimik.mimoeclient.microserviceobjects.MicroserviceDeploymentConfig
import com.mimik.mimoeclient.milm.MimOEClientMilm
import com.mimik.mimoeclient.milm.model.Message
import com.mimik.mimoeclient.milm.model.MilmModel
import com.mimik.mimoeclient.milm.model.MilmQuery
import com.mimik.mimoeclient.milm.model.MilmResponse
import com.mimik.mimoeclient.milm.model.ModelDownload
import com.mimik.mimoeclient.milm.model.ModelStatus
import com.mimik.mimoeclient.mimoeservice.MimOEConfig
import com.mimik.mimoeclient.model.AccessTokenPayload
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.awaitResponse
import java.io.IOException
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var textBy: TextView
    private lateinit var textResponse: TextView
    private lateinit var textProgress: TextView
    private lateinit var textViewQuery: TextView
    private lateinit var editTextQuery: TextInputEditText
    private lateinit var textInputLayout: TextInputLayout
    private lateinit var buttonAdd: ImageButton
    private lateinit var spinner: Spinner
    private lateinit var scrollview: ScrollView

    private lateinit var markwon: Markwon

    private val mimOEClient: MimOEClient = MimOEClient(
        this,
        MimOEConfig().logLevel("debug").license(Constants.MIM_OE_LICENSE)
    )
    private var currentModelId: String = Constants.DEFAULT_MODEL.id

    private val executor: Executor = Executors.newFixedThreadPool(3)
    private lateinit var handler: Handler

    private var accessToken: String? = null

    private val modelWrapperList: MutableList<String> = mutableListOf()
    private val modelList: MutableList<MilmModel> = mutableListOf()
    private lateinit var modelAdapter: ArrayAdapter<String>

    private var uploadOpenCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main)
        // Prevent the screen from turning off
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        MimOEClientMilm.setMilmApiKey(this, "1234")

        // Use Markdown TextView library
        markwon = Markwon.builder(this)
            .usePlugin(HtmlPlugin.create())
            .build()

        handler = Handler(Looper.getMainLooper())

        editTextQuery = findViewById(R.id.edit_query)
        textViewQuery = findViewById(R.id.text_query)
        textBy = findViewById(R.id.text_by)

        textResponse = findViewById(R.id.text_response)

        textProgress = findViewById(R.id.text_progress)
        textInputLayout = findViewById(R.id.text_input_layout)
        spinner = findViewById(R.id.spinner)
        buttonAdd = findViewById(R.id.button_add)

        scrollview = findViewById(R.id.scrollview)

        disableQuery()

        buttonAdd.setOnClickListener {
            showAddDialog()
        }

        modelAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modelWrapperList)
        modelAdapter.setDropDownViewResource(android.R.layout.select_dialog_singlechoice)
        spinner.adapter = modelAdapter
        spinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentModelId = modelAdapter.getItem(position) ?: Constants.DEFAULT_MODEL.id
                val currentModel = modelList.find { it.id == currentModelId }
                if (currentModel == null || !currentModel.readyToUse) {
                    disableQuery()
                } else {
                    enableQuery()
                }
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed(this@MainActivity::checkModelDeployed, 200)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        executor.execute { this.startEdge() }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Enable all buttons and entry once mILM microservice has a model ready
     */
    private fun enableQuery() {
        editTextQuery.setOnEditorActionListener(this::handleRequestIME)
        textInputLayout.setEndIconOnClickListener(this::handleRequest)
        textProgress.text = String.format(getString(R.string.model_progress), "100")
        textProgress.visibility = View.VISIBLE
        buttonAdd.isEnabled = true

        if (textResponse.text == String.format(getString(R.string.model_not_ready_yet), currentModelId)) {
            textResponse.text = ""
        }
    }

    /**
     * Disable all buttons and entry while mILM microservicedoes not have a
     * model ready
     */
    private fun disableQuery() {
        editTextQuery.setOnEditorActionListener { _, _, _ ->
            showNotReady()
            return@setOnEditorActionListener true
        }
        textInputLayout.setEndIconOnClickListener {
            showNotReady()
        }
        textProgress.text = String.format(
            getString(R.string.model_not_ready_yet),
            currentModelId)
        textProgress.visibility = View.VISIBLE
        buttonAdd.isEnabled = false
    }

    /**
     * Show not ready text
     */
    private fun showNotReady() {
        textViewQuery.text = editTextQuery.text.toString().trim()
        textBy.text = String.format(getString(R.string.response_s), currentModelId)
        textResponse.text = String.format(
            getString(R.string.model_not_ready_yet),
            currentModelId)
    }

    /**
     * Enable prompt entry UI
     */
    private fun enableQuestion() {
        editTextQuery.isEnabled = true
        textInputLayout.isEnabled = true
        textInputLayout.isFocusable = true
        textInputLayout.isFocusableInTouchMode = true
    }

    /**
     * Disable prompt entry UI
     */
    private fun disableQuestion() {
        editTextQuery.isEnabled = false
        textInputLayout.isEnabled = false
        textInputLayout.isFocusable = false
        textInputLayout.isFocusableInTouchMode = false
    }

    /**
     * Parse prompt and request response
     */
    private fun handleRequestIME(textView: TextView?, actionId: Int, keyEvent: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            handleRequest(textView)
            return true
        }
        return false
    }

    /**
     * Parse prompt and request response
     */
    private fun handleRequest(view: View?) {
        val question = editTextQuery.text.toString().trim()
        if (question.isBlank()) {
            return
        }

        getResponse(question, currentModelId)
    }

    /**
     * Start mimik Edge. If already initialized, clean up models that are not
     * ready in mILM microservice and start periodic checking. If not already
     * initialized, authorize mimik Edge.
     */
    private fun startEdge() {
        if (mimOEClient.startMimOESynchronously()) { // Start edgeEngine runtime
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "mim OE started!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (getInitialized()) {
                Log.d(javaClass.simpleName, "startEdge: ${mimOEClient.mimikAccessToken}")
                removeNotReadyModels()
                runOnUiThread {
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ checkModelDeployed() }, 1000)
                }
            } else {
                authorizeEdge()
            }
        } else {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "mim OE failed to start!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Clean up models that are not ready to use, since mILM does not support resume.
     */
    @SuppressLint("DefaultLocale") // Not used in a way in which locale matters
    private fun removeNotReadyModels() {
        val provider = MimOEClientMilm.getMilmProvider(this, mimOEClient)
        val resp = provider.getModels().execute()
        if (resp.isSuccessful) {
            resp.body()?.let {
                for (model in it.data) {
                    if (!model.readyToUse) {
                        provider.deleteModel(model.id).execute()
                    }
                }
            }
        }
    }

    /**
     * Authorize mimik Edge using Developer ID Token, and if successful deploy
     * microservices
     */
    private fun authorizeEdge() {
        if (developerTokenIsExpired()) {
            runOnUiThread {
                textProgress.text = getString(R.string.developer_id_token_expired)

                Toast.makeText(
                    this@MainActivity, getString(R.string.developer_id_token_expired),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val config = DeveloperTokenLoginConfig()
        config.setAuthorizationRootUri(Constants.MID_URL)
        config.developerToken = Constants.DEVELOPER_ID_TOKEN
        config.clientId = Constants.CLIENT_ID

        mimOEClient.loginWithDeveloperToken(
            this,
            config,
            object : MimOEResponseHandler {
                override fun onError(mimOERequestError: MimOERequestError) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity, "Error getting access token! "
                                    + mimOERequestError.errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(mimOERequestResponse: MimOERequestResponse) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Got access token!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    accessToken = mimOEClient.mimikAccessToken
                    Log.d(javaClass.simpleName, "accessToken: $accessToken")
                    deployMIlmMicroservice()
                }
            }
        )
    }

    /**
     * Check if Developer ID Token is expired
     */
    private fun developerTokenIsExpired(): Boolean {
        try {
            val developerTokenPayload = Util.getJWTPayload(Constants.DEVELOPER_ID_TOKEN, AccessTokenPayload::class.java)
            val expiry = LocalDateTime.ofInstant(Instant.ofEpochSecond(developerTokenPayload.exp), TimeZone.getDefault().toZoneId())
            return expiry.isBefore(LocalDateTime.now())
        } catch (ex: Exception) {
            Toast.makeText(
                this@MainActivity, getString(R.string.could_not_parse_developer_id_token),
                Toast.LENGTH_SHORT
            ).show()
            ex.printStackTrace()
            return true
        }
    }

    /**
     * Deploy mILM microservice, then clean up models that are
     * not ready and start periodic checking
     */
    private fun deployMIlmMicroservice() {
        val status = MimOEClientMilm.deployDefaultMilmMicroservice(
            this,
            mimOEClient,
            resources.openRawResource(R.raw.milm_v1_1_7_0)
        )
        if (status.error != null) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity, (
                            "Failed to deploy microservice! "
                                    + status.error.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Successfully deployed mIM!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            removeNotReadyModels()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ checkModelDeployed() }, 1000)
        }
    }

    /**
     * Periodic check to keep track of models in mILM microservice,
     * as well as track if a model is ready to use.
     */
    @SuppressLint("DefaultLocale") // Not used in a way in which locale matters
    private fun checkModelDeployed() {
        executor.execute {
            val resp = MimOEClientMilm.getMilmProvider(this, mimOEClient).getModels().execute()
            if (resp.isSuccessful) {
                val list = resp.body()?.data
                modelList.clear()
                modelWrapperList.clear()
                for (model in list!!) {
                    modelWrapperList.add(model.id)
                    modelList.add(model)
                }
                runOnUiThread {
                    modelAdapter.notifyDataSetChanged()
                    val index = modelWrapperList.indexOf(currentModelId)
                    if (index != -1
                        && spinner.selectedItemPosition != index) {
                        spinner.setSelection(index)
                    }
                }

                val defaultModel = list.find { it.id == Constants.DEFAULT_MODEL.id }
                if (defaultModel == null) {
                    deployModel(
                        Constants.DEFAULT_MODEL
                    )
                }
                val currentModel = list.find { it.id == currentModelId }
                if (currentModel?.readyToUse == true) {
                    runOnUiThread {
                        setInitialized()
                        enableQuery()
                    }
                    handler.removeCallbacksAndMessages(null)
                } else {
                    runOnUiThread {
//                        showNotReady()
//                        disableQuery()
                    }
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed(this::checkModelDeployed, 5000)
                }
            } else {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed(this::checkModelDeployed, 5000)
            }
        }
    }

    /**
     * Queue a model to be created and downloaded by the mILM microservice
     * @param modelUpload Model to be created
     */
    @SuppressLint("DefaultLocale") // Not used in a way in which locale matters
    private fun deployModel(modelUpload: ModelDownload) {
        val call = MimOEClientMilm.getMilmProvider(this, mimOEClient).queueModel(modelUpload)
        CoroutineScope(Dispatchers.IO).launch {
            streamingCoroutine(call, Gson(), ModelStatus::class.java).collectLatest {
                withContext(Dispatchers.Main) {
                    // Set model download progress in UI
                    if (currentModelId == modelUpload.id) {
                        val percent: Double =
                            ((it.size.toFloat() / it.totalSize.toFloat()) * 100).toDouble()
                        textProgress.text = String.format(
                            getString(R.string.model_progress),
                            DecimalFormat("#0.00").format(percent)
                        )
                        textProgress.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /**
     * Submit a prompt to a specific model for a response
     * @param question Prompt to submit to the model
     * @param modelId ID of model to submit the prompt to
     */
    @SuppressLint("DefaultLocale") // Not used in a way in which locale matters
    private fun getResponse(question: String, modelId: String) {
        textViewQuery.text = question
        textBy.text = String.format(getString(R.string.response_s), currentModelId)
        editTextQuery.text?.clear()
        textResponse.text = getString(R.string.please_wait)
        disableQuestion()

        val query = MilmQuery(
            model = modelId,
            messages = listOf(Message("user", question)),
            temperature = null,
            maxTokens = null,
            stream = true
        )

        // Use Coroutines to asynchronously make API call
        CoroutineScope(Dispatchers.IO).launch {
            val str = StringBuilder()
            streamingCoroutine(
                MimOEClientMilm.getMilmProvider(this@MainActivity, mimOEClient).sendCompletion(query),
                Gson(),
                MilmResponse::class.java
            ).collectLatest {
                withContext(Dispatchers.Main) {
                    // Set response in UI
                    str.append(it.choices[0].delta?.content)
                    markwon.setMarkdown(textResponse, str.toString())
                    scrollview.post {
                        scrollview.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    /**
     * Handle an API call as streaming
     * @param call API call to execute
     * @param gson Gson JSON parser to use
     * @param classOf Expected class of data
     */
    private fun <T> streamingCoroutine(call: Call<ResponseBody>, gson: Gson, classOf: Class<T>) = flow {
        try {
            val response = call.awaitResponse()
            if (response.isSuccessful) {
                Log.d(javaClass.simpleName, "streamingCoroutine: do the thing")
                response.body()?.byteStream()?.bufferedReader().use { input ->
                    while (currentCoroutineContext().isActive) {
                        val line = input?.readLine()
                        Log.d(javaClass.simpleName, "streamingCoroutine: ${line}")
                        if (line != null && line.startsWith("data:")) {
                            try {
                                val data = gson.fromJson(
                                    line.substring(5).trim(),
                                    classOf
                                )

                                if (data is MilmResponse
                                    && data.choices[0].finishReason == "stop") {
                                    // Response is finished, re-enable user interaction and end streaming
                                    runOnUiThread {
                                        enableQuestion()
                                    }
                                    break
                                } else if (data is ModelStatus
                                    && data.size == data.totalSize) {
                                    // Model Queuing is finished, end streaming
                                    break
                                }
                                emit(data)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            delay(100)
                        }
                    }
                }
            } else {
                runOnUiThread {
                    markwon.setMarkdown(textResponse, response.message())
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Get initialized state
     */
    private fun getInitialized(): Boolean {
        return sharedPreferences.getBoolean("init", false)
    }

    /**
     * Set initialized state
     */
    private fun setInitialized() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("init", true)
        editor.apply()
    }

    /**
     * Show a dialog that allows the user to add a custom model to mILM
     * microservice. Pre-populate the fields with information for various
     * example models, cycling each time the dialog is opened.
     */
    private fun showAddDialog() {
        uploadOpenCount++
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add)

        // Cycle through example models
        val modelToUse = Constants.SECOND_MODEL

        val editId: EditText? = dialog.findViewById(R.id.edit_id)
        editId?.setText(modelToUse.id)
        val editObject: EditText? = dialog.findViewById(R.id.edit_object)
        editObject?.setText(modelToUse.obj)
        val editUrl: EditText? = dialog.findViewById(R.id.edit_url)
        editUrl?.setText(modelToUse.url)
        val editOwnedBy: EditText? = dialog.findViewById(R.id.edit_owned_by)
        editOwnedBy?.setText(modelToUse.ownedBy)

        val button: Button? = dialog.findViewById(R.id.button_add_model)
        button?.setOnClickListener {
            // Deploy the custom model to mILM microservice
            deployModel(ModelDownload(
                editId?.text.toString().trim(),
                editObject?.text.toString().trim(),
                editUrl?.text.toString().trim(),
                editOwnedBy?.text.toString().trim()
            ))

            textProgress.text = String.format(getString(R.string.model_progress), DecimalFormat("#0.00").format(0))
            textProgress.visibility = View.VISIBLE

            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(this::checkModelDeployed, 200)
            currentModelId = editId?.text.toString().trim()

            dialog.dismiss()
        }
        dialog.show()
    }
}