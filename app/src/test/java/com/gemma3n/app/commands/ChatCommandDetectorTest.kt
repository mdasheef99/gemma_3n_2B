package com.gemma3n.app.commands

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatCommandDetector.
 * 
 * Tests all intent detection scenarios to ensure >90% accuracy
 * for command recognition in the bookstore inventory system.
 */
class ChatCommandDetectorTest {
    
    private lateinit var detector: ChatCommandDetector
    
    @Before
    fun setUp() {
        detector = ChatCommandDetector()
    }
    
    // ==================== REGULAR CHAT TESTS ====================
    
    @Test
    fun testRegularChatDetection() {
        val regularMessages = listOf(
            "Hello, how are you?",
            "What's the weather like today?",
            "Tell me a joke",
            "How do I cook pasta?",
            "What is the meaning of life?"
        )
        
        regularMessages.forEach { message ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be regular chat", intent is ChatIntent.RegularChat)
            assertEquals(message, intent.getOriginalMessage())
        }
    }
    
    // ==================== BOOK CATALOGING TESTS ====================
    
    @Test
    fun testBookCatalogingWithImage() {
        val catalogingMessages = listOf(
            "Catalog these books",
            "Scan this image for books",
            "Add books from this photo",
            "Recognize books in image",
            "What books are in this picture?"
        )
        
        catalogingMessages.forEach { message ->
            val intent = detector.detectIntent(message, hasImage = true)
            assertTrue("Message '$message' with image should be book cataloging", 
                intent is ChatIntent.BookCataloging)
            assertTrue("Should require image", intent.requiresImage())
        }
    }
    
    @Test
    fun testBookCatalogingWithoutImage() {
        val message = "Catalog these books"
        val intent = detector.detectIntent(message, hasImage = false)
        
        // Without image, should be treated as help request or regular chat
        assertTrue("Message without image should not be book cataloging", 
            intent !is ChatIntent.BookCataloging)
    }
    
    // ==================== MANUAL BOOK ENTRY TESTS ====================
    
