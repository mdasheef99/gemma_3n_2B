package com.gemma3n.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gemma3n.app.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * MainActivity refactored for better maintainability
 * Uses modular architecture with separated concerns
 * Optimized for Samsung S23 with Gemma 3n model
 */
class MainActivity : AppCompatActivity(),
    ModelManager.ModelStatusListener,
    PermissionHandler.PermissionListener,
    ImageProcessor.ImageProcessorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var modelManager: ModelManager
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var uiStateManager: UIStateManager
    private lateinit var errorHandler: ErrorHandler

    // Chat system components
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        setupUI()
        startInitialization()
    }

    /**
     * Initialize all component dependencies
     */
    private fun initializeComponents() {
        Log.d(TAG, "Initializing components...")

        modelManager = ModelManager(this).apply { setListener(this@MainActivity) }
        permissionHandler = PermissionHandler(this).apply { setListener(this@MainActivity) }
        imageProcessor = ImageProcessor(this).apply {
            setListener(this@MainActivity)
            initialize(this@MainActivity)
        }
        uiStateManager = UIStateManager(binding, this)
        errorHandler = ErrorHandler(this)

        // Initialize chat system
        setupChatSystem()

        Log.d(TAG, "All components initialized successfully")
    }

    /**
     * Set up the chat system components
     */
    private fun setupChatSystem() {
        Log.d(TAG, "Setting up chat system...")

        // Initialize chat adapter
        chatAdapter = ChatAdapter()

        // Set up RecyclerView
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
            // Auto-scroll to bottom when new messages are added
            chatAdapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    scrollToPosition(chatAdapter.itemCount - 1)
                }
            })
        }

        Log.d(TAG, "Chat system setup completed")
    }

    /**
     * Start the initialization process
     */
    private fun startInitialization() {
        Log.d(TAG, "Starting initialization process...")

        if (permissionHandler.checkAndRequestPermissions()) {
            // Permissions already granted, start model initialization
            modelManager.initialize()
        }
        // If permissions not granted, model initialization will be called after permission grant
    }
    
    /**
     * Setup UI event listeners and initial state
     */
    private fun setupUI() {
        Log.d(TAG, "Setting up UI...")

        binding.selectImageButton.setOnClickListener {
            imageProcessor.selectImage()
        }

        binding.takePhotoButton.setOnClickListener {
            imageProcessor.takePhoto()
        }

        // Set up settings button
        binding.settingsButton.setOnClickListener {
            openModelSettings()
        }

        // Set up chat input handling
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Handle Enter key in message input
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        // Set up download button
        binding.downloadModelButton.setOnClickListener {
            startModelDownload()
        }

        // Set up cancel download button
        binding.cancelDownloadButton.setOnClickListener {
            cancelModelDownload()
        }

        // Keep old button for backward compatibility (hidden)
        binding.askButton.setOnClickListener {
            processQuestion()
        }

        // Show initial state
        uiStateManager.showInitialState()

        // Add welcome message to chat
        addWelcomeMessage()

        // Check if we should trigger download (from settings activity)
        if (intent.getBooleanExtra("trigger_download", false)) {
            startModelDownload()
        }

        // Note: Removed forced model status override - let the actual model status be detected

        Log.d(TAG, "UI setup completed")
    }

    /**
     * Add a welcome message to start the conversation
     */
    private fun addWelcomeMessage() {
        val welcomeMessage = ChatMessage.createSystemMessage(
            "Welcome! I'm Gemma 3n AI. You can ask me questions about images you select. " +
            "Start by selecting an image above, then type your question below."
        )
        chatAdapter.addMessage(welcomeMessage)
    }

    /**
     * Start model download process
     */
    private fun startModelDownload() {
        Log.d(TAG, "User initiated model download")

        // Critical safeguard: Prevent multiple downloads
        if (modelManager.getCurrentStatus() == ModelManager.ModelStatus.DOWNLOADING) {
            Log.w(TAG, "Download already in progress, ignoring duplicate request")
            return
        }

        modelManager.startDownload()
    }

    /**
     * Cancel ongoing model download
     */
    private fun cancelModelDownload() {
        Log.d(TAG, "User cancelled model download")
        modelManager.cancelDownload()
        addSystemMessage("Download cancelled by user.")
    }

    /**
     * Open model settings activity
     */
    private fun openModelSettings() {
        Log.d(TAG, "Opening model settings")
        val intent = Intent(this, ModelSettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Send a message in the chat
     */
    private fun sendMessage() {
        val messageText = binding.messageInput.text.toString().trim()

        if (messageText.isEmpty()) {
            return
        }

        if (!modelManager.isReady()) {
            addSystemMessage("AI model is not ready yet. Please wait for initialization to complete.")
            return
        }

        // Clear input field
        binding.messageInput.text.clear()

        // Add user message to chat
        val userMessage = ChatMessage.createUserMessage(messageText)
        chatAdapter.addMessage(userMessage)

        // Scroll to show user's message
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

        // Process the message
        processMessageWithAI(messageText)
    }

    /**
     * Process message with AI and add response to chat
     */
    private fun processMessageWithAI(message: String) {
        Log.d(TAG, "Processing message with AI: $message")

        // Show processing indicator
        binding.progressBar.visibility = View.VISIBLE

        // CRITICAL FIX: Move to background thread to prevent ANR
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Show loading state on UI thread
                withContext(Dispatchers.Main) {
                    binding.sendButton.isEnabled = false
                    binding.sendButton.text = "Sending..."
                    // Hide keyboard
                    hideKeyboard()
                }

                // Process on background thread
                val response = modelManager.processTextQuestion(message)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Add AI response to chat
                    val aiMessage = ChatMessage.createAIResponse(response)
                    chatAdapter.addMessage(aiMessage)

                    // CRITICAL FIX: Scroll to bottom to show new message
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                    binding.sendButton.isEnabled = true
                    binding.sendButton.text = "Send"
                    Log.d(TAG, "Message processed successfully - Response added to chat")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                withContext(Dispatchers.Main) {
                    addSystemMessage("Sorry, I encountered an error processing your message. Please try again.")
                    binding.sendButton.isEnabled = true
                    binding.sendButton.text = "Send"
                    errorHandler.handleProcessingError(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Hide processing indicator
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Add a system message to the chat
     */
    private fun addSystemMessage(message: String) {
        val systemMessage = ChatMessage.createSystemMessage(message)
        chatAdapter.addMessage(systemMessage)
    }

    /**
     * Hide the soft keyboard
     */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
    }

    /**
     * Process user question with selected image (legacy method for backward compatibility)
     */
    private fun processQuestion() {
        val question = binding.questionInput.text.toString().trim()
        val bitmap = imageProcessor.getCurrentBitmap()

        if (question.isEmpty()) {
            errorHandler.showValidationError("Please enter a question")
            return
        }

        if (bitmap == null) {
            errorHandler.showValidationError("Please select an image first")
            return
        }

        if (!modelManager.isReady()) {
            errorHandler.showValidationError("AI model is not ready yet")
            return
        }

        Log.d(TAG, "Processing question: $question")
        uiStateManager.showProcessing()

        lifecycleScope.launch {
            try {
                val response = modelManager.processImageQuestion(question, bitmap)
                uiStateManager.showResponse(response)
                Log.d(TAG, "Question processed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing question", e)
                errorHandler.handleProcessingError(e)
                uiStateManager.showError("Processing Failed", "Please try again")
            }
        }
    }

    // ================================================================================================
    // LISTENER INTERFACE IMPLEMENTATIONS
    // ================================================================================================

    /**
     * ModelStatusListener implementation
     */
    override fun onStatusChanged(status: ModelManager.ModelStatus) {
        Log.d(TAG, "Model status changed: $status")
        uiStateManager.updateModelStatus(status)

        // Clear any existing click listeners to prevent multiple listeners
        binding.askButton.setOnClickListener(null)

        // Setup manual download button when model is missing
        if (status == ModelManager.ModelStatus.MISSING) {
            Log.d(TAG, "Model missing - setting up manual download button")
            binding.askButton.setOnClickListener {
                Log.d(TAG, "User initiated model download")
                modelManager.startDownload()
            }
        }

        // Handle retry download button for download failures
        if (status == ModelManager.ModelStatus.DOWNLOAD_FAILED) {
            Log.d(TAG, "Download failed - setting up retry download button")
            binding.askButton.setOnClickListener {
                Log.d(TAG, "User initiated download retry after failure")
                // Cleanup is already done in ModelManager.onError()
                modelManager.startDownload()
            }
        }

        // Handle retry for model initialization errors
        if (status == ModelManager.ModelStatus.ERROR) {
            Log.d(TAG, "Model initialization failed - setting up retry button")
            binding.askButton.setOnClickListener {
                Log.d(TAG, "Model initialization failed - retrying initialization")
                // Try to retry initialization first before downloading again
                // Only download if the model file is actually missing or corrupted
                modelManager.retryInitialization()
            }
        }
    }

    override fun onDownloadProgress(percentage: Int, downloadedMB: Long, totalMB: Long, speedMBps: Float, etaSeconds: Long) {
        Log.d(TAG, "Download progress: $percentage% ($downloadedMB/$totalMB MB, ${speedMBps}MB/s, ETA: ${etaSeconds}s)")
        uiStateManager.updateDownloadProgress(percentage, downloadedMB, totalMB, speedMBps, etaSeconds)
    }

    override fun onModelReady() {
        Log.d(TAG, "Model is ready for inference")

        // Show completion notification and enable features
        uiStateManager.showDownloadCompleted()

        // Add success message to chat
        addSystemMessage("🎉 AI model loaded successfully! You can now ask questions about images.")

        // Restore normal ask button functionality
        binding.askButton.setOnClickListener {
            processQuestion()
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "Model error: $error")

        // Pass specific error message to UI for better user guidance
        uiStateManager.showDownloadFailed(error)

        // Also handle through error handler for logging/analytics
        errorHandler.handleModelError(Exception(error))
    }

    override fun onDownloadCancelled() {
        Log.d(TAG, "Download was cancelled by user")
        uiStateManager.showDownloadCancelled()
        addSystemMessage("Download cancelled. You can restart the download anytime.")
    }

    /**
     * PermissionListener implementation
     */
    override fun onPermissionsGranted() {
        Log.d(TAG, "All permissions granted")
        modelManager.initialize()
    }

    override fun onPermissionsDenied(deniedPermissions: List<String>) {
        Log.w(TAG, "Permissions denied: ${deniedPermissions.joinToString()}")
        errorHandler.handlePermissionError(deniedPermissions)
        uiStateManager.disableMainFeatures()
    }

    override fun onPermissionsPermanentlyDenied(permissions: List<String>) {
        Log.w(TAG, "Permissions permanently denied: ${permissions.joinToString()}")
        permissionHandler.showPermanentPermissionDenialDialog()
    }

    /**
     * ImageProcessorListener implementation
     */
    override fun onImageSelected(bitmap: Bitmap) {
        Log.d(TAG, "Image selected: ${bitmap.width}x${bitmap.height}")
        uiStateManager.showImageSelected(bitmap)
    }

    override fun onImageError(error: String) {
        Log.e(TAG, "Image error: $error")
        errorHandler.handleImageError(error)
    }

    override fun onImageSelectionCancelled() {
        Log.d(TAG, "Image selection cancelled by user")
        // No action needed, just log
    }

    /**
     * Handle permission request results from the system
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandler.handlePermissionResult(requestCode, permissions, grantResults)
    }

}
