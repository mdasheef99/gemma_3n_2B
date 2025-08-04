package com.gemma3n.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gemma3n.app.databinding.ActivityModelSettingsBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity for managing AI model settings and storage
 */
class ModelSettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ModelSettingsActivity"
    }
    
    private lateinit var binding: ActivityModelSettingsBinding
    private lateinit var modelManager: ModelManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize model manager
        modelManager = ModelManager(this)
        
        setupUI()
        updateModelInfo()
    }
    
    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Model Settings"
        
        // Set up buttons
        binding.deleteModelButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        
        binding.redownloadModelButton.setOnClickListener {
            showRedownloadConfirmationDialog()
        }
        
        binding.refreshInfoButton.setOnClickListener {
            updateModelInfo()
        }
    }
    
    private fun updateModelInfo() {
        Log.d(TAG, "Updating model information")
        
        val modelPath = modelManager.getModelPath()
        val modelFile = File(modelPath)
        
        if (modelFile.exists()) {
            // Model exists - show details
            binding.modelStatusText.text = "✅ Model Available"
            binding.modelLocationText.text = "📁 Location: ${modelFile.absolutePath}"
            
            val sizeGB = modelFile.length() / (1024f * 1024f * 1024f)
            binding.modelSizeText.text = "📦 Size: %.2f GB".format(sizeGB)
            
            val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(modelFile.lastModified()))
            binding.modelDateText.text = "📅 Downloaded: $lastModified"
            
            // Enable management buttons
            binding.deleteModelButton.isEnabled = true
            binding.redownloadModelButton.isEnabled = true
            binding.deleteModelButton.alpha = 1.0f
            binding.redownloadModelButton.alpha = 1.0f
            
        } else {
            // Model missing
            binding.modelStatusText.text = "❌ Model Not Found"
            binding.modelLocationText.text = "📁 Location: Not available"
            binding.modelSizeText.text = "📦 Size: 0 GB"
            binding.modelDateText.text = "📅 Downloaded: Never"
            
            // Disable delete button, enable redownload
            binding.deleteModelButton.isEnabled = false
            binding.redownloadModelButton.isEnabled = true
            binding.deleteModelButton.alpha = 0.5f
            binding.redownloadModelButton.alpha = 1.0f
        }
        
        // Update storage info
        updateStorageInfo()
    }
    
    private fun updateStorageInfo() {
        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) {
            val stat = android.os.StatFs(externalDir.path)
            val availableBytes = stat.availableBytes
            val totalBytes = stat.totalBytes
            val usedBytes = totalBytes - availableBytes
            
            val availableGB = availableBytes / (1024f * 1024f * 1024f)
            val totalGB = totalBytes / (1024f * 1024f * 1024f)
            val usedGB = usedBytes / (1024f * 1024f * 1024f)
            
            binding.storageInfoText.text = "💾 Storage: %.1f GB used / %.1f GB total\n" +
                    "🆓 Available: %.1f GB".format(usedGB, totalGB, availableGB)
            
            // Show warning if low storage
            if (availableGB < 4.0f) {
                binding.storageWarningText.visibility = View.VISIBLE
                binding.storageWarningText.text = "⚠️ Low storage space! Consider freeing up space for optimal performance."
            } else {
                binding.storageWarningText.visibility = View.GONE
            }
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete AI Model")
            .setMessage("Are you sure you want to delete the AI model? This will free up storage space but you'll need to download it again to use AI features.")
            .setPositiveButton("Delete") { _, _ ->
                deleteModel()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showRedownloadConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Redownload AI Model")
            .setMessage("This will delete the current model and download a fresh copy. This may take several minutes.")
            .setPositiveButton("Redownload") { _, _ ->
                redownloadModel()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteModel() {
        lifecycleScope.launch {
            try {
                val modelPath = modelManager.getModelPath()
                val modelFile = File(modelPath)
                val tempFile = File(modelPath + ".tmp")
                
                var deleted = false
                if (modelFile.exists()) {
                    deleted = modelFile.delete()
                }
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                
                if (deleted) {
                    Log.d(TAG, "Model deleted successfully")
                    updateModelInfo()
                    
                    // Show success message
                    AlertDialog.Builder(this@ModelSettingsActivity)
                        .setTitle("Success")
                        .setMessage("AI model deleted successfully. Storage space has been freed.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    throw Exception("Failed to delete model file")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting model", e)
                AlertDialog.Builder(this@ModelSettingsActivity)
                    .setTitle("Error")
                    .setMessage("Failed to delete model: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun redownloadModel() {
        // Delete existing model first
        val modelPath = modelManager.getModelPath()
        val modelFile = File(modelPath)
        val tempFile = File(modelPath + ".tmp")
        
        if (modelFile.exists()) {
            modelFile.delete()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }
        
        // Return to main activity and trigger download
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("trigger_download", true)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
