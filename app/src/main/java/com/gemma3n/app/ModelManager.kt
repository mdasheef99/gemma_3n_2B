package com.gemma3n.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * ModelManager handles all Gemma 3n model lifecycle operations
 * Separated from MainActivity for better maintainability and testing
 */
class ModelManager(private val context: Context) {
    
    /**
     * Enum representing different states of the AI model
     */
    enum class ModelStatus {
        CHECKING,           // Currently checking model availability
        AVAILABLE,          // Model file exists and is valid
        MISSING,            // Model file not found
        CORRUPTED,          // Model file exists but is corrupted
        OUTDATED,           // Model file is an older version
        DOWNLOADING,        // Currently downloading model
        DOWNLOAD_FAILED,    // Download process failed
        INITIALIZING,       // Model is being loaded into memory
        READY,              // Model is loaded and ready for inference
        ERROR               // General error state
    }
    
    /**
     * Interface for listening to model status changes and events
     */
    interface ModelStatusListener {
        /**
         * Called when model status changes
         * @param status The new model status
         */
        fun onStatusChanged(status: ModelStatus)
        
        /**
         * Called during model download progress
         * @param percentage Download percentage (0-100)
         * @param downloadedMB Downloaded size in MB
         * @param totalMB Total size in MB
         * @param speedMBps Download speed in MB/s
         * @param etaSeconds Estimated time remaining in seconds
         */
        fun onDownloadProgress(percentage: Int, downloadedMB: Long, totalMB: Long, speedMBps: Float, etaSeconds: Long)
        
        /**
         * Called when model is fully loaded and ready for inference
         */
        fun onModelReady()
        
        /**
         * Called when an error occurs in model operations
         * @param error Human-readable error message
         */
        fun onError(error: String)

        /**
         * Called when download is cancelled by user
         */
        fun onDownloadCancelled()
    }
    
