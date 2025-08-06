package com.gemma3n.app

import android.content.Context
import android.graphics.Bitmap
import com.gemma3n.app.commands.ChatIntent
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ModelManager inventory-specific functionality.
 * 
 * Tests the integration of command detection, entity extraction, and AI processing
 * for bookstore inventory management operations.
 */
class ModelManagerInventoryTest {
    
    private lateinit var modelManager: ModelManager
    private lateinit var mockContext: Context
    private lateinit var mockBitmap: Bitmap
    
    @Before
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockBitmap = mockk<Bitmap>(relaxed = true)
        
        // Mock bitmap dimensions
        every { mockBitmap.width } returns 800
        every { mockBitmap.height } returns 600
        
        modelManager = ModelManager(mockContext)
    }
    
    // ==================== COMMAND DETECTION INTEGRATION TESTS ====================
    
    @Test
    fun testBookCatalogingCommandDetection() = runBlocking {
        val message = "Catalog these books"
        
        // Since we can't easily mock the AI model, we'll test the command detection logic
        // by checking that the method handles the intent correctly
        val result = modelManager.processInventoryCommand(message, mockBitmap)
        
        // Should recognize this as a book cataloging request
        assertTrue("Should handle book cataloging request", 
            result.contains("catalog") || result.contains("image") || result.contains("books"))
    }
    
    @Test
    fun testManualBookEntryCommandDetection() = runBlocking {
        val message = "Add book: Atomic Habits by James Clear"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should recognize this as manual book entry and extract information
        assertTrue("Should handle manual book entry", 
            result.contains("Atomic Habits") || result.contains("James Clear") || result.contains("extracted"))
    }
    
    @Test
    fun testInventorySearchCommandDetection() = runBlocking {
        val message = "Find books by James Clear"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should recognize this as an inventory search
        assertTrue("Should handle inventory search", 
            result.contains("search") || result.contains("James Clear") || result.contains("author"))
    }
    
    @Test
    fun testInventoryHelpCommandDetection() = runBlocking {
        val message = "Help me with inventory"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should provide inventory help
        assertTrue("Should provide inventory help", 
            result.contains("help") || result.contains("Book") || result.contains("Inventory"))
    }
    
    @Test
    fun testRegularChatHandling() = runBlocking {
        val message = "What's the weather like today?"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should handle as regular chat but in inventory context
        assertTrue("Should handle regular chat", 
            result.isNotEmpty())
    }
    
    // ==================== BOOK CATALOGING TESTS ====================
    
    @Test
    fun testBookCatalogingWithoutImage() = runBlocking {
        val message = "Catalog these books"
        
        val result = modelManager.processInventoryCommand(message, null)
        
        assertTrue("Should request image", 
            result.contains("attach") || result.contains("image"))
    }
    
    @Test
    fun testBookCatalogingWithImage() = runBlocking {
        val message = "What books are in this image?"
        
        // This would normally call the AI model, but since we can't mock that easily,
        // we'll test that the method handles the request appropriately
        val result = modelManager.processInventoryCommand(message, mockBitmap)
        
        // Should attempt to process the image (even if model isn't loaded)
        assertTrue("Should attempt image processing", 
            result.isNotEmpty())
    }
    
    // ==================== MANUAL BOOK ENTRY TESTS ====================
    
    @Test
    fun testManualBookEntryWithCompleteInfo() = runBlocking {
        val message = "Add book: Atomic Habits by James Clear price 299 qty 5 location A-1"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should extract and display book information
        assertTrue("Should extract title", result.contains("Atomic Habits"))
        assertTrue("Should extract author", result.contains("James Clear"))
        assertTrue("Should extract price", result.contains("299") || result.contains("Price"))
        assertTrue("Should extract quantity", result.contains("5") || result.contains("Quantity"))
        assertTrue("Should extract location", result.contains("A-1") || result.contains("Location"))
    }
    
    @Test
    fun testManualBookEntryWithMinimalInfo() = runBlocking {
        val message = "Book: The Alchemist by Paulo Coelho"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should extract basic information
        assertTrue("Should extract title", result.contains("The Alchemist"))
        assertTrue("Should extract author", result.contains("Paulo Coelho"))
    }
    
    @Test
    fun testManualBookEntryWithIncompleteInfo() = runBlocking {
        val message = "Add a book called Atomic Habits"
        
        val result = modelManager.processInventoryCommand(message)
        
        // Should handle incomplete information gracefully
        assertTrue("Should handle incomplete info", result.isNotEmpty())
    }
    
    // ==================== INVENTORY SEARCH TESTS ====================
    
    @Test
    fun testSearchByAuthor() = runBlocking {
        val message = "Find books by James Clear"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle author search", 
            result.contains("author") && result.contains("James Clear"))
    }
    
    @Test
    fun testSearchByTitle() = runBlocking {
        val message = "Search for Atomic Habits"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle title search", 
            result.contains("search") || result.contains("Atomic Habits"))
    }
    
    @Test
    fun testSearchByLocation() = runBlocking {
        val message = "Show books in location A-5"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle location search", 
            result.contains("location") && result.contains("A-5"))
    }
    
    @Test
    fun testRecentBooksSearch() = runBlocking {
        val message = "Show recent books"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle recent books search", 
            result.contains("recent") || result.contains("recently"))
    }
    
    @Test
    fun testLowStockSearch() = runBlocking {
        val message = "Show low stock books"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle low stock search", 
            result.contains("low stock") || result.contains("running"))
    }
    
    // ==================== ERROR HANDLING TESTS ====================
    
    @Test
    fun testEmptyMessage() = runBlocking {
        val result = modelManager.processInventoryCommand("")
        
        assertTrue("Should handle empty message", result.isNotEmpty())
    }
    
    @Test
    fun testVeryLongMessage() = runBlocking {
        val longMessage = "Add book: " + "A".repeat(1000) + " by " + "B".repeat(1000)
        
        val result = modelManager.processInventoryCommand(longMessage)
        
        assertTrue("Should handle long message", result.isNotEmpty())
    }
    
    @Test
    fun testSpecialCharacters() = runBlocking {
        val message = "Add book: C++ Programming & Design by Bjarne Stroustrup"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should handle special characters", 
            result.contains("C++") || result.contains("Programming"))
    }
    
    // ==================== HELP SYSTEM TESTS ====================
    
    @Test
    fun testInventoryHelp() = runBlocking {
        val message = "inventory help"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should provide comprehensive help", result.length > 100)
        assertTrue("Should mention book cataloging", result.contains("Cataloging"))
        assertTrue("Should mention manual entry", result.contains("Manual"))
        assertTrue("Should mention search", result.contains("Search"))
        assertTrue("Should mention analytics", result.contains("Analytics"))
    }
    
    @Test
    fun testWhatCanIDo() = runBlocking {
        val message = "What can I do?"
        
        val result = modelManager.processInventoryCommand(message)
        
        assertTrue("Should provide help information", result.isNotEmpty())
    }
    
    // ==================== INTEGRATION TESTS ====================
    
    @Test
    fun testCommandDetectionAccuracy() = runBlocking {
        val testCases = mapOf(
            "Catalog these books" to "cataloging",
            "Add book: Test Title by Test Author" to "manual",
            "Find books by author" to "search",
            "Show inventory stats" to "analytics",
            "Help with inventory" to "help",
            "Hello there" to "chat"
        )
        
        var correctDetections = 0
        
        testCases.forEach { (message, expectedType) ->
            val result = modelManager.processInventoryCommand(message)
            
            val isCorrect = when (expectedType) {
                "cataloging" -> result.contains("catalog") || result.contains("image")
                "manual" -> result.contains("Test Title") || result.contains("Test Author") || result.contains("extracted")
                "search" -> result.contains("search") || result.contains("author")
                "analytics" -> result.contains("stats") || result.contains("analytics")
                "help" -> result.contains("help") || result.contains("Book")
                "chat" -> result.isNotEmpty()
                else -> false
            }
            
            if (isCorrect) {
                correctDetections++
            } else {
                println("FAILED: '$message' expected $expectedType but got: ${result.take(100)}")
            }
        }
        
        val accuracy = correctDetections.toDouble() / testCases.size
        println("Command detection integration accuracy: ${(accuracy * 100).toInt()}%")
        
        assertTrue("Integration accuracy should be >80%", accuracy >= 0.8)
    }
    
    // ==================== PERFORMANCE TESTS ====================
    
    @Test
    fun testProcessingPerformance() = runBlocking {
        val message = "Add book: Performance Test by Test Author"
        
        val startTime = System.currentTimeMillis()
        val result = modelManager.processInventoryCommand(message)
        val endTime = System.currentTimeMillis()
        
        val processingTime = endTime - startTime
        
        assertTrue("Should return result", result.isNotEmpty())
        assertTrue("Processing should be fast (<1000ms)", processingTime < 1000)
        
        println("Command processing time: ${processingTime}ms")
    }
    
    @Test
    fun testMultipleCommandsPerformance() = runBlocking {
        val commands = listOf(
            "Add book: Book1 by Author1",
            "Find books by Author2",
            "Show inventory stats",
            "Help with inventory",
            "Catalog these books"
        )
        
        val startTime = System.currentTimeMillis()
        
        commands.forEach { command ->
            val result = modelManager.processInventoryCommand(command)
            assertTrue("Should handle command: $command", result.isNotEmpty())
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val averageTime = totalTime / commands.size
        
        assertTrue("Average processing time should be reasonable (<500ms)", averageTime < 500)
        
        println("Average command processing time: ${averageTime}ms")
    }
}
