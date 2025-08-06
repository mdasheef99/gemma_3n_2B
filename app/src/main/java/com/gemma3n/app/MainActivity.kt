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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gemma3n.app.databinding.ActivityMainBinding
import com.gemma3n.app.data.Book
import com.gemma3n.app.repository.BookRepository
import com.gemma3n.app.ai.BookRecognitionParser
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
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

    // Database components
    private lateinit var bookRepository: BookRepository

    // Chat system components
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Inventory workflow state
    private var isPushToDatabaseMode = false
    private var pendingBooksList: List<String> = emptyList()
    private var currentBookIndex = 0

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

        // Initialize database repository
        bookRepository = BookRepository.getInstance(this)

        // Initialize chat system
        setupChatSystem()

        // Add test multilingual books for testing
        addTestMultilingualBooks()

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

        // Legacy image buttons (now hidden)
        binding.selectImageButton.setOnClickListener {
            imageProcessor.selectImage()
        }

        binding.takePhotoButton.setOnClickListener {
            imageProcessor.takePhoto()
        }

        // New integrated image upload button
        binding.attachImageButton.setOnClickListener {
            showImageSelectionDialog()
        }

        // Remove image button
        binding.removeImageButton.setOnClickListener {
            removeSelectedImage()
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

        // Set up inventory actions dropdown
        setupInventoryActionsDropdown()

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
     * Set up the inventory actions dropdown menu
     */
    private fun setupInventoryActionsDropdown() {
        Log.d(TAG, "Setting up inventory actions dropdown")

        // Create adapter for dropdown
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.inventory_actions,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inventoryActionsSpinner.adapter = adapter

        // Set up dropdown selection listener
        binding.inventoryActionsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                handleInventoryActionSelection(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    /**
     * Handle inventory action selection from dropdown
     */
    private fun handleInventoryActionSelection(position: Int) {
        when (position) {
            0 -> {
                // "Select Action" - do nothing
                return
            }
            1 -> {
                // "📷 Catalogue Image"
                handleCatalogueImageAction()
            }
            2 -> {
                // "💾 Push to Database"
                handlePushToDatabaseAction()
            }
            3 -> {
                // "➕ Add Book Manually"
                handleAddBookManuallyAction()
            }
            4 -> {
                // "🔍 Search Inventory"
                handleSearchInventoryAction()
            }
            5 -> {
                // "📊 Show Statistics"
                handleShowStatisticsAction()
            }
            6 -> {
                // "📚 View All Books"
                handleViewAllBooksAction()
            }
            7 -> {
                // "❓ Help"
                handleHelpAction()
            }
        }

        // Reset dropdown to "Select Action" after handling
        binding.inventoryActionsSpinner.setSelection(0)
    }

    // ==================== INVENTORY ACTION HANDLERS ====================

    /**
     * Handle "📷 Photo" action - Help with image upload
     */
    private fun handleCatalogueImageAction() {
        Log.d(TAG, "Photo action selected")

        addSystemMessage("📷 **Upload a photo of books**\n\nTap the 📎 button to take/select a photo. Then ask me to analyze it (e.g., 'What books are in this image?').\n\n**After analysis:** Copy the book list, then use dropdown → 💾 Save to add to database.")

        // Automatically trigger image selection
        showImageSelectionDialog()
    }

    /**
     * Handle "💾 Save" action - Step 2 of two-step process
     */
    private fun handlePushToDatabaseAction() {
        Log.d(TAG, "Save action selected")

        addSystemMessage("💾 **Step 2: Save books to database**\n\n1. Paste the book list you copied from photo analysis\n2. I'll ask for details for each book in format: `quantity, price, condition, location`\n\nExample: `2, 200, N, A1` (2 copies, ₹200 each, New condition, location A1)")

        setPushToDatabaseMode(true)
    }

    /**
     * Handle "➕ Add" action - simplified manual entry
     */
    private fun handleAddBookManuallyAction() {
        Log.d(TAG, "Add action selected")

        addSystemMessage("➕ **Add book manually**\n\nFormat: Add book: [Title] by [Author] price ₹[Price] qty [Quantity]\nExample: Add book: Atomic Habits by James Clear price ₹299 qty 5")
    }

    /**
     * Handle "🔍 Search Inventory" action
     */
    private fun handleSearchInventoryAction() {
        Log.d(TAG, "Search Inventory action selected")

        val guidanceMessage = """
            🔍 **Search Inventory Selected**

            **Search Options:**
            • Find books by [Author Name]
            • Search for [Book Title]
            • Books in location [Location]
            • Show [condition] books (new/used)

            **Examples:**
            • "Find books by James Clear"
            • "Search for Atomic Habits"
            • "Books in location A-1"
            • "Show used books"

            **Type your search query below:**
        """.trimIndent()

        addSystemMessage(guidanceMessage)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    /**
     * Handle "📊 Show Statistics" action
     */
    private fun handleShowStatisticsAction() {
        Log.d(TAG, "Show Statistics action selected")

        // Trigger inventory statistics directly
        lifecycleScope.launch {
            try {
                val totalBooks = bookRepository.getTotalBookCount().getOrNull() ?: 0
                val totalQuantity = bookRepository.getTotalQuantity().getOrNull() ?: 0
                val totalValue = bookRepository.getTotalInventoryValue().getOrNull() ?: 0.0

                val statsMessage = """
                    📊 **Inventory Statistics**

                    📚 **Total Books:** $totalBooks unique titles
                    📦 **Total Quantity:** $totalQuantity copies
                    💰 **Total Value:** ₹${String.format("%.2f", totalValue)}

                    **Additional Stats:**
                    • Average price per book: ₹${if (totalBooks > 0) String.format("%.2f", totalValue / totalBooks) else "0.00"}
                    • Average copies per title: ${if (totalBooks > 0) String.format("%.1f", totalQuantity.toDouble() / totalBooks) else "0.0"}

                    Type "Show low stock books" to see books with low inventory.
                """.trimIndent()

                addSystemMessage(statsMessage)
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

            } catch (e: Exception) {
                Log.e(TAG, "Error getting inventory statistics", e)
                addSystemMessage("⚠️ Error retrieving inventory statistics. Please try again.")
            }
        }
    }

    /**
     * Handle "📚 View All Books" action
     */
    private fun handleViewAllBooksAction() {
        Log.d(TAG, "View All Books action selected")

        lifecycleScope.launch {
            try {
                val allBooks = bookRepository.getAllBooks().first()

                if (allBooks.isEmpty()) {
                    addSystemMessage("📚 **No books in inventory yet.**\n\nUse 'Catalogue Image' or 'Add Book Manually' to add books to your inventory.")
                } else {
                    val booksMessage = StringBuilder("📚 **All Books in Inventory** (${allBooks.size} books)\n\n")

                    allBooks.forEachIndexed { index, book ->
                        booksMessage.append("${index + 1}. ")
                        booksMessage.append(book.getMultilingualInventoryString())
                        booksMessage.append("\n\n")
                    }

                    booksMessage.append("💡 Use 'Toresu' (Search) to find specific books.")

                    addSystemMessage(booksMessage.toString())
                }

                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving all books", e)
                addSystemMessage("⚠️ Error retrieving book list. Please try again.")
            }
        }
    }

    /**
     * Handle "❓ Help" action
     */
    private fun handleHelpAction() {
        Log.d(TAG, "Help action selected")

        val helpMessage = """
            ❓ **Bookstore Inventory Assistant Help**

            **🎯 Main Features:**

            **📷 Catalogue Image**
            • Upload photos of books to automatically extract book information
            • System analyzes images and provides structured book lists
            • Long-press AI responses to copy book data

            **💾 Push to Database**
            • Add extracted books to your inventory database
            • Provide additional details: quantity, price, condition, location
            • Format: `quantity, price, condition, location` (e.g., `2, 200, N, 2`)

            **➕ Add Book Manually**
            • Add individual books using natural language
            • Format: "Add book: [Title] by [Author] price ₹[Price] qty [Quantity]"

            **🔍 Search & View**
            • Search by author, title, location, or condition
            • View complete inventory with statistics
            • Get low stock alerts and analytics

            **💡 Tips:**
            • Use clear, well-lit photos for best image recognition
            • Condition codes: "N" = New, "U" = Used
            • All operations provide confirmation messages

            **Need specific help? Ask me anything about inventory management!**
        """.trimIndent()

        addSystemMessage(helpMessage)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    // ==================== PUSH TO DATABASE MODE HANDLING ====================

    /**
     * Set push to database mode state
     */
    private fun setPushToDatabaseMode(enabled: Boolean) {
        isPushToDatabaseMode = enabled
        if (!enabled) {
            pendingBooksList = emptyList()
            currentBookIndex = 0
        }
        Log.d(TAG, "Push to database mode: $enabled")
    }

    /**
     * Process message in push to database mode
     */
    private fun processPushToDatabaseMessage(message: String): Boolean {
        Log.d(TAG, "Processing push to database message: $message")

        if (pendingBooksList.isEmpty()) {
            // First message should contain the book list
            return parseBookListFromMessage(message)
        } else {
            // Subsequent messages should contain book details
            return processBookDetailsInput(message)
        }
    }

    /**
     * Parse book list from pasted message
     */
    private fun parseBookListFromMessage(message: String): Boolean {
        try {
            // Look for book entries in the message
            val bookEntries = mutableListOf<String>()
            val lines = message.split("\n")

            for (line in lines) {
                val trimmedLine = line.trim()
                // Look for lines that contain book information (title and author patterns)
                if (trimmedLine.contains(" by ") ||
                    (trimmedLine.contains(".") && (trimmedLine.contains("Title") || trimmedLine.contains("Author")))) {
                    bookEntries.add(trimmedLine)
                }
            }

            if (bookEntries.isEmpty()) {
                addSystemMessage("⚠️ **No book entries found in the pasted text.**\n\nPlease paste the book list you copied from the image analysis step.")
                return false
            }

            pendingBooksList = bookEntries
            currentBookIndex = 0

            val confirmationMessage = """
                ✅ **Found ${bookEntries.size} book(s) to add to database**

                **Books detected:**
                ${bookEntries.mapIndexed { index, book -> "${index + 1}. $book" }.joinToString("\n")}

                **Next Step:** I'll ask for details for each book.

                **Book 1 of ${bookEntries.size}:**
                ${bookEntries[0]}

                **Please provide details in format:** `quantity, price, condition, location`
                **Example:** `2, 200, N, 2` (2 copies, ₹200 each, New condition, location 2)
                **Condition codes:** N = New, U = Used
            """.trimIndent()

            addSystemMessage(confirmationMessage)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing book list", e)
            addSystemMessage("⚠️ **Error parsing book list.** Please try pasting the book list again.")
            return false
        }
    }

    /**
     * Process book details input
     */
    private fun processBookDetailsInput(message: String): Boolean {
        try {
            val details = message.trim().split(",").map { it.trim() }

            if (details.size != 4) {
                addSystemMessage("⚠️ **Invalid format.** Please provide exactly 4 values: `quantity, price, condition, location`\n\nExample: `2, 200, N, 2`")
                return false
            }

            val quantity = details[0].toIntOrNull()
            val price = details[1].toDoubleOrNull()
            val condition = details[2].uppercase()
            val location = details[3]

            // Validate inputs
            if (quantity == null || quantity <= 0) {
                addSystemMessage("⚠️ **Invalid quantity.** Please enter a positive number for quantity.")
                return false
            }

            if (price == null || price < 0) {
                addSystemMessage("⚠️ **Invalid price.** Please enter a valid price (can be 0).")
                return false
            }

            if (condition != "N" && condition != "U") {
                addSystemMessage("⚠️ **Invalid condition.** Please use 'N' for New or 'U' for Used.")
                return false
            }

            if (location.isBlank()) {
                addSystemMessage("⚠️ **Invalid location.** Please provide a location.")
                return false
            }

            // Store the book with details
            val bookEntry = pendingBooksList[currentBookIndex]
            val conditionFull = if (condition == "N") "New" else "Used"

            lifecycleScope.launch {
                try {
                    // Parse book title and author from the entry
                    val (title, author) = parseBookTitleAndAuthor(bookEntry)

                    if (title.isNotBlank() && author.isNotBlank()) {
                        val book = Book.fromManualEntry(title, author, price, quantity, location, conditionFull)
                        val result = bookRepository.insertBook(book)

                        if (result.isSuccess) {
                            val successMessage = "✅ **Book ${currentBookIndex + 1} added successfully!**\n" +
                                    "📖 $title by $author\n" +
                                    "💰 ₹$price | 📦 $quantity copies | 📍 $location | 🏷️ $conditionFull"

                            addSystemMessage(successMessage)

                            // Move to next book or finish
                            currentBookIndex++

                            if (currentBookIndex < pendingBooksList.size) {
                                // Ask for next book details
                                val nextBookMessage = """
                                    **Book ${currentBookIndex + 1} of ${pendingBooksList.size}:**
                                    ${pendingBooksList[currentBookIndex]}

                                    **Please provide details:** `quantity, price, condition, location`
                                """.trimIndent()

                                addSystemMessage(nextBookMessage)
                            } else {
                                // All books processed
                                val completionMessage = """
                                    🎉 **All ${pendingBooksList.size} books added to database successfully!**

                                    **Summary:**
                                    • ${pendingBooksList.size} books added
                                    • Total copies: ${quantity} (from last book - use 'Show Statistics' for full count)

                                    **What's next?**
                                    • Use 'View All Books' to see your inventory
                                    • Use 'Show Statistics' for complete analytics
                                    • Add more books using 'Catalogue Image' or 'Add Book Manually'
                                """.trimIndent()

                                addSystemMessage(completionMessage)
                                setPushToDatabaseMode(false)
                            }
                        } else {
                            addSystemMessage("⚠️ **Error adding book to database.** Please try again.")
                        }
                    } else {
                        addSystemMessage("⚠️ **Error parsing book title and author.** Please check the book entry format.")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error adding book to database", e)
                    addSystemMessage("⚠️ **Error adding book to database.** Please try again.")
                }
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error processing book details", e)
            addSystemMessage("⚠️ **Error processing book details.** Please check the format: `quantity, price, condition, location`")
            return false
        }
    }

    /**
     * Parse book title and author from book entry string
     */
    private fun parseBookTitleAndAuthor(bookEntry: String): Pair<String, String> {
        try {
            // Handle different formats from AI responses
            when {
                bookEntry.contains(" by ") -> {
                    val parts = bookEntry.split(" by ")
                    val title = parts[0].trim().removePrefix("1.").removePrefix("2.").removePrefix("3.").removePrefix("4.").removePrefix("5.").trim()
                    val author = parts[1].trim()
                    return Pair(title, author)
                }
                bookEntry.contains("Title:") && bookEntry.contains("Author:") -> {
                    val titleMatch = Regex("Title:?\\s*(.+?)(?=Author:|$)").find(bookEntry)
                    val authorMatch = Regex("Author:?\\s*(.+?)$").find(bookEntry)
                    val title = titleMatch?.groupValues?.get(1)?.trim() ?: ""
                    val author = authorMatch?.groupValues?.get(1)?.trim() ?: ""
                    return Pair(title, author)
                }
                else -> {
                    // Try to extract from numbered format like "1. Title 2. Author"
                    val parts = bookEntry.split(Regex("\\d+\\.")).filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        return Pair(parts[0].trim(), parts[1].trim())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing book title and author", e)
        }

        return Pair("", "")
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

        // Add user message to chat (check if image is attached)
        val hasImage = imageProcessor.getCurrentBitmap() != null
        val userMessage = if (hasImage) {
            ChatMessage.createUserMessageWithImage(messageText)
        } else {
            ChatMessage.createUserMessage(messageText)
        }
        chatAdapter.addMessage(userMessage)

        // Scroll to show user's message
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

        // Process the message with AI (for images or regular chat)
        // Check if we're in push to database mode
        if (isPushToDatabaseMode) {
            val processed = processPushToDatabaseMessage(messageText)
            if (processed) {
                return
            }
        }

        // Regular message processing (works with or without images)
        processMessageWithAI(messageText)
    }

    /**
     * Process message with AI and add response to chat
     */
    private fun processMessageWithAI(message: String) {
        Log.d(TAG, "Processing message with AI: $message")

        // Check for database test commands first
        if (isDatabaseTestCommand(message)) {
            processDatabaseTestCommand(message)
            return
        }

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

                // Process on background thread - check if image is attached
                val bitmap = imageProcessor.getCurrentBitmap()

                // Use simple message processing
                val response = if (bitmap != null) {
                    Log.d(TAG, "Processing message with image")
                    modelManager.processImageQuestion(message, bitmap)
                } else {
                    Log.d(TAG, "Processing text message")
                    modelManager.processTextQuestion(message)
                }

                // ENHANCED: Process inventory command results before displaying
                val processedResponse = processInventoryCommandResult(response, message)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Hide processing indicator
                    binding.progressBar.visibility = View.GONE

                    // Add AI response to chat with enhanced formatting
                    val wasImageQuery = bitmap != null
                    val aiMessage = if (wasImageQuery) {
                        ChatMessage.createAIResponseToImage(formatInventoryResponse(processedResponse))
                    } else {
                        ChatMessage.createAIResponse(formatInventoryResponse(processedResponse))
                    }
                    chatAdapter.addMessage(aiMessage)

                    // Add quick action buttons if applicable
                    addQuickActionButtons(processedResponse, message)

                    // CRITICAL FIX: Scroll to bottom to show new message
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                    // Clear image after successful processing
                    if (imageProcessor.getCurrentBitmap() != null) {
                        removeSelectedImage()
                    }

                    binding.sendButton.isEnabled = true
                    binding.sendButton.text = "Send"
                    Log.d(TAG, "Message processed successfully - Response added to chat")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                withContext(Dispatchers.Main) {
                    handleEnhancedError(e.message ?: "Unknown error", message)
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
     * Show image selection dialog (Gallery or Camera)
     */
    private fun showImageSelectionDialog() {
        val options = arrayOf("📸 Select from Gallery", "📷 Take Photo")

        AlertDialog.Builder(this)
            .setTitle("Add Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imageProcessor.selectImage() // Gallery
                    1 -> imageProcessor.takePhoto()   // Camera
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Remove the currently selected image
     */
    private fun removeSelectedImage() {
        imageProcessor.clearImage()
        hideImagePreview()
    }

    /**
     * Show image preview with selected image
     */
    private fun showImagePreview(bitmap: Bitmap) {
        binding.selectedImagePreview.setImageBitmap(bitmap)
        binding.imagePreviewLayout.visibility = View.VISIBLE

        // Update hint text to indicate image is attached
        binding.messageInput.hint = "Ask a question about this image..."
    }

    /**
     * Hide image preview
     */
    private fun hideImagePreview() {
        binding.imagePreviewLayout.visibility = View.GONE
        binding.messageInput.hint = "Type your message..."
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

        // Show integrated image preview
        showImagePreview(bitmap)

        // Also update legacy UI state manager
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

    // ==================== INVENTORY INTEGRATION METHODS ====================

    /**
     * Process inventory command results and handle database operations.
     * This method bridges the AI processing with database operations.
     */
    private suspend fun processInventoryCommandResult(response: String, originalMessage: String): String {
        Log.d(TAG, "Processing inventory command result")

        try {
            // Check if the response indicates successful book extraction that needs database storage
            if (response.contains("Would you like me to add") &&
                (response.contains("book(s)") || response.contains("inventory"))) {

                // This is a book cataloging or manual entry result
                // For now, we'll return the response as-is and let the user confirm
                // In a full implementation, this would parse the response and prepare for database insertion
                return response + "\n\n💡 Tip: Reply 'yes' to add these books to the inventory, or 'no' to cancel."
            }

            // Check if this is a search request that needs database querying
            if (response.contains("search") && response.contains("database")) {
                // This indicates a search operation that should query the actual database
                return handleInventorySearch(originalMessage, response)
            }

            // For other responses, return as-is
            return response

        } catch (e: Exception) {
            Log.e(TAG, "Error processing inventory command result", e)
            return response // Return original response if processing fails
        }
    }

    /**
     * Handle inventory search operations by querying the actual database.
     */
    private suspend fun handleInventorySearch(originalMessage: String, aiResponse: String): String {
        Log.d(TAG, "Handling inventory search for: $originalMessage")

        try {
            // Extract search terms from the original message
            val searchQuery = extractSearchQuery(originalMessage)

            if (searchQuery.isNotBlank()) {
                // Query the database using the repository
                val books = bookRepository.getAllBooks().first() // Get current books

                // Simple search implementation - in a full version this would use proper search methods
                val matchingBooks = books.filter { book ->
                    book.titleEnglish.contains(searchQuery, ignoreCase = true) ||
                    book.authorEnglish.contains(searchQuery, ignoreCase = true) ||
                    book.titleKannada?.contains(searchQuery, ignoreCase = true) == true ||
                    book.authorKannada?.contains(searchQuery, ignoreCase = true) == true
                }

                return if (matchingBooks.isNotEmpty()) {
                    val bookList = matchingBooks.take(10).joinToString("\n\n") { book ->
                        "📖 ${book.getMultilingualInventoryString()}"
                    }
                    "🔍 Found ${matchingBooks.size} book(s) matching '$searchQuery':\n\n$bookList" +
                    if (matchingBooks.size > 10) "\n\n... and ${matchingBooks.size - 10} more books" else ""
                } else {
                    "🔍 No books found matching '$searchQuery' in the current inventory.\n\n" +
                    "💡 Try searching with different keywords or add the book to inventory first."
                }
            } else {
                return aiResponse // Return AI response if we can't extract search query
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling inventory search", e)
            return aiResponse // Return AI response if search fails
        }
    }

    /**
     * Extract search query from user message.
     */
    private fun extractSearchQuery(message: String): String {
        val lowerMessage = message.lowercase().trim()

        // Remove common search prefixes
        val searchPrefixes = listOf("find", "search", "show", "list", "get", "display")
        var query = lowerMessage

        for (prefix in searchPrefixes) {
            if (query.startsWith(prefix)) {
                query = query.removePrefix(prefix).trim()
                break
            }
        }

        // Remove common connecting words
        query = query.removePrefix("for").removePrefix("me").removePrefix("all").trim()

        // Remove "books" if it's at the end
        if (query.endsWith("books")) {
            query = query.removeSuffix("books").trim()
        }

        // Remove "by" prefix for author searches
        query = query.removePrefix("by").trim()

        return query
    }

    /**
     * Add system message with inventory context styling.
     */
    private fun addInventorySystemMessage(message: String) {
        val inventoryMessage = ChatMessage.createSystemMessage("📚 $message")
        chatAdapter.addMessage(inventoryMessage)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    // ==================== USER EXPERIENCE ENHANCEMENTS ====================

    /**
     * Format inventory responses for better readability and user experience.
     */
    private fun formatInventoryResponse(response: String): String {
        // Add emojis and formatting for better visual appeal
        var formattedResponse = response

        // Enhance book cataloging responses
        if (response.contains("Successfully identified") && response.contains("book(s)")) {
            formattedResponse = "🎉 " + response
        }

        // Enhance search results
        if (response.contains("Found") && response.contains("book(s) matching")) {
            formattedResponse = response.replace("Found", "🔍 Found")
        }

        // Enhance error messages
        if (response.contains("error") || response.contains("failed") || response.contains("couldn't")) {
            formattedResponse = "⚠️ " + response
        }

        // Enhance success messages
        if (response.contains("successfully") || response.contains("✅")) {
            if (!response.startsWith("🎉") && !response.startsWith("✅")) {
                formattedResponse = "✅ " + response
            }
        }

        // Enhance help messages
        if (response.contains("Bookstore Inventory Assistant") || response.contains("I can help you with")) {
            formattedResponse = "🤖 " + response
        }

        // Add line breaks for better readability
        formattedResponse = formattedResponse
            .replace("• ", "\n• ")
            .replace("📖 ", "\n📖 ")
            .replace("🔍 ", "\n🔍 ")

        return formattedResponse.trim()
    }

    /**
     * Add quick action buttons for common inventory operations.
     */
    private fun addQuickActionButtons(response: String, originalMessage: String) {
        // Add quick action buttons based on response content
        if (response.contains("Would you like me to add") && response.contains("inventory")) {
            addQuickActionMessage("Quick Actions:", listOf(
                "✅ Yes, add to inventory",
                "❌ No, cancel",
                "📝 Edit details first"
            ))
        } else if (response.contains("No books found matching")) {
            addQuickActionMessage("Suggestions:", listOf(
                "🔍 Try different keywords",
                "➕ Add this book to inventory",
                "📚 Browse all books"
            ))
        } else if (response.contains("Database Test Commands") || response.contains("help")) {
            addQuickActionMessage("Quick Commands:", listOf(
                "📖 Add sample books",
                "📊 Show inventory stats",
                "🔍 List all books"
            ))
        }
    }

    /**
     * Add a message with quick action buttons.
     */
    private fun addQuickActionMessage(title: String, actions: List<String>) {
        val actionMessage = "$title\n\n${actions.joinToString("\n")}\n\n💡 Tap or type any of these options"
        val quickActionMessage = ChatMessage.createSystemMessage(actionMessage)
        chatAdapter.addMessage(quickActionMessage)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
    }

    /**
     * Show enhanced processing indicator with context.
     */
    private fun showProcessingIndicator(message: String, hasImage: Boolean = false) {
        binding.progressBar.visibility = View.VISIBLE

        // Add contextual processing message
        val processingContext = when {
            hasImage -> "🖼️ Analyzing image for books..."
            message.lowercase().contains("add book") -> "📝 Processing book entry..."
            message.lowercase().contains("find") || message.lowercase().contains("search") -> "🔍 Searching inventory..."
            message.lowercase().contains("help") -> "🤖 Preparing help information..."
            else -> "🤔 Processing your request..."
        }

        addSystemMessage(processingContext)
    }

    /**
     * Enhanced error handling with actionable guidance.
     */
    private fun handleEnhancedError(error: String, originalMessage: String) {
        val enhancedErrorMessage = when {
            error.contains("model") || error.contains("AI") -> {
                "🤖 AI processing temporarily unavailable. You can still:\n" +
                "• Use database test commands (type 'db help')\n" +
                "• Try again in a moment\n" +
                "• Use manual book entry format: 'Add book: Title by Author'"
            }
            error.contains("database") -> {
                "💾 Database temporarily unavailable. Please:\n" +
                "• Check your connection\n" +
                "• Try again in a moment\n" +
                "• Contact support if the issue persists"
            }
            error.contains("image") -> {
                "🖼️ Image processing failed. Please:\n" +
                "• Ensure the image is clear and well-lit\n" +
                "• Make sure book titles are visible\n" +
                "• Try taking a new photo"
            }
            else -> {
                "⚠️ Something went wrong. You can:\n" +
                "• Try rephrasing your request\n" +
                "• Use 'help' to see available commands\n" +
                "• Try again in a moment"
            }
        }

        addSystemMessage(enhancedErrorMessage)

        // Add recovery suggestions
        addQuickActionMessage("Recovery Options:", listOf(
            "🔄 Try again",
            "❓ Get help",
            "📝 Use manual entry"
        ))
    }

    /**
     * Add typing indicator for better user feedback.
     */
    private fun showTypingIndicator() {
        val typingMessage = ChatMessage.createSystemMessage("🤖 Typing...")
        chatAdapter.addMessage(typingMessage)
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

        // Remove typing indicator after a short delay
        binding.chatRecyclerView.postDelayed({
            if (chatAdapter.itemCount > 0) {
                val lastMessage = chatAdapter.getLastMessage()
                if (lastMessage?.text?.contains("Typing...") == true) {
                    chatAdapter.removeLastMessage()
                }
            }
        }, 1500)
    }

    /**
     * Enhanced success feedback with next steps.
     */
    private fun showSuccessFeedback(operation: String, details: String = "") {
        val successMessage = when (operation.lowercase()) {
            "book_added" -> "🎉 Book successfully added to inventory!$details\n\n" +
                    "What would you like to do next?\n" +
                    "• Add another book\n" +
                    "• View inventory\n" +
                    "• Search for books"
            "search_completed" -> "🔍 Search completed!$details\n\n" +
                    "You can:\n" +
                    "• Refine your search\n" +
                    "• Add a new book\n" +
                    "• View full inventory"
            "books_cataloged" -> "📚 Books cataloged from image!$details\n\n" +
                    "Next steps:\n" +
                    "• Review and confirm details\n" +
                    "• Add to inventory\n" +
                    "• Take another photo"
            else -> "✅ Operation completed successfully!$details"
        }

        addSystemMessage(successMessage)
    }

    // ==================== DATABASE TEST COMMANDS ====================

    /**
     * Check if message is a database test command
     */
    private fun isDatabaseTestCommand(message: String): Boolean {
        val lowerMessage = message.lowercase().trim()
        return lowerMessage.startsWith("test db") ||
                lowerMessage.startsWith("add sample") ||
                lowerMessage.startsWith("list books") ||
                lowerMessage.startsWith("show books") ||
                lowerMessage.startsWith("count books") ||
                lowerMessage.startsWith("clear books") ||
                lowerMessage.startsWith("db help")
    }

    /**
     * Process database test commands
     */
    private fun processDatabaseTestCommand(message: String) {
        val lowerMessage = message.lowercase().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = when {
                    lowerMessage.startsWith("test db") -> testDatabaseConnection()
                    lowerMessage.startsWith("add sample") -> addSampleBooks()
                    lowerMessage.startsWith("list books") || lowerMessage.startsWith("show books") -> listAllBooks()
                    lowerMessage.startsWith("count books") -> countBooks()
                    lowerMessage.startsWith("clear books") -> clearAllBooks()
                    lowerMessage.startsWith("db help") -> getDatabaseHelp()
                    else -> "Unknown database command. Type 'db help' for available commands."
                }

                withContext(Dispatchers.Main) {
                    addSystemMessage(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addSystemMessage("Database error: ${e.message}")
                }
                Log.e(TAG, "Database test command error", e)
            }
        }
    }

    /**
     * Test database connection
     */
    private suspend fun testDatabaseConnection(): String {
        return try {
            val count = bookRepository.getTotalBookCount()
            if (count.isSuccess) {
                "✅ Database connection successful! Current books: ${count.getOrNull()}"
            } else {
                "❌ Database connection failed: ${count.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "❌ Database test failed: ${e.message}"
        }
    }

    /**
     * Add sample books for testing
     */
    private suspend fun addSampleBooks(): String {
        return try {
            val sampleBooks = listOf(
                Book.fromManualEntry("Atomic Habits", "James Clear", 299.0, 5, "A-1", "New"),
                Book.fromManualEntry("The Alchemist", "Paulo Coelho", 199.0, 3, "A-2", "New"),
                Book.fromAIRecognition("Rich Dad Poor Dad", "Robert Kiyosaki", confidence = "HIGH")
            )

            val result = bookRepository.insertBooks(sampleBooks)
            if (result.isSuccess) {
                "✅ Added ${result.getOrNull()} sample books successfully!"
            } else {
                "❌ Failed to add sample books: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "❌ Error adding sample books: ${e.message}"
        }
    }

    /**
     * List all books in database
     */
    private suspend fun listAllBooks(): String {
        return try {
            val books = bookRepository.getAllBooks().first()
            if (books.isEmpty()) {
                "📚 No books in inventory. Use 'add sample' to add test books."
            } else {
                val bookList = books.take(10).joinToString("\n") { book ->
                    "📖 ${book.getDisplayString()}"
                }
                "📚 Books in inventory (showing first 10):\n$bookList\n\nTotal: ${books.size} books"
            }
        } catch (e: Exception) {
            "❌ Error listing books: ${e.message}"
        }
    }

    /**
     * Count books in database
     */
    private suspend fun countBooks(): String {
        return try {
            val countResult = bookRepository.getTotalBookCount()
            val quantityResult = bookRepository.getTotalQuantity()
            val valueResult = bookRepository.getTotalInventoryValue()

            if (countResult.isSuccess) {
                val count = countResult.getOrNull() ?: 0
                val quantity = quantityResult.getOrNull() ?: 0
                val value = valueResult.getOrNull() ?: 0.0

                "📊 Inventory Statistics:\n" +
                "• Total Books: $count\n" +
                "• Total Quantity: $quantity\n" +
                "• Total Value: ₹${"%.2f".format(value)}"
            } else {
                "❌ Error counting books: ${countResult.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "❌ Error getting book count: ${e.message}"
        }
    }

    /**
     * Clear all books from database
     */
    private suspend fun clearAllBooks(): String {
        return try {
            // Note: We don't have a clearAll method in repository, so we'll get all books and delete them
            "⚠️ Clear all books functionality not implemented for safety. Use individual delete commands."
        } catch (e: Exception) {
            "❌ Error clearing books: ${e.message}"
        }
    }

    /**
     * Get database help commands
     */
    private fun getDatabaseHelp(): String {
        return """
            📚 Database Test Commands:

            • test db - Test database connection
            • add sample - Add sample books for testing
            • list books - Show all books in inventory
            • show books - Same as list books
            • count books - Show inventory statistics
            • clear books - Clear all books (disabled for safety)
            • db help - Show this help message

            Use these commands to test the database functionality!
        """.trimIndent()
    }

    /**
     * Add test multilingual books for testing the UI
     */
    private fun addTestMultilingualBooks() {
        lifecycleScope.launch {
            try {
                // Check if test books already exist
                val existingBooks = bookRepository.getAllBooks().first()
                if (existingBooks.isNotEmpty()) {
                    Log.d(TAG, "Test books already exist, skipping creation")
                    return@launch
                }

                Log.d(TAG, "Adding test multilingual books...")

                // Test Book 1: English + Kannada
                val book1 = Book(
                    titleEnglish = "Atomic Habits",
                    titleKannada = "ಪರಮಾಣು ಅಭ್ಯಾಸಗಳು",
                    authorEnglish = "James Clear",
                    authorKannada = "ಜೇಮ್ಸ್ ಕ್ಲಿಯರ್",
                    price = 299.0,
                    quantity = 5,
                    location = "A-1",
                    condition = "New",
                    extractionConfidence = "HIGH",
                    sourceImagePath = "test_image_1"
                )

                // Test Book 2: English only (to test fallback)
                val book2 = Book(
                    titleEnglish = "The Power of Now",
                    titleKannada = null,
                    authorEnglish = "Eckhart Tolle",
                    authorKannada = null,
                    price = 250.0,
                    quantity = 3,
                    location = "B-2",
                    condition = "New",
                    extractionConfidence = "MEDIUM",
                    sourceImagePath = "test_image_2"
                )

                // Test Book 3: Another multilingual book
                val book3 = Book(
                    titleEnglish = "Sapiens",
                    titleKannada = "ಸೇಪಿಯನ್ಸ್",
                    authorEnglish = "Yuval Noah Harari",
                    authorKannada = "ಯುವಾಲ್ ನೋಹ್ ಹರಾರಿ",
                    price = 399.0,
                    quantity = 2,
                    location = "C-3",
                    condition = "Used",
                    extractionConfidence = "HIGH",
                    sourceImagePath = "test_image_3"
                )

                // Insert test books
                bookRepository.insertBook(book1)
                bookRepository.insertBook(book2)
                bookRepository.insertBook(book3)

                Log.d(TAG, "Test multilingual books added successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error adding test books", e)
            }
        }
    }

}