    // Private properties for model management
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isModelLoaded = false
    private var isInitialized = false
    private lateinit var downloadManager: ModelDownloadManager
    private var listener: ModelStatusListener? = null
    
    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"
        private const val MIN_MODEL_SIZE_BYTES = 2_000_000_000L // 2GB minimum (3.1GB expected)
    }
    
    /**
     * Set the listener for model status updates
     * @param listener The listener to receive model events
     */
    fun setListener(listener: ModelStatusListener) {
        this.listener = listener
        Log.d(TAG, "ModelStatusListener set")
    }
    
    /**
     * Initialize the ModelManager and start model checking process
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "ModelManager already initialized, skipping duplicate initialization")
            return
        }

        Log.d(TAG, "Initializing ModelManager...")
        isInitialized = true
        downloadManager = ModelDownloadManager(context)
        checkModelStatus()
    }
    
    /**
     * Check current model status and notify listener
     */
    private fun checkModelStatus() {
        Log.d(TAG, "Checking model status...")
        listener?.onStatusChanged(ModelStatus.CHECKING)

        if (!isModelAvailable()) {
            Log.d(TAG, "Model file not found")
            listener?.onStatusChanged(ModelStatus.MISSING)
        } else {
            Log.d(TAG, "Model file found, starting initialization")
            initializeModel()
        }
    }

    /**
     * Check if model file is available and valid in external storage
     * Moved from MainActivity with integrity checks
     */
    private fun isModelAvailable(): Boolean {
        val modelFile = File(getModelPath())
        val exists = modelFile.exists()
        val hasValidSize = modelFile.length() > MIN_MODEL_SIZE_BYTES
        val isIntact = exists && hasValidSize && isModelFileIntact(modelFile)

        Log.d(TAG, "Model availability check: exists=$exists, size=${modelFile.length()}, intact=$isIntact")

        if (exists && !isIntact) {
            Log.w(TAG, "Model file exists but is corrupted - will be cleaned up")
            cleanupCorruptedModel(modelFile)
            return false
        }

        return isIntact
    }

    /**
     * Verify model file integrity
     */
    private fun isModelFileIntact(modelFile: File): Boolean {
        return try {
            // Check if file size matches expected minimum
            val fileSize = modelFile.length()
            if (fileSize < MIN_MODEL_SIZE_BYTES) {
                Log.w(TAG, "Model file too small: $fileSize bytes (minimum: $MIN_MODEL_SIZE_BYTES)")
                return false
            }

            // Basic file header validation for .task files
            modelFile.inputStream().use { stream ->
                val header = ByteArray(8)
                val bytesRead = stream.read(header)
                if (bytesRead < 8) {
                    Log.w(TAG, "Cannot read model file header")
                    return false
                }
                // Basic validation - .task files should have specific header patterns
                true // Simplified validation - in production, add proper header checks
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model file integrity", e)
            false
        }
    }

    /**
     * Clean up corrupted or incomplete model files
     */
    private fun cleanupCorruptedModel(modelFile: File) {
        try {
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                Log.d(TAG, "Corrupted model file cleanup: deleted=$deleted")

                // Also clean up any temporary download files
                val parentDir = modelFile.parentFile
                parentDir?.listFiles { file ->
                    file.name.startsWith(MODEL_FILENAME) && file.name.contains(".tmp")
                }?.forEach { tempFile ->
                    val tempDeleted = tempFile.delete()
                    Log.d(TAG, "Temporary file cleanup: ${tempFile.name} deleted=$tempDeleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up corrupted model", e)
        }
    }

    /**
     * Clean up failed download files
     */
    private fun cleanupFailedDownload() {
        try {
            val modelFile = File(getModelPath())
            val parentDir = modelFile.parentFile

            // Delete main model file if it exists (likely incomplete)
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                Log.d(TAG, "Failed download cleanup: main file deleted=$deleted")
            }

            // Delete any temporary or partial download files
            parentDir?.listFiles { file ->
                file.name.startsWith(MODEL_FILENAME) ||
                file.name.contains(".tmp") ||
                file.name.contains(".part") ||
                file.name.contains(".download")
            }?.forEach { tempFile ->
                val tempDeleted = tempFile.delete()
                Log.d(TAG, "Failed download cleanup: ${tempFile.name} deleted=$tempDeleted")
            }

            Log.d(TAG, "Failed download cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up failed download", e)
        }
    }
    
    /**
     * Get the full path to the model file
     * @return Absolute path to the model file
     */
    fun getModelPath(): String {
        // PRODUCTION: Use app's external files directory for Samsung S23
        val productionPath = File(context.getExternalFilesDir(null), "gemma-3n-E2B-it-int4.task").absolutePath
        val productionFile = File(productionPath)

        Log.d(TAG, "Checking for model at production path: $productionPath")
        if (productionFile.exists()) {
            Log.d(TAG, "Using production model file: $productionPath (${productionFile.length()} bytes)")
            return productionPath
        }

        // EMULATOR TESTING: Use pre-downloaded model file from /data/local/tmp/
        val testPath = "/data/local/tmp/gemma-model.task"
        val testFile = File(testPath)

        Log.d(TAG, "Checking for test model at: $testPath")
        if (testFile.exists()) {
            Log.d(TAG, "Using test model file: $testPath (${testFile.length()} bytes)")
            return testPath
        }

        // Fallback to normal download location
        val fallbackPath = File(context.getExternalFilesDir(null), MODEL_FILENAME).absolutePath
        Log.d(TAG, "No pre-existing model found, using fallback path: $fallbackPath")
        return fallbackPath
    }
    
    /**
     * Initialize the Gemma 3n model from external storage
     * Moved from MainActivity - following DataCamp tutorial approach exactly
     */
    private fun initializeModel() {
        Log.d(TAG, "Starting model initialization...")
        listener?.onStatusChanged(ModelStatus.INITIALIZING)

        // Use coroutine scope from context if available
        if (context is androidx.lifecycle.LifecycleOwner) {
            val lifecycleScope = (context as androidx.lifecycle.LifecycleOwner).lifecycleScope
            lifecycleScope.launch {
                performModelInitialization()
            }
        } else {
            // Fallback for non-lifecycle contexts
            kotlinx.coroutines.GlobalScope.launch {
                performModelInitialization()
            }
        }
    }

    /**
     * Perform the actual model initialization
     */
    private suspend fun performModelInitialization() {
        try {
            withContext(Dispatchers.IO) {
                val modelPath = getModelPath()
                Log.d(TAG, "=== STARTING MODEL INITIALIZATION ===")
                Log.d(TAG, "Model path: $modelPath")

                // COMPREHENSIVE LOGGING - File Verification
                val modelFile = File(modelPath)
                Log.d(TAG, "Model file exists: ${modelFile.exists()}")
                if (modelFile.exists()) {
                    Log.d(TAG, "Model file size: ${modelFile.length()} bytes (${modelFile.length() / 1024 / 1024} MB)")
                    Log.d(TAG, "Model file readable: ${modelFile.canRead()}")
                    Log.d(TAG, "Model file absolute path: ${modelFile.absolutePath}")
                } else {
                    Log.e(TAG, "MODEL FILE DOES NOT EXIST!")
                    throw FileNotFoundException("Model file not found at: $modelPath")
                }

                Log.d(TAG, "Model file found, creating LlmInference options...")

                // COMPREHENSIVE LOGGING - MediaPipe Configuration
                Log.d(TAG, "=== CONFIGURING MEDIAPIPE LLM OPTIONS ===")
                Log.d(TAG, "Backend: CPU (changed from GPU for compatibility)")
                Log.d(TAG, "Max tokens: 512")
                Log.d(TAG, "Vision modality: DISABLED (for basic text testing)")

                // Create LLM Inference options - MODIFIED FOR BASIC TESTING
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
                    .setPreferredBackend(LlmInference.Backend.CPU) // CHANGED: Use CPU instead of GPU
                    .setMaxNumImages(0) // CHANGED: Disable image support for basic testing
                    .build()

                Log.d(TAG, "LlmInference options created successfully")

                Log.d(TAG, "Creating LlmInference engine...")
                llmInference = LlmInference.createFromOptions(context, options)
                Log.d(TAG, "LlmInference engine created successfully!")

                Log.d(TAG, "Creating LlmInferenceSession with basic text configuration...")
                Log.d(TAG, "Session parameters: TopK=40, Temperature=0.7f, Vision=DISABLED")

                // Create inference session - MODIFIED FOR BASIC TEXT TESTING
                llmSession = LlmInferenceSession.createFromOptions(
                    llmInference!!,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(40)
                        .setTemperature(0.7f)
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(false) // CHANGED: Disable vision for basic testing
                                .build()
                        )
                        .build()
                )
                Log.d(TAG, "LlmInferenceSession created successfully!")

                isModelLoaded = true
                Log.d(TAG, "=== MODEL INITIALIZATION COMPLETED SUCCESSFULLY! ===")
                Log.d(TAG, "Model is ready for text-only inference")
            }

            withContext(Dispatchers.Main) {
                listener?.onStatusChanged(ModelStatus.READY)
                listener?.onModelReady()
                Log.d(TAG, "Model ready for inference - UI updated")
            }

        } catch (e: Exception) {
            Log.e(TAG, "=== MODEL INITIALIZATION FAILED ===", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")

            withContext(Dispatchers.Main) {
                listener?.onStatusChanged(ModelStatus.ERROR)
                listener?.onError(cleanUpErrorMessage(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Start model download process with cleanup
     * Moved from MainActivity with UI separation
     */
    fun startDownload() {
        Log.d(TAG, "Starting model download...")

        // Check if already downloading to prevent race conditions
        if (getCurrentStatus() == ModelStatus.DOWNLOADING) {
            Log.d(TAG, "Download already in progress, ignoring duplicate request")
            return
        }

        // Only clean up if the model is actually corrupted, not just because we're starting a download
        val modelFile = File(getModelPath())
        if (modelFile.exists() && !isModelAvailable()) {
            Log.d(TAG, "Cleaning up corrupted model file before fresh download")
            cleanupCorruptedModel(modelFile)
        } else if (modelFile.exists() && isModelAvailable()) {
            Log.d(TAG, "Valid model file already exists, skipping download")
            listener?.onStatusChanged(ModelStatus.READY)
            return
        }

        listener?.onStatusChanged(ModelStatus.DOWNLOADING)

        // Use coroutine scope from context if available
        if (context is androidx.lifecycle.LifecycleOwner) {
            val lifecycleScope = (context as androidx.lifecycle.LifecycleOwner).lifecycleScope
            lifecycleScope.launch {
                performModelDownload()
            }
        } else {
            // Fallback for non-lifecycle contexts
            kotlinx.coroutines.GlobalScope.launch {
                performModelDownload()
            }
        }
    }

    /**
     * Cancel ongoing model download
     */
    fun cancelDownload() {
        Log.d(TAG, "Cancelling model download...")
        downloadManager.cancelDownload()
    }

    /**
     * Perform the actual model download
     * Moved from MainActivity
     */
    private suspend fun performModelDownload() {
        downloadManager.downloadGemma3nE2B(object : ModelDownloadManager.DownloadCallback {
            override fun onStarted() {
                Log.d(TAG, "Download started")
                // UI updates are handled by the listener
            }

            override fun onProgress(bytesDownloaded: Long, totalBytes: Long, percentage: Int, speedMBps: Float, etaSeconds: Long) {
                val downloadedMB = bytesDownloaded / 1024 / 1024
                val totalMB = totalBytes / 1024 / 1024

                Log.d(TAG, "Download progress: $percentage% ($downloadedMB/$totalMB MB, ${speedMBps}MB/s, ETA: ${etaSeconds}s)")
                listener?.onDownloadProgress(percentage, downloadedMB, totalMB, speedMBps, etaSeconds)
            }

            override fun onCancelled() {
                Log.d(TAG, "Download was cancelled")
                listener?.onDownloadCancelled()
                listener?.onStatusChanged(ModelStatus.MISSING)
            }

            override fun onSuccess(filePath: String) {
                Log.d(TAG, "Download completed successfully: $filePath")
                // Automatically initialize the model after download
                initializeModel()
            }

            override fun onError(error: String) {
                Log.e(TAG, "Download failed: $error")

                // Clean up any partially downloaded files
                cleanupFailedDownload()

                listener?.onStatusChanged(ModelStatus.DOWNLOAD_FAILED)
                listener?.onError(error)
            }
        })
    }

    /**
     * Process a text-only question using Gemma 3n model (no image)
     * @param question The text question to ask
     * @return AI response string
     */
    suspend fun processTextQuestion(question: String): String {
        return try {
            Log.d(TAG, "Starting Gemma 3n text-only inference for question: $question")

            if (!isReady()) {
                return "AI model is not ready yet. Please wait for initialization to complete."
            }

            // CRITICAL FIX: Create a fresh session for each query to prevent context overflow
            val inference = llmInference ?: return "Model inference engine not available"

            // Create fresh session with clean context for each query
            val freshSession = LlmInferenceSession.createFromOptions(
                inference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.7f)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(false)
                            .build()
                    )
                    .build()
            )
            Log.d(TAG, "Created fresh session for query to prevent context overflow")

            // Add the text query to the fresh session
            freshSession.addQueryChunk(question)

            // Generate response using the fresh session
            val response = freshSession.generateResponse()
            Log.d(TAG, "Generated text response: ${response?.take(100)}...")

            response ?: "I apologize, but I couldn't generate a response. Please try again."

        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemma 3n text inference", e)
            "I encountered an error processing your message. Please try again."
        }
    }

    /**
     * Process image question using the loaded model
     * Moved from MainActivity - following DataCamp tutorial pattern exactly
     * @param question The question to ask about the image
     * @param bitmap The image to analyze
     * @return AI response string
     */
    suspend fun processImageQuestion(question: String, bitmap: Bitmap): String {
        return try {
            Log.d(TAG, "Starting Gemma 3n inference for question: $question")
            Log.d(TAG, "Image dimensions: ${bitmap.width}x${bitmap.height}")

            if (!isReady()) {
                throw IllegalStateException("Model is not ready for inference")
            }

            // Convert bitmap to MediaPipe image
            val mpImage = BitmapImageBuilder(bitmap).build()
            Log.d(TAG, "Created MediaPipe image")

            // Use session-based multimodal processing (tutorial pattern)
            // Add the text query first
            llmSession!!.addQueryChunk("Analyze this image and answer the following question: $question")
            Log.d(TAG, "Added query chunk to session")

            // Add the image to the session
            llmSession!!.addImage(mpImage)
            Log.d(TAG, "Added image to session")

            // Generate response using the session
            val response = llmSession!!.generateResponse()
            Log.d(TAG, "Generated response: ${response?.take(100)}...")

            response ?: "No response generated"

        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemma 3n inference", e)
            throw e
        }
    }
    
    /**
     * Retry model initialization (public method for UI)
     */
    fun retryInitialization() {
        Log.d(TAG, "Retrying model initialization...")
        checkModelStatus()
    }

    /**
     * Get current model status
     * @return Current ModelStatus
     */
    fun getCurrentStatus(): ModelStatus {
        return when {
            !isModelAvailable() -> ModelStatus.MISSING
            !isModelLoaded -> ModelStatus.AVAILABLE
            else -> ModelStatus.READY
        }
    }
    
    /**
     * Check if model is ready for inference
     * @return true if model is loaded and ready
     */
    fun isReady(): Boolean {
        return isModelLoaded && llmInference != null && llmSession != null
    }
    
    /**
     * Clean up MediaPipe error messages for user display
     * Moved from MainActivity
     */
    private fun cleanUpErrorMessage(message: String): String {
        return when {
            message.contains("Unable to open zip archive") -> "Model file is corrupted or incomplete"
            message.contains("Failed to initialize engine") -> "Model file format is invalid"
            message.contains("Model file not found") -> "Model file is missing"
            else -> message.take(100) // Limit length
        }
    }

    /**
     * Clean up resources when ModelManager is no longer needed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ModelManager resources...")
        llmInference = null
        llmSession = null
        isModelLoaded = false
        listener = null
    }
}
