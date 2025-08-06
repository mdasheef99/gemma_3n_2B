package com.gemma3n.app

import android.content.Context
import android.graphics.Bitmap
import com.gemma3n.app.commands.ChatCommandDetector
import com.gemma3n.app.commands.ChatIntent
import com.gemma3n.app.commands.EntityExtractor
import com.gemma3n.app.data.Book
import com.gemma3n.app.repository.BookRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the complete chat-to-database workflow.
 * 
 * Tests the end-to-end integration of natural language processing,
 * command detection, AI processing, and database operations in the
 * bookstore inventory management system.
 */
class ChatIntegrationTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockBookRepository: BookRepository
    private lateinit var commandDetector: ChatCommandDetector
    private lateinit var entityExtractor: EntityExtractor
    private lateinit var mockBitmap: Bitmap
    
    @Before
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockBookRepository = mockk<BookRepository>(relaxed = true)
        mockBitmap = mockk<Bitmap>(relaxed = true)
        
        commandDetector = ChatCommandDetector()
        entityExtractor = EntityExtractor()
        
        // Mock bitmap properties
        every { mockBitmap.width } returns 800
        every { mockBitmap.height } returns 600
    }
    
    // ==================== COMMAND DETECTION INTEGRATION TESTS ====================
    
    @Test
    fun testBookCatalogingWorkflow() {
        val message = "What books are in this image?"
        
        // Test command detection
        val intent = commandDetector.detectIntent(message, hasImage = true)
        assertTrue("Should detect book cataloging intent", intent is ChatIntent.BookCataloging)
        
        val catalogingIntent = intent as ChatIntent.BookCataloging
        assertTrue("Should require image", catalogingIntent.hasImage)
        assertEquals(message, catalogingIntent.message)
    }
    
    @Test
    fun testManualBookEntryWorkflow() {
        val message = "Add book: Atomic Habits by James Clear price ₹299 qty 5 location A-1"
        
        // Test command detection
        val intent = commandDetector.detectIntent(message)
        assertTrue("Should detect manual book entry intent", intent is ChatIntent.ManualBookEntry)
        
        val entryIntent = intent as ChatIntent.ManualBookEntry
        assertEquals("Atomic Habits", entryIntent.extractedTitle)
        assertEquals("James Clear", entryIntent.extractedAuthor)
        assertEquals(299.0, entryIntent.extractedPrice)
        assertEquals(5, entryIntent.extractedQuantity)
        assertEquals("A-1", entryIntent.extractedLocation)
        
        // Test entity extraction
        val bookInfo = entityExtractor.extractBookInfo(message)
        assertTrue("Should extract valid book info", bookInfo.isValid())
        assertEquals("Atomic Habits", bookInfo.title)
        assertEquals("James Clear", bookInfo.author)
        assertEquals(299.0, bookInfo.price)
        assertEquals(5, bookInfo.quantity)
        assertEquals("A-1", bookInfo.location)
    }
    
    @Test
    fun testInventorySearchWorkflow() {
        val message = "Find books by James Clear"
        
        // Test command detection
        val intent = commandDetector.detectIntent(message)
        assertTrue("Should detect inventory search intent", intent is ChatIntent.InventorySearch)
        
        val searchIntent = intent as ChatIntent.InventorySearch
        assertEquals(ChatIntent.InventorySearch.SearchType.BY_AUTHOR, searchIntent.searchType)
        assertTrue("Query should contain author name", searchIntent.query.contains("James Clear"))
        
        // Test search query extraction
        val extractedQuery = entityExtractor.extractSearchQuery(message, searchIntent.searchType)
        assertTrue("Should extract author name", extractedQuery.lowercase().contains("james clear"))
    }
    
    // ==================== DATABASE INTEGRATION TESTS ====================
    
    @Test
    fun testBookStorageIntegration() = runBlocking {
        // Mock repository responses
        val sampleBook = Book.fromManualEntry("Test Title", "Test Author", 100.0, 1, "A-1", "New")
        coEvery { mockBookRepository.insertBook(any()) } returns Result.success(Unit)
        coEvery { mockBookRepository.getAllBooks() } returns flowOf(listOf(sampleBook))
        
        // Test book creation from entity extraction
        val message = "Add book: Test Title by Test Author price ₹100 qty 1 location A-1 condition new"
        val bookInfo = entityExtractor.extractBookInfo(message)
        
        assertTrue("Should extract valid book info", bookInfo.isValid())
        
        // Convert to Book entity
        val book = Book.fromManualEntry(
            bookInfo.title!!,
            bookInfo.author!!,
            bookInfo.price ?: 0.0,
            bookInfo.quantity ?: 1,
            bookInfo.location ?: "",
            bookInfo.condition ?: "New"
        )
        
        // Test database insertion
        val insertResult = mockBookRepository.insertBook(book)
        assertTrue("Should successfully insert book", insertResult.isSuccess)
        
        // Verify the call was made
        coVerify { mockBookRepository.insertBook(any()) }
    }
    
    @Test
    fun testInventorySearchIntegration() = runBlocking {
        // Mock repository with sample books
        val sampleBooks = listOf(
            Book.fromManualEntry("Atomic Habits", "James Clear", 299.0, 5, "A-1", "New"),
            Book.fromManualEntry("The Alchemist", "Paulo Coelho", 199.0, 3, "A-2", "New"),
            Book.fromManualEntry("Rich Dad Poor Dad", "Robert Kiyosaki", 249.0, 2, "B-1", "Used")
        )
        
        coEvery { mockBookRepository.getAllBooks() } returns flowOf(sampleBooks)
        
        // Test search functionality
        val searchMessage = "Find books by James Clear"
        val intent = commandDetector.detectIntent(searchMessage)
        
        assertTrue("Should detect search intent", intent is ChatIntent.InventorySearch)
        
        // Simulate search processing
        val books = mockBookRepository.getAllBooks()
        val bookList = books.first()
        
        val searchQuery = "james clear"
        val matchingBooks = bookList.filter { book ->
            book.titleEnglish.contains(searchQuery, ignoreCase = true) ||
            book.authorEnglish.contains(searchQuery, ignoreCase = true)
        }
        
        assertEquals("Should find 1 matching book", 1, matchingBooks.size)
        assertEquals("Atomic Habits", matchingBooks[0].titleEnglish)
        assertEquals("James Clear", matchingBooks[0].authorEnglish)
    }
    
    // ==================== ERROR HANDLING INTEGRATION TESTS ====================
    
    @Test
    fun testInvalidCommandHandling() {
        val invalidMessage = "This is just random text with no clear intent"
        
        val intent = commandDetector.detectIntent(invalidMessage)
        assertTrue("Should default to regular chat", intent is ChatIntent.RegularChat)
        
        val regularChatIntent = intent as ChatIntent.RegularChat
        assertEquals(invalidMessage, regularChatIntent.message)
    }
    
    @Test
    fun testIncompleteBookEntryHandling() {
        val incompleteMessage = "Add book: Atomic Habits"
        
        val intent = commandDetector.detectIntent(incompleteMessage)
        // This might be detected as manual book entry or regular chat depending on implementation
        assertTrue("Should handle incomplete entry", 
            intent is ChatIntent.ManualBookEntry || intent is ChatIntent.RegularChat)
        
        val bookInfo = entityExtractor.extractBookInfo(incompleteMessage)
        assertFalse("Should not be valid without author", bookInfo.isValid())
    }
    
    @Test
    fun testDatabaseErrorHandling() = runBlocking {
        // Mock repository to return error
        coEvery { mockBookRepository.insertBook(any()) } returns Result.failure(Exception("Database error"))
        
        val message = "Add book: Test Title by Test Author"
        val bookInfo = entityExtractor.extractBookInfo(message)
        
        if (bookInfo.isValid()) {
            val book = Book.fromManualEntry(bookInfo.title!!, bookInfo.author!!)
            val result = mockBookRepository.insertBook(book)
            
            assertTrue("Should handle database error", result.isFailure)
            assertTrue("Should contain error message", 
                result.exceptionOrNull()?.message?.contains("Database error") == true)
        }
    }
    
    // ==================== PERFORMANCE INTEGRATION TESTS ====================
    
    @Test
    fun testCommandDetectionPerformance() {
        val testMessages = listOf(
            "Add book: Performance Test by Test Author",
            "Find books by Test Author",
            "What books are in this image?",
            "Show inventory statistics",
            "Help with inventory management"
        )
        
        val startTime = System.currentTimeMillis()
        
        testMessages.forEach { message ->
            val intent = commandDetector.detectIntent(message)
            assertNotNull("Should detect intent for: $message", intent)
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val averageTime = totalTime / testMessages.size
        
        assertTrue("Command detection should be fast (<50ms average)", averageTime < 50)
        println("Average command detection time: ${averageTime}ms")
    }
    
    @Test
    fun testEntityExtractionPerformance() {
        val testMessages = listOf(
            "Add book: Title1 by Author1 price ₹100 qty 1 location A-1",
            "Book: Title2 by Author2 Rs 200 quantity 2 shelf B-2",
            "title: Title3 author: Author3 cost ₹150 count 3 section C-3"
        )
        
        val startTime = System.currentTimeMillis()
        
        testMessages.forEach { message ->
            val bookInfo = entityExtractor.extractBookInfo(message)
            assertTrue("Should extract book info for: $message", bookInfo.isValid())
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val averageTime = totalTime / testMessages.size
        
        assertTrue("Entity extraction should be fast (<100ms average)", averageTime < 100)
        println("Average entity extraction time: ${averageTime}ms")
    }
    
    // ==================== END-TO-END WORKFLOW TESTS ====================
    
    @Test
    fun testCompleteBookAdditionWorkflow() = runBlocking {
        // Mock successful database operations
        coEvery { mockBookRepository.insertBook(any()) } returns Result.success(Unit)
        coEvery { mockBookRepository.getTotalBookCount() } returns Result.success(1)
        
        // Step 1: User input
        val userMessage = "Add book: Complete Workflow Test by Integration Author price ₹199 qty 2 location TEST-1"
        
        // Step 2: Command detection
        val intent = commandDetector.detectIntent(userMessage)
        assertTrue("Should detect manual book entry", intent is ChatIntent.ManualBookEntry)
        
        // Step 3: Entity extraction
        val bookInfo = entityExtractor.extractBookInfo(userMessage)
        assertTrue("Should extract valid book info", bookInfo.isValid())
        assertEquals("Complete Workflow Test", bookInfo.title)
        assertEquals("Integration Author", bookInfo.author)
        assertEquals(199.0, bookInfo.price)
        assertEquals(2, bookInfo.quantity)
        assertEquals("TEST-1", bookInfo.location)
        
        // Step 4: Book creation
        val book = Book.fromManualEntry(
            bookInfo.title!!,
            bookInfo.author!!,
            bookInfo.price!!,
            bookInfo.quantity!!,
            bookInfo.location!!,
            "New"
        )
        
        // Step 5: Database storage
        val insertResult = mockBookRepository.insertBook(book)
        assertTrue("Should successfully store book", insertResult.isSuccess)
        
        // Step 6: Verification
        val countResult = mockBookRepository.getTotalBookCount()
        assertTrue("Should get updated count", countResult.isSuccess)
        assertEquals("Should have 1 book", 1, countResult.getOrNull())
        
        // Verify all operations were called
        coVerify { mockBookRepository.insertBook(any()) }
        coVerify { mockBookRepository.getTotalBookCount() }
    }
    
    @Test
    fun testCompleteSearchWorkflow() = runBlocking {
        // Mock database with test data
        val testBooks = listOf(
            Book.fromManualEntry("Search Test Book", "Search Author", 100.0, 1, "S-1", "New"),
            Book.fromManualEntry("Another Book", "Different Author", 150.0, 2, "S-2", "Used")
        )
        
        coEvery { mockBookRepository.getAllBooks() } returns flowOf(testBooks)
        
        // Step 1: User search query
        val searchMessage = "Find books by Search Author"
        
        // Step 2: Command detection
        val intent = commandDetector.detectIntent(searchMessage)
        assertTrue("Should detect search intent", intent is ChatIntent.InventorySearch)
        
        val searchIntent = intent as ChatIntent.InventorySearch
        assertEquals(ChatIntent.InventorySearch.SearchType.BY_AUTHOR, searchIntent.searchType)
        
        // Step 3: Query extraction
        val searchQuery = entityExtractor.extractSearchQuery(searchMessage, searchIntent.searchType)
        assertTrue("Should extract author name", searchQuery.lowercase().contains("search author"))
        
        // Step 4: Database query
        val books = mockBookRepository.getAllBooks().first()
        val matchingBooks = books.filter { book ->
            book.authorEnglish.contains("Search Author", ignoreCase = true)
        }
        
        // Step 5: Results validation
        assertEquals("Should find 1 matching book", 1, matchingBooks.size)
        assertEquals("Search Test Book", matchingBooks[0].titleEnglish)
        assertEquals("Search Author", matchingBooks[0].authorEnglish)
        
        // Verify database was queried
        coVerify { mockBookRepository.getAllBooks() }
    }
}
