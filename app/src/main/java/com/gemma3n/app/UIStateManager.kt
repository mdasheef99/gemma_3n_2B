package com.gemma3n.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.view.View
import com.gemma3n.app.databinding.ActivityMainBinding
import java.io.File

/**
 * UIStateManager handles all UI state coordination and updates
 * Separated from MainActivity for better maintainability and testing
 */
class UIStateManager(private val binding: ActivityMainBinding, private val context: Context) {

    companion object {
        private const val TAG = "UIStateManager"
        private const val REQUIRED_STORAGE_GB = 3.5f // 3.1GB model + 0.4GB buffer
        private const val TEST_MODE = false // Should match ModelDownloadManager.TEST_MODE
        private const val TEST_REQUIRED_STORAGE_GB = 0.1f // 100MB for test mode
    }

    /**
     * Check if running on emulator
     */
    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
               Build.MODEL.contains("Emulator") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               Build.PRODUCT.contains("sdk") ||
               Build.HARDWARE.contains("goldfish")
    }

    /**
     * Get available storage space in GB
     */
    private fun getAvailableStorageGB(): Float {
        return try {
            val externalDir = context.getExternalFilesDir(null)
            val stat = StatFs(externalDir?.path ?: Environment.getExternalStorageDirectory().path)
            val availableBytes = stat.availableBytes
            availableBytes / (1024f * 1024f * 1024f) // Convert to GB
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage info", e)
            0f
        }
    }

    /**
     * Check if sufficient storage is available
     */
    private fun hasSufficientStorage(): Boolean {
        val requiredGB = if (TEST_MODE) TEST_REQUIRED_STORAGE_GB else REQUIRED_STORAGE_GB
        return getAvailableStorageGB() >= requiredGB
    }

    /**
     * Update storage information display
     */
    private fun updateStorageInfo() {
        val availableGB = getAvailableStorageGB()
        val hasSpace = hasSufficientStorage()
        val requiredGB = if (TEST_MODE) TEST_REQUIRED_STORAGE_GB else REQUIRED_STORAGE_GB
        val modelSizeText = if (TEST_MODE) "10MB (Test Mode)" else "3.1GB"

        val storageText = if (hasSpace) {
            "📦 Model Size: $modelSizeText\n💾 Available Space: %.1f GB ✅".format(availableGB)
        } else {
            "📦 Model Size: $modelSizeText\n💾 Available Space: %.1f GB ❌\n⚠️ Need %.1f GB more space".format(
                availableGB, requiredGB - availableGB
            )
        }

        binding.storageInfo.text = storageText
    }
    
    /**
     * Show the initial welcome state when app starts
     */
    fun showInitialState() {
        Log.d(TAG, "Showing initial welcome state")

        // Don't hide download section initially - let model status determine what to show
        // The model status check will determine whether to show download section or chat

        // Initially disable all AI-dependent features
        disableAIFeatures()

        binding.progressBar.visibility = View.VISIBLE
    }

    /**
     * Disable all AI-dependent features when model is not ready
     */
    private fun disableAIFeatures() {
        // Disable image selection buttons
        binding.selectImageButton.isEnabled = false
        binding.takePhotoButton.isEnabled = false
        binding.selectImageButton.alpha = 0.5f
        binding.takePhotoButton.alpha = 0.5f
        binding.selectImageButton.text = "📸 Select Image (AI Loading...)"
        binding.takePhotoButton.text = "📷 Take Photo (AI Loading...)"

        // Disable chat input
        binding.messageInput.isEnabled = false
        binding.sendButton.isEnabled = false
        binding.messageInput.alpha = 0.5f
        binding.sendButton.alpha = 0.5f
        binding.messageInput.hint = "AI model loading..."

        // Keep settings button always enabled for model management
        binding.settingsButton.isEnabled = true
        binding.settingsButton.alpha = 1.0f
    }

    /**
     * Enable all AI-dependent features when model is ready
     */
    private fun enableAIFeatures() {
        // Enable image selection buttons
        binding.selectImageButton.isEnabled = true
        binding.takePhotoButton.isEnabled = true
        binding.selectImageButton.alpha = 1.0f
        binding.takePhotoButton.alpha = 1.0f
        binding.selectImageButton.text = "📸 Select Image"
        binding.takePhotoButton.text = "📷 Take Photo"

        // Enable chat input
        binding.messageInput.isEnabled = true
        binding.sendButton.isEnabled = true
        binding.messageInput.alpha = 1.0f
        binding.sendButton.alpha = 1.0f
        binding.messageInput.hint = "Type your message..."
    }

    /**
     * Show download section with storage information
     */
    private fun showDownloadSection() {
        binding.downloadSection.visibility = View.VISIBLE
        binding.chatRecyclerView.visibility = View.GONE
        updateStorageInfo()

        // Enable download button only if sufficient storage
        val hasSpace = hasSufficientStorage()
        binding.downloadModelButton.isEnabled = hasSpace
        binding.downloadModelButton.alpha = if (hasSpace) 1.0f else 0.5f

        if (!hasSpace) {
            binding.downloadModelButton.text = "❌ Insufficient Storage Space"
        }
    }

    /**
     * Hide download section and show chat
     */
    private fun hideDownloadSection() {
        binding.downloadSection.visibility = View.GONE
        binding.chatRecyclerView.visibility = View.VISIBLE
    }
    
    /**
     * Update UI based on model status changes
     * @param status The current model status
     */
    fun updateModelStatus(status: ModelManager.ModelStatus) {
        Log.d(TAG, "Updating UI for model status: $status")
        
        when (status) {
            ModelManager.ModelStatus.CHECKING -> showModelChecking()
            ModelManager.ModelStatus.AVAILABLE -> showModelAvailable()
            ModelManager.ModelStatus.MISSING -> showModelMissing()
            ModelManager.ModelStatus.CORRUPTED -> showModelCorrupted()
            ModelManager.ModelStatus.DOWNLOADING -> showModelDownloading()
            ModelManager.ModelStatus.DOWNLOAD_FAILED -> showDownloadFailed()
            ModelManager.ModelStatus.INITIALIZING -> showModelInitializing()
            ModelManager.ModelStatus.READY -> showModelReady()
            ModelManager.ModelStatus.ERROR -> showModelError()
            else -> showUnknownState()
        }
    }
    
    /**
     * Show model checking state
     */
    private fun showModelChecking() {
        binding.progressBar.visibility = View.VISIBLE
        hideDownloadSection()
        disableAIFeatures()
    }
    
    /**
     * Show model available state (found but not loaded)
     */
    private fun showModelAvailable() {
        binding.progressBar.visibility = View.VISIBLE
        hideDownloadSection()
        disableAIFeatures()
    }

    /**
     * Show model missing state - display download UI
     */
    private fun showModelMissing() {
        Log.d(TAG, "Showing model missing state - displaying download UI")
        binding.progressBar.visibility = View.GONE

        // Force show download section
        binding.downloadSection.visibility = View.VISIBLE
        binding.chatRecyclerView.visibility = View.GONE
        updateStorageInfo()

        disableAIFeatures()

        // Reset download UI to initial state
        binding.downloadProgressBar.visibility = View.GONE
        binding.downloadProgressText.visibility = View.GONE
        binding.cancelDownloadButton.visibility = View.GONE
        binding.downloadModelButton.visibility = View.VISIBLE

        // Enable download button only if sufficient storage
        val hasSpace = hasSufficientStorage()
        binding.downloadModelButton.isEnabled = hasSpace
        binding.downloadModelButton.alpha = if (hasSpace) 1.0f else 0.5f

        if (!hasSpace) {
            binding.downloadModelButton.text = "❌ Insufficient Storage Space"
        } else {
            binding.downloadModelButton.text = "📥 Download AI Model (3.1GB) for Offline Use"
        }

        Log.d(TAG, "Download section visibility set to VISIBLE")
    }
    
    /**
     * Show model corrupted state
     */
    private fun showModelCorrupted() {
        binding.progressBar.visibility = View.GONE
        binding.responseText.text = "⚠️ Model File Issue Detected\n\n" +
                "🚨 The Gemma 3n model file appears to be corrupted or incomplete.\n\n" +
                "🔧 Recommended Actions:\n" +
                "• Re-download the model file\n" +
                "• Check available storage space\n" +
                "• Ensure stable internet connection\n\n" +
                "💡 Tap 'Re-download Model' to fix this issue."
        
        binding.askButton.text = "🔄 Re-download Model"
        binding.askButton.isEnabled = true
    }
    
    /**
     * Show model downloading state
     */
    private fun showModelDownloading() {
        showDownloadSection()
        disableAIFeatures()

        // Update download UI for active download
        binding.downloadModelButton.text = "⏳ Starting Download..."
        binding.downloadModelButton.isEnabled = false
        binding.downloadProgressBar.visibility = View.VISIBLE
        binding.downloadProgressText.visibility = View.VISIBLE
        binding.downloadProgressText.text = "📊 Initializing download...\n⚡ Connecting to server..."
        binding.cancelDownloadButton.visibility = View.VISIBLE
    }
    
    /**
     * Show download failed state with specific error message
     */
    fun showDownloadFailed(errorMessage: String? = null) {
        showDownloadSection()
        disableAIFeatures()

        // Reset download UI to failed state
        binding.downloadProgressBar.visibility = View.GONE
        binding.downloadProgressText.visibility = View.VISIBLE

        // Provide specific error message or generic fallback
        val specificError = when {
            errorMessage?.contains("internet", ignoreCase = true) == true ||
            errorMessage?.contains("connection", ignoreCase = true) == true ||
            errorMessage?.contains("network", ignoreCase = true) == true ->
                "❌ Network Error\n📶 Check your Wi-Fi connection and try again"

            errorMessage?.contains("storage", ignoreCase = true) == true ||
            errorMessage?.contains("space", ignoreCase = true) == true ->
                "❌ Storage Error\n💾 Free up ${REQUIRED_STORAGE_GB} GB of storage space"

            errorMessage?.contains("timeout", ignoreCase = true) == true ->
                "❌ Connection Timeout\n⏱️ Check your internet speed and try again"

            errorMessage?.contains("authentication", ignoreCase = true) == true ||
            errorMessage?.contains("access denied", ignoreCase = true) == true ->
                "❌ Access Error\n🔐 Model access issue - please try again later"

            else -> "❌ Download Failed\n🔄 Please check your connection and try again"
        }

        binding.downloadProgressText.text = "$specificError\n\n🧹 Cleanup completed automatically"
        binding.cancelDownloadButton.visibility = View.GONE
        binding.downloadModelButton.visibility = View.VISIBLE
        binding.downloadModelButton.text = "🔄 Retry Download"
        binding.downloadModelButton.isEnabled = true
    }

    /**
     * Show download completion notification
     */
    fun showDownloadCompleted() {
        hideDownloadSection()
        enableAIFeatures()

        // Show temporary success message in chat or as toast
        binding.progressBar.visibility = View.GONE

        // Could add a success message to chat here if needed
        Log.d(TAG, "Download completed successfully - AI features enabled")
    }

    /**
     * Show download cancelled state
     */
    fun showDownloadCancelled() {
        showDownloadSection()
        disableAIFeatures()

        // Reset download UI to cancelled state
        binding.downloadProgressBar.visibility = View.GONE
        binding.downloadProgressText.visibility = View.VISIBLE
        binding.downloadProgressText.text = "❌ Download Cancelled\n" +
                "🧹 Partial files cleaned up\n" +
                "💡 Tap below to start fresh download"
        binding.cancelDownloadButton.visibility = View.GONE
        binding.downloadModelButton.visibility = View.VISIBLE
        binding.downloadModelButton.text = "📥 Download AI Model (3.1GB) for Offline Use"
        binding.downloadModelButton.isEnabled = true
    }
    
    /**
     * Show model initializing state
     */
    private fun showModelInitializing() {
        binding.progressBar.visibility = View.VISIBLE
        binding.responseText.text = "🔄 Initializing Gemma 3n AI\n\n" +
                "🧠 Loading model into memory...\n" +
                "⚙️ Configuring AI parameters...\n" +
                "🔧 Preparing for inference...\n\n" +
                "⏱️ Almost ready!"
        
        binding.askButton.isEnabled = false
        binding.askButton.text = "⏳ Initializing..."
    }
    
    /**
     * Show model ready state
     */
    private fun showModelReady() {
        binding.progressBar.visibility = View.GONE
        hideDownloadSection()
        enableAIFeatures()

        // CRITICAL FIX: Show chat interface when model is ready
        binding.chatRecyclerView.visibility = View.VISIBLE
        binding.chatInputLayout.visibility = View.VISIBLE
        binding.messageInput.isEnabled = true
        binding.sendButton.isEnabled = true

        // Hide old UI elements
        binding.responseText.visibility = View.GONE
        binding.askButton.visibility = View.GONE
        binding.questionInput.visibility = View.GONE

        // HIDE OLD IMAGE BUTTONS - using integrated approach now
        // selectedImage removed from layout for cleaner UI
        binding.selectImageButton.visibility = View.GONE
        binding.takePhotoButton.visibility = View.GONE
        binding.titleText.visibility = View.GONE // Hide title for cleaner UI

        // Ensure settings button is always enabled
        binding.settingsButton.isEnabled = true
        binding.settingsButton.alpha = 1.0f

        Log.d(TAG, "Chat interface now visible and ready for text and image input")
    }
    
    /**
     * Show model error state
     */
    private fun showModelError() {
        binding.progressBar.visibility = View.GONE
        binding.responseText.text = "🤖 Gemma 3n Model Status\n\n" +
                "📊 Current Status: ❌ Initialization Failed\n" +
                "📁 Model File: Found but corrupted/invalid\n" +
                "🔧 Issue: Model failed to load properly\n\n" +
                "🧹 Automatic Actions:\n" +
                "• Corrupted model will be deleted\n" +
                "• Fresh download will be initiated\n" +
                "• Clean installation attempted\n\n" +
                "🛠️ Common Causes:\n" +
                "• Incomplete previous download\n" +
                "• File corruption during transfer\n" +
                "• Insufficient device memory\n\n" +
                "💡 Click below to download fresh model"

        binding.askButton.text = "📥 Retry Download"
        binding.askButton.isEnabled = true

        // Disable image interactions until model works
        binding.selectImageButton.isEnabled = false
        binding.takePhotoButton.isEnabled = false
        binding.selectImageButton.alpha = 0.5f
        binding.takePhotoButton.alpha = 0.5f
        binding.selectImageButton.text = "📸 Select Image (Download Required)"
        binding.takePhotoButton.text = "📷 Take Photo (Download Required)"
    }
    
    /**
     * Show unknown state fallback
     */
    private fun showUnknownState() {
        Log.w(TAG, "Unknown model status - showing fallback state")
        binding.progressBar.visibility = View.GONE
        binding.responseText.text = "🤖 Gemma 3n Impact\n\n" +
                "⚠️ Unknown system state\n" +
                "Please restart the app or contact support."
        
        binding.askButton.text = "🔄 Restart"
        binding.askButton.isEnabled = true
    }
    
    /**
     * Show image selected state (legacy method for compatibility)
     * @param bitmap The selected image bitmap
     */
    fun showImageSelected(bitmap: Bitmap) {
        Log.d(TAG, "Image selected - using integrated preview instead of legacy view")
        // Legacy selectedImage view removed - using integrated preview in MainActivity
    }
    
    /**
     * Show processing state when AI is analyzing
     */
    fun showProcessing() {
        Log.d(TAG, "Showing processing state")
        binding.progressBar.visibility = View.VISIBLE
        binding.responseText.text = "🤖 Gemma 3n Processing...\n\n" +
                "🖼️ Analyzing your image...\n" +
                "💭 Understanding your question...\n" +
                "🧠 Generating AI response...\n\n" +
                "⏱️ This may take 10-30 seconds"
        
        binding.askButton.isEnabled = false
        binding.askButton.text = "🤖 Processing..."
    }
    
    /**
     * Show AI response
     * @param response The AI-generated response
     */
    fun showResponse(response: String) {
        Log.d(TAG, "Showing AI response")
        binding.progressBar.visibility = View.GONE
        binding.responseText.text = "🤖 Gemma 3n Analysis:\n\n" +
                "$response\n\n" +
                "✨ Analysis complete! Ask another question or select a new image."
        
        binding.askButton.isEnabled = true
        binding.askButton.text = "🤖 Ask Gemma 3n"
    }
    
    /**
     * Update download progress
     * @param percentage Download percentage (0-100)
     * @param downloadedMB Downloaded size in MB
     * @param totalMB Total size in MB
     */
    fun updateDownloadProgress(percentage: Int, downloadedMB: Long, totalMB: Long) {
        val remainingMB = totalMB - downloadedMB
        val progressBar = "█".repeat(percentage / 5) + "░".repeat(20 - percentage / 5)

        binding.responseText.text = "📥 Downloading Gemma 3n Model\n\n" +
                "📊 Progress: $percentage%\n" +
                "[$progressBar]\n\n" +
                "📈 Downloaded: ${downloadedMB}MB / ${totalMB}MB\n" +
                "⏳ Remaining: ${remainingMB}MB\n\n" +
                "💡 Please keep the app open and connected to WiFi"
    }

    /**
     * Enable main features when model is ready
     */
    fun enableMainFeatures() {
        binding.selectImageButton.isEnabled = true
        binding.takePhotoButton.isEnabled = true
        binding.selectImageButton.text = "📸 Select Image"
        binding.takePhotoButton.text = "📷 Take Photo"
        Log.d(TAG, "Main features enabled")
    }

    /**
     * Disable main features when model is not ready
     */
    fun disableMainFeatures() {
        binding.selectImageButton.isEnabled = false
        binding.takePhotoButton.isEnabled = false
        binding.selectImageButton.text = "📸 Select Image (After Download)"
        binding.takePhotoButton.text = "📷 Take Photo (After Download)"
        Log.d(TAG, "Main features disabled")
    }

    /**
     * Update download progress with real-time information
     * @param percentage Download percentage (0-100)
     * @param downloadedMB Downloaded size in MB
     * @param totalMB Total size in MB
     * @param speedMBps Download speed in MB/s
     * @param etaSeconds Estimated time remaining in seconds
     */
    fun updateDownloadProgress(
        percentage: Int,
        downloadedMB: Long,
        totalMB: Long,
        speedMBps: Float,
        etaSeconds: Long
    ) {
        Log.d(TAG, "Updating download progress: $percentage%")

        // Update progress bar
        binding.downloadProgressBar.visibility = View.VISIBLE
        binding.downloadProgressBar.progress = percentage
        binding.downloadProgressBar.max = 100

        // Format ETA
        val etaText = when {
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }

        // Update progress text with dual display
        val progressText = "📊 Progress: $percentage% (${downloadedMB}MB / ${totalMB}MB)\n" +
                "⚡ Speed: %.1f MB/s\n".format(speedMBps) +
                "⏱️ Time Remaining: $etaText"

        binding.downloadProgressText.text = progressText
        binding.downloadProgressText.visibility = View.VISIBLE

        // Update download button text
        binding.downloadModelButton.text = "⏳ Downloading... $percentage%"
        binding.downloadModelButton.isEnabled = false

        // Show cancel button
        binding.cancelDownloadButton.visibility = View.VISIBLE
    }

    /**
     * Show error state with custom message
     * @param title Error title
     * @param message Error message
     */
    fun showError(title: String, message: String) {
        binding.progressBar.visibility = View.GONE
        binding.responseText.text = "❌ $title\n\n$message"
        binding.askButton.text = "🔄 Try Again"
        binding.askButton.isEnabled = true
        Log.d(TAG, "Error state shown: $title")
    }
}