    @Test
    fun testManualBookEntryBasicFormat() {
        val testCases = listOf(
            "Add book: Atomic Habits by James Clear" to Pair("Atomic Habits", "James Clear"),
            "Book: The Alchemist by Paulo Coelho" to Pair("The Alchemist", "Paulo Coelho"),
            "add book: Rich Dad Poor Dad by Robert Kiyosaki" to Pair("Rich Dad Poor Dad", "Robert Kiyosaki")
        )
        
        testCases.forEach { (message, expected) ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be manual book entry", 
                intent is ChatIntent.ManualBookEntry)
            
            val bookEntry = intent as ChatIntent.ManualBookEntry
            assertEquals("Title should match", expected.first, bookEntry.extractedTitle)
            assertEquals("Author should match", expected.second, bookEntry.extractedAuthor)
        }
    }
    
    @Test
    fun testManualBookEntryWithPrice() {
        val message = "Add book: Atomic Habits by James Clear price 299"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be manual book entry", intent is ChatIntent.ManualBookEntry)
        val bookEntry = intent as ChatIntent.ManualBookEntry
        assertEquals("Atomic Habits", bookEntry.extractedTitle)
        assertEquals("James Clear", bookEntry.extractedAuthor)
        assertEquals(299.0, bookEntry.extractedPrice)
    }
    
    @Test
    fun testManualBookEntryWithQuantityAndLocation() {
        val message = "Add book: The Alchemist by Paulo Coelho qty 5 location A-1"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be manual book entry", intent is ChatIntent.ManualBookEntry)
        val bookEntry = intent as ChatIntent.ManualBookEntry
        assertEquals("The Alchemist", bookEntry.extractedTitle)
        assertEquals("Paulo Coelho", bookEntry.extractedAuthor)
        assertEquals(5, bookEntry.extractedQuantity)
        assertEquals("A-1", bookEntry.extractedLocation)
    }
    
    // ==================== SEARCH TESTS ====================
    
    @Test
    fun testGeneralSearch() {
        val searchMessages = listOf(
            "Find Atomic Habits",
            "Search for James Clear",
            "Show me books by Paulo Coelho",
            "List all fiction books",
            "Get books about habits"
        )
        
        searchMessages.forEach { message ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be inventory search", 
                intent is ChatIntent.InventorySearch)
            
            val search = intent as ChatIntent.InventorySearch
            assertFalse("Query should not be empty", search.query.isBlank())
        }
    }
    
    @Test
    fun testSearchByAuthor() {
        val message = "Find books by James Clear"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be inventory search", intent is ChatIntent.InventorySearch)
        val search = intent as ChatIntent.InventorySearch
        assertEquals("Search type should be BY_AUTHOR", 
            ChatIntent.InventorySearch.SearchType.BY_AUTHOR, search.searchType)
        assertTrue("Query should contain author name", search.query.contains("James Clear"))
    }
    
    @Test
    fun testSearchByLocation() {
        val message = "Show books in location A-5"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be inventory search", intent is ChatIntent.InventorySearch)
        val search = intent as ChatIntent.InventorySearch
        assertEquals("Search type should be BY_LOCATION", 
            ChatIntent.InventorySearch.SearchType.BY_LOCATION, search.searchType)
    }
    
    @Test
    fun testSearchByCondition() {
        val message = "List all used books"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be inventory search", intent is ChatIntent.InventorySearch)
        val search = intent as ChatIntent.InventorySearch
        assertEquals("Search type should be BY_CONDITION", 
            ChatIntent.InventorySearch.SearchType.BY_CONDITION, search.searchType)
    }
    
    // ==================== UPDATE TESTS ====================
    
    @Test
    fun testUpdateBookPrice() {
        val message = "Update price of Atomic Habits to 350"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be update book", intent is ChatIntent.UpdateBook)
        val update = intent as ChatIntent.UpdateBook
        assertEquals("Update type should be PRICE", 
            ChatIntent.UpdateBook.UpdateType.PRICE, update.updateType)
        assertEquals("Atomic Habits", update.bookIdentifier)
        assertEquals("350", update.newValue)
    }
    
    @Test
    fun testMoveBook() {
        val message = "Move The Alchemist to location B-3"
        val intent = detector.detectIntent(message)
        
        assertTrue("Should be update book", intent is ChatIntent.UpdateBook)
        val update = intent as ChatIntent.UpdateBook
        assertEquals("Update type should be LOCATION", 
            ChatIntent.UpdateBook.UpdateType.LOCATION, update.updateType)
        assertEquals("The Alchemist", update.bookIdentifier)
        assertEquals("location B-3", update.newValue)
    }
    
    // ==================== DELETE TESTS ====================
    
    @Test
    fun testDeleteBook() {
        val deleteMessages = listOf(
            "Remove Atomic Habits",
            "Delete The Alchemist",
            "Clear Rich Dad Poor Dad"
        )
        
        deleteMessages.forEach { message ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be delete book", intent is ChatIntent.DeleteBook)
            
            val delete = intent as ChatIntent.DeleteBook
            assertFalse("Book identifier should not be empty", delete.bookIdentifier.isNullOrBlank())
        }
    }
    
    // ==================== ANALYTICS TESTS ====================
    
    @Test
    fun testAnalyticsRequests() {
        val analyticsMessages = mapOf(
            "How many books do we have?" to ChatIntent.InventoryAnalytics.AnalyticsType.COUNT,
            "Show inventory stats" to ChatIntent.InventoryAnalytics.AnalyticsType.GENERAL,
            "What's the total value?" to ChatIntent.InventoryAnalytics.AnalyticsType.VALUE,
            "Show books by condition" to ChatIntent.InventoryAnalytics.AnalyticsType.BY_CONDITION,
            "Low stock report" to ChatIntent.InventoryAnalytics.AnalyticsType.LOW_STOCK
        )
        
        analyticsMessages.forEach { (message, expectedType) ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be analytics", intent is ChatIntent.InventoryAnalytics)
            
            val analytics = intent as ChatIntent.InventoryAnalytics
            assertEquals("Analytics type should match", expectedType, analytics.analyticsType)
        }
    }
    
    // ==================== HELP TESTS ====================
    
    @Test
    fun testHelpRequests() {
        val helpMessages = listOf(
            "Help",
            "What can I do?",
            "Show me commands",
            "How to add books?",
            "Inventory help"
        )
        
        helpMessages.forEach { message ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be help request", intent is ChatIntent.InventoryHelp)
        }
    }
    
    // ==================== BATCH OPERATIONS TESTS ====================
    
    @Test
    fun testBatchOperations() {
        val batchMessages = mapOf(
            "Add all books from image" to ChatIntent.BatchOperation.BatchType.ADD_MULTIPLE,
            "Update all prices by 10%" to ChatIntent.BatchOperation.BatchType.UPDATE_MULTIPLE,
            "Delete multiple books" to ChatIntent.BatchOperation.BatchType.DELETE_MULTIPLE,
            "Move all books to section A" to ChatIntent.BatchOperation.BatchType.MOVE_MULTIPLE
        )
        
        batchMessages.forEach { (message, expectedType) ->
            val intent = detector.detectIntent(message)
            assertTrue("Message '$message' should be batch operation", intent is ChatIntent.BatchOperation)
            
            val batch = intent as ChatIntent.BatchOperation
            assertEquals("Batch type should match", expectedType, batch.operationType)
        }
    }
    
    // ==================== EDGE CASES AND ERROR HANDLING ====================
    
    @Test
    fun testEmptyMessage() {
        val intent = detector.detectIntent("")
        assertTrue("Empty message should be regular chat", intent is ChatIntent.RegularChat)
    }
    
    @Test
    fun testWhitespaceOnlyMessage() {
        val intent = detector.detectIntent("   \n\t  ")
        assertTrue("Whitespace-only message should be regular chat", intent is ChatIntent.RegularChat)
    }
    
    @Test
    fun testVeryLongMessage() {
        val longMessage = "Add book: " + "A".repeat(1000) + " by " + "B".repeat(1000)
        val intent = detector.detectIntent(longMessage)
        assertTrue("Long message should still be processed", intent is ChatIntent.ManualBookEntry)
    }
    
    @Test
    fun testSpecialCharacters() {
        val message = "Add book: C++ Programming by Bjarne Stroustrup"
        val intent = detector.detectIntent(message)
        assertTrue("Message with special characters should work", intent is ChatIntent.ManualBookEntry)
    }
    
    // ==================== ACCURACY TESTS ====================
    
    @Test
    fun testCommandDetectionAccuracy() {
        val testCases = listOf(
            // Manual book entries
            "Add book: Test Title by Test Author" to ChatIntent.ManualBookEntry::class.java,
            "Book: Another Title by Another Author" to ChatIntent.ManualBookEntry::class.java,
            
            // Searches
            "Find Test Book" to ChatIntent.InventorySearch::class.java,
            "Search for author name" to ChatIntent.InventorySearch::class.java,
            "Show me books" to ChatIntent.InventorySearch::class.java,
            
            // Updates
            "Update price of book to 100" to ChatIntent.UpdateBook::class.java,
            "Move book to location A-1" to ChatIntent.UpdateBook::class.java,
            
            // Deletes
            "Remove test book" to ChatIntent.DeleteBook::class.java,
            "Delete old book" to ChatIntent.DeleteBook::class.java,
            
            // Analytics
            "How many books?" to ChatIntent.InventoryAnalytics::class.java,
            "Show stats" to ChatIntent.InventoryAnalytics::class.java,
            
            // Help
            "Help me" to ChatIntent.InventoryHelp::class.java,
            "What can I do?" to ChatIntent.InventoryHelp::class.java,
            
            // Regular chat
            "Hello there" to ChatIntent.RegularChat::class.java,
            "How are you?" to ChatIntent.RegularChat::class.java
        )
        
        var correctDetections = 0
        
        testCases.forEach { (message, expectedClass) ->
            val intent = detector.detectIntent(message)
            if (expectedClass.isInstance(intent)) {
                correctDetections++
            } else {
                println("FAILED: '$message' expected ${expectedClass.simpleName} but got ${intent::class.java.simpleName}")
            }
        }
        
        val accuracy = correctDetections.toDouble() / testCases.size
        println("Command detection accuracy: ${(accuracy * 100).toInt()}%")
        
        assertTrue("Command detection accuracy should be >90%", accuracy >= 0.9)
    }
}
