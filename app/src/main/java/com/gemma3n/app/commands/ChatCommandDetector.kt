package com.gemma3n.app.commands

import android.util.Log
import java.util.regex.Pattern

/**
 * Chat Command Detector for the bookstore inventory system.
 * 
 * This class analyzes user messages and classifies them into appropriate
 * ChatIntent types using keyword matching, regex patterns, and context analysis.
 * Designed to achieve >90% accuracy for inventory command detection.
 */
class ChatCommandDetector {
    
    companion object {
        private const val TAG = "ChatCommandDetector"
        
        // Inventory-related keywords for initial filtering
        private val INVENTORY_KEYWORDS = setOf(
            // Book operations
            "catalog", "scan", "add", "book", "books", "inventory",
            // Search operations
            "find", "search", "show", "list", "display", "get",
            // Update operations
            "update", "change", "modify", "set", "move", "edit",
            // Delete operations
            "remove", "delete", "clear", "drop",
            // Analytics
            "count", "total", "stats", "statistics", "report", "summary",
            // Help
            "help", "commands", "what", "how",
            // Export/Import
            "export", "backup", "save", "import", "restore"
        )
        
        // Regex patterns for structured commands
        private val MANUAL_BOOK_ENTRY_PATTERNS = listOf(
            Pattern.compile("add book:?\\s*(.+?)\\s+by\\s+(.+?)(?:\\s+price\\s+(\\d+(?:\\.\\d+)?))?(?:\\s+qty\\s+(\\d+))?(?:\\s+location\\s+(.+?))?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("book:?\\s*(.+?)\\s+author:?\\s*(.+?)(?:\\s+price:?\\s*(\\d+(?:\\.\\d+)?))?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("title:?\\s*(.+?)\\s+author:?\\s*(.+?)(?:\\s+price:?\\s*(\\d+(?:\\.\\d+)?))?", Pattern.CASE_INSENSITIVE)
        )
        
        private val SEARCH_PATTERNS = listOf(
            Pattern.compile("find\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("search\\s+(?:for\\s+)?(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("show\\s+(?:me\\s+)?(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("list\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("get\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )
        
        private val UPDATE_PATTERNS = listOf(
            Pattern.compile("(?:update|set|change)\\s+(.+?)\\s+(?:to|=)\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:move|relocate)\\s+(.+?)\\s+to\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("price\\s+of\\s+(.+?)\\s+(?:to|=)\\s+(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)
        )
        
        private val DELETE_PATTERNS = listOf(
            Pattern.compile("(?:remove|delete)\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("clear\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )
    }
    
    /**
     * Main method to detect intent from user message.
     * 
     * @param message The user's input message
     * @param hasImage Whether the message includes an image attachment
     * @return ChatIntent representing the detected user intent
     */
    fun detectIntent(message: String, hasImage: Boolean = false): ChatIntent {
        val cleanMessage = message.trim()
        val lowerMessage = cleanMessage.lowercase()
        
        Log.d(TAG, "Detecting intent for message: '$cleanMessage', hasImage: $hasImage")
        
        try {
            // Quick check: if no inventory keywords and no image, likely regular chat
            if (!hasImage && !containsInventoryKeywords(lowerMessage)) {
                Log.d(TAG, "No inventory keywords found, classifying as regular chat")
                return ChatIntent.RegularChat(cleanMessage)
            }
            
            // Priority 1: Image + inventory keywords = Book cataloging
            if (hasImage && containsBookCatalogingKeywords(lowerMessage)) {
                Log.d(TAG, "Detected book cataloging intent (image + keywords)")
                return ChatIntent.BookCataloging(cleanMessage, hasImage = true)
            }
            
            // Priority 2: Manual book entry patterns
            val manualEntryIntent = detectManualBookEntry(cleanMessage)
            if (manualEntryIntent != null) {
                Log.d(TAG, "Detected manual book entry intent")
                return manualEntryIntent
            }
            
            // Priority 3: Search operations
            val searchIntent = detectSearchIntent(cleanMessage, lowerMessage)
            if (searchIntent != null) {
                Log.d(TAG, "Detected search intent: ${searchIntent.searchType}")
                return searchIntent
            }
            
            // Priority 4: Update operations
            val updateIntent = detectUpdateIntent(cleanMessage, lowerMessage)
            if (updateIntent != null) {
                Log.d(TAG, "Detected update intent: ${updateIntent.updateType}")
                return updateIntent
            }
            
            // Priority 5: Delete operations
            val deleteIntent = detectDeleteIntent(cleanMessage, lowerMessage)
            if (deleteIntent != null) {
                Log.d(TAG, "Detected delete intent")
                return deleteIntent
            }
            
            // Priority 6: Analytics requests
            val analyticsIntent = detectAnalyticsIntent(cleanMessage, lowerMessage)
            if (analyticsIntent != null) {
                Log.d(TAG, "Detected analytics intent: ${analyticsIntent.analyticsType}")
                return analyticsIntent
            }
            
            // Priority 7: Help requests
            if (isHelpRequest(lowerMessage)) {
                Log.d(TAG, "Detected help request")
                return ChatIntent.InventoryHelp(cleanMessage)
            }
            
            // Priority 8: Export/Import operations
            val exportIntent = detectExportIntent(cleanMessage, lowerMessage)
            if (exportIntent != null) {
                Log.d(TAG, "Detected export intent: ${exportIntent.exportType}")
                return exportIntent
            }
            
            // Priority 9: Batch operations
            val batchIntent = detectBatchOperation(cleanMessage, lowerMessage)
            if (batchIntent != null) {
                Log.d(TAG, "Detected batch operation: ${batchIntent.operationType}")
                return batchIntent
            }
            
            // If has inventory keywords but no specific pattern matched, 
            // treat as general inventory help request
            if (containsInventoryKeywords(lowerMessage)) {
                Log.d(TAG, "Contains inventory keywords but no specific pattern, treating as help request")
                return ChatIntent.InventoryHelp(cleanMessage)
            }
            
            // Default: Regular chat
            Log.d(TAG, "No specific intent detected, defaulting to regular chat")
            return ChatIntent.RegularChat(cleanMessage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting intent", e)
            return ChatIntent.RegularChat(cleanMessage)
        }
    }
    
    /**
     * Check if message contains general inventory keywords.
     */
    private fun containsInventoryKeywords(lowerMessage: String): Boolean {
        return INVENTORY_KEYWORDS.any { keyword ->
            lowerMessage.contains(keyword)
        }
    }
    
    /**
     * Check if message contains book cataloging keywords.
     */
    private fun containsBookCatalogingKeywords(lowerMessage: String): Boolean {
        val catalogingKeywords = setOf("catalog", "scan", "add", "books", "inventory", "recognize")
        return catalogingKeywords.any { keyword ->
            lowerMessage.contains(keyword)
        }
    }
    
    /**
     * Detect manual book entry intent using regex patterns.
     */
    private fun detectManualBookEntry(message: String): ChatIntent.ManualBookEntry? {
        for (pattern in MANUAL_BOOK_ENTRY_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val title = matcher.group(1)?.trim()
                val author = matcher.group(2)?.trim()
                val price = matcher.group(3)?.toDoubleOrNull()
                val quantity = matcher.group(4)?.toIntOrNull()
                val location = matcher.group(5)?.trim()
                
                if (!title.isNullOrBlank() && !author.isNullOrBlank()) {
                    return ChatIntent.ManualBookEntry(
                        message = message,
                        extractedTitle = title,
                        extractedAuthor = author,
                        extractedPrice = price,
                        extractedQuantity = quantity,
                        extractedLocation = location
                    )
                }
            }
        }
        return null
    }
    
    /**
     * Detect search intent and classify search type.
     */
    private fun detectSearchIntent(message: String, lowerMessage: String): ChatIntent.InventorySearch? {
        // Check for specific search type keywords first
        val searchType = when {
            lowerMessage.contains("by author") || lowerMessage.contains("author") -> 
                ChatIntent.InventorySearch.SearchType.BY_AUTHOR
            lowerMessage.contains("by title") || lowerMessage.contains("title") -> 
                ChatIntent.InventorySearch.SearchType.BY_TITLE
            lowerMessage.contains("location") || lowerMessage.contains("shelf") -> 
                ChatIntent.InventorySearch.SearchType.BY_LOCATION
            lowerMessage.contains("condition") || lowerMessage.contains("new") || 
            lowerMessage.contains("used") || lowerMessage.contains("damaged") -> 
                ChatIntent.InventorySearch.SearchType.BY_CONDITION
            lowerMessage.contains("recent") || lowerMessage.contains("latest") || 
            lowerMessage.contains("new") -> 
                ChatIntent.InventorySearch.SearchType.RECENT
            lowerMessage.contains("low stock") || lowerMessage.contains("running out") -> 
                ChatIntent.InventorySearch.SearchType.LOW_STOCK
            else -> ChatIntent.InventorySearch.SearchType.GENERAL
        }
        
        // Extract search query using patterns
        for (pattern in SEARCH_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val query = matcher.group(1)?.trim()
                if (!query.isNullOrBlank()) {
                    return ChatIntent.InventorySearch(query, searchType)
                }
            }
        }
        
        // Check for simple search keywords without patterns
        if (lowerMessage.contains("show") || lowerMessage.contains("list") || 
            lowerMessage.contains("find") || lowerMessage.contains("search")) {
            // Extract everything after the search keyword
            val query = extractQueryAfterKeyword(lowerMessage, listOf("show", "list", "find", "search"))
            if (query.isNotBlank()) {
                return ChatIntent.InventorySearch(query, searchType)
            }
        }
        
        return null
    }
    
    /**
     * Extract query text after a search keyword.
     */
    private fun extractQueryAfterKeyword(lowerMessage: String, keywords: List<String>): String {
        for (keyword in keywords) {
            val index = lowerMessage.indexOf(keyword)
            if (index != -1) {
                val afterKeyword = lowerMessage.substring(index + keyword.length).trim()
                // Remove common connecting words
                val cleaned = afterKeyword.removePrefix("me").removePrefix("for").removePrefix("all").trim()
                if (cleaned.isNotBlank()) {
                    return cleaned
                }
            }
        }
        return ""
    }
    
    /**
     * Detect update intent and classify update type.
     */
    private fun detectUpdateIntent(message: String, lowerMessage: String): ChatIntent.UpdateBook? {
        val updateType = when {
            lowerMessage.contains("price") -> ChatIntent.UpdateBook.UpdateType.PRICE
            lowerMessage.contains("quantity") || lowerMessage.contains("qty") -> 
                ChatIntent.UpdateBook.UpdateType.QUANTITY
            lowerMessage.contains("location") || lowerMessage.contains("move") -> 
                ChatIntent.UpdateBook.UpdateType.LOCATION
            lowerMessage.contains("condition") -> ChatIntent.UpdateBook.UpdateType.CONDITION
            else -> ChatIntent.UpdateBook.UpdateType.GENERAL
        }
        
        for (pattern in UPDATE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val bookIdentifier = matcher.group(1)?.trim()
                val newValue = matcher.group(2)?.trim()
                
                if (!bookIdentifier.isNullOrBlank() && !newValue.isNullOrBlank()) {
                    return ChatIntent.UpdateBook(
                        message = message,
                        updateType = updateType,
                        bookIdentifier = bookIdentifier,
                        newValue = newValue
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * Detect delete intent.
     */
    private fun detectDeleteIntent(message: String, lowerMessage: String): ChatIntent.DeleteBook? {
        for (pattern in DELETE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val bookIdentifier = matcher.group(1)?.trim()
                if (!bookIdentifier.isNullOrBlank()) {
                    return ChatIntent.DeleteBook(
                        message = message,
                        bookIdentifier = bookIdentifier
                    )
                }
            }
        }
        return null
    }
    
    /**
     * Detect analytics intent.
     */
    private fun detectAnalyticsIntent(message: String, lowerMessage: String): ChatIntent.InventoryAnalytics? {
        val analyticsKeywords = setOf("count", "total", "stats", "statistics", "report", "summary", "how many")
        
        if (analyticsKeywords.any { lowerMessage.contains(it) }) {
            val analyticsType = when {
                lowerMessage.contains("count") || lowerMessage.contains("how many") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.COUNT
                lowerMessage.contains("value") || lowerMessage.contains("worth") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.VALUE
                lowerMessage.contains("condition") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.BY_CONDITION
                lowerMessage.contains("location") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.BY_LOCATION
                lowerMessage.contains("low stock") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.LOW_STOCK
                lowerMessage.contains("recent") -> 
                    ChatIntent.InventoryAnalytics.AnalyticsType.RECENT_ACTIVITY
                else -> ChatIntent.InventoryAnalytics.AnalyticsType.GENERAL
            }
            
            return ChatIntent.InventoryAnalytics(message, analyticsType)
        }
        
        return null
    }
    
    /**
     * Check if message is a help request.
     */
    private fun isHelpRequest(lowerMessage: String): Boolean {
        val helpKeywords = setOf("help", "commands", "what can", "how to", "instructions")
        return helpKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Detect export intent.
     */
    private fun detectExportIntent(message: String, lowerMessage: String): ChatIntent.InventoryExport? {
        val exportKeywords = setOf("export", "backup", "save", "download")
        
        if (exportKeywords.any { lowerMessage.contains(it) }) {
            val exportType = when {
                lowerMessage.contains("condition") -> ChatIntent.InventoryExport.ExportType.BY_CONDITION
                lowerMessage.contains("location") -> ChatIntent.InventoryExport.ExportType.BY_LOCATION
                lowerMessage.contains("recent") -> ChatIntent.InventoryExport.ExportType.RECENT
                else -> ChatIntent.InventoryExport.ExportType.FULL
            }
            
            return ChatIntent.InventoryExport(message, exportType)
        }
        
        return null
    }
    
    /**
     * Detect batch operations.
     */
    private fun detectBatchOperation(message: String, lowerMessage: String): ChatIntent.BatchOperation? {
        val batchKeywords = setOf("all", "multiple", "batch", "bulk")
        
        if (batchKeywords.any { lowerMessage.contains(it) }) {
            val operationType = when {
                lowerMessage.contains("add") || lowerMessage.contains("catalog") -> 
                    ChatIntent.BatchOperation.BatchType.ADD_MULTIPLE
                lowerMessage.contains("update") || lowerMessage.contains("change") -> 
                    ChatIntent.BatchOperation.BatchType.UPDATE_MULTIPLE
                lowerMessage.contains("delete") || lowerMessage.contains("remove") -> 
                    ChatIntent.BatchOperation.BatchType.DELETE_MULTIPLE
                lowerMessage.contains("move") || lowerMessage.contains("relocate") -> 
                    ChatIntent.BatchOperation.BatchType.MOVE_MULTIPLE
                else -> ChatIntent.BatchOperation.BatchType.ADD_MULTIPLE
            }
            
            return ChatIntent.BatchOperation(message, operationType)
        }
        
        return null
    }
}
