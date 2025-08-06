package com.gemma3n.app.commands

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EntityExtractor.
 * 
 * Tests all entity extraction scenarios to ensure >85% accuracy
 * for extracting book information from natural language text.
 */
class EntityExtractorTest {
    
    private lateinit var extractor: EntityExtractor
    
    @Before
    fun setUp() {
        extractor = EntityExtractor()
    }
    
    // ==================== PRICE EXTRACTION TESTS ====================
    
    @Test
    fun testPriceExtractionRupees() {
        val testCases = mapOf(
            "Price ₹299" to 299.0,
            "Rs. 150" to 150.0,
            "Rs 99.50" to 99.50,
            "rupees 250" to 250.0,
            "price: ₹199.99" to 199.99,
            "cost ₹75" to 75.0
        )
        
        testCases.forEach { (text, expected) ->
            val price = extractor.extractPrice(text)
            assertEquals("Price extraction failed for '$text'", expected, price)
        }
    }
    
    @Test
    fun testPriceExtractionDollars() {
        val testCases = mapOf(
            "$25.99" to 25.99,
            "$10" to 10.0,
            "$199.50" to 199.50
        )
        
        testCases.forEach { (text, expected) ->
            val price = extractor.extractPrice(text)
            assertEquals("Dollar price extraction failed for '$text'", expected, price)
        }
    }
    
    @Test
    fun testInvalidPriceExtraction() {
        val invalidTexts = listOf(
            "No price here",
            "Price: free",
            "₹0",
            "Rs -50"
        )
        
        invalidTexts.forEach { text ->
            val price = extractor.extractPrice(text)
            assertNull("Should not extract price from '$text'", price)
        }
    }
    
    // ==================== QUANTITY EXTRACTION TESTS ====================
    
    @Test
    fun testQuantityExtraction() {
        val testCases = mapOf(
            "qty 5" to 5,
            "quantity: 10" to 10,
            "count 3" to 3,
            "5 copies" to 5,
            "2 books" to 2,
            "x 7" to 7
        )
        
        testCases.forEach { (text, expected) ->
            val quantity = extractor.extractQuantity(text)
            assertEquals("Quantity extraction failed for '$text'", expected, quantity)
        }
    }
    
    @Test
    fun testInvalidQuantityExtraction() {
        val invalidTexts = listOf(
            "No quantity here",
            "qty 0",
            "quantity -5"
        )
        
        invalidTexts.forEach { text ->
            val quantity = extractor.extractQuantity(text)
            assertNull("Should not extract quantity from '$text'", quantity)
        }
    }
    
    // ==================== LOCATION EXTRACTION TESTS ====================
    
    @Test
    fun testLocationExtraction() {
        val testCases = mapOf(
            "location A-5" to "A-5",
            "shelf: B12" to "B12",
            "section C-3" to "C-3",
            "at location-15" to "location-15",
            "in shelf-A" to "shelf-A"
        )
        
        testCases.forEach { (text, expected) ->
            val location = extractor.extractLocation(text)
            assertEquals("Location extraction failed for '$text'", expected, location)
        }
    }
    
    // ==================== CONDITION EXTRACTION TESTS ====================
    
    @Test
    fun testConditionExtraction() {
        val testCases = mapOf(
            "condition new" to "New",
            "brand new book" to "New",
            "mint condition" to "New",
            "used book" to "Used",
            "second hand" to "Used",
            "pre-owned" to "Used",
            "damaged cover" to "Damaged",
            "worn pages" to "Damaged",
            "torn book" to "Damaged"
        )
        
        testCases.forEach { (text, expected) ->
            val condition = extractor.extractCondition(text)
            assertEquals("Condition extraction failed for '$text'", expected, condition)
        }
    }
    
    // ==================== BOOK INFO EXTRACTION TESTS ====================
    
    @Test
    fun testCompleteBookInfoExtraction() {
        val message = "Add book: Atomic Habits by James Clear price ₹299 qty 5 location A-1 condition new"
        val info = extractor.extractBookInfo(message)
        
        assertTrue("Should be valid book info", info.isValid())
        assertEquals("Atomic Habits", info.title)
        assertEquals("James Clear", info.author)
        assertEquals(299.0, info.price)
        assertEquals(5, info.quantity)
        assertEquals("A-1", info.location)
        assertEquals("New", info.condition)
        assertTrue("Confidence should be high", info.confidence > 0.8)
    }
    
    @Test
    fun testMinimalBookInfoExtraction() {
        val message = "Book: The Alchemist by Paulo Coelho"
        val info = extractor.extractBookInfo(message)
        
        assertTrue("Should be valid book info", info.isValid())
        assertEquals("The Alchemist", info.title)
        assertEquals("Paulo Coelho", info.author)
        assertNull("Price should be null", info.price)
        assertNull("Quantity should be null", info.quantity)
        assertNull("Location should be null", info.location)
        assertNull("Condition should be null", info.condition)
    }
    
    @Test
    fun testBookInfoWithQuotes() {
        val message = "Add book: \"Rich Dad Poor Dad\" by \"Robert Kiyosaki\""
        val info = extractor.extractBookInfo(message)
        
        assertTrue("Should be valid book info", info.isValid())
        assertEquals("Rich Dad Poor Dad", info.title)
        assertEquals("Robert Kiyosaki", info.author)
    }
    
    @Test
    fun testBookInfoWithSpecialCharacters() {
        val message = "Book: C++ Programming by Bjarne Stroustrup"
        val info = extractor.extractBookInfo(message)
        
        assertTrue("Should be valid book info", info.isValid())
        assertEquals("C++ Programming", info.title)
        assertEquals("Bjarne Stroustrup", info.author)
    }
    
    @Test
    fun testInvalidBookInfo() {
        val invalidMessages = listOf(
            "Add book: by James Clear", // No title
            "Add book: Atomic Habits by", // No author
            "Add book:", // No title or author
            "Just some random text" // No book info
        )
        
        invalidMessages.forEach { message ->
            val info = extractor.extractBookInfo(message)
            assertFalse("Should not be valid book info for '$message'", info.isValid())
        }
    }
    
    // ==================== SEARCH QUERY EXTRACTION TESTS ====================
    
    @Test
    fun testSearchQueryExtraction() {
        val testCases = mapOf(
            "Find Atomic Habits" to "atomic habits",
            "Search for James Clear" to "james clear",
            "Show me books about programming" to "books about programming",
            "List all fiction books" to "all fiction books",
            "Get books by Paulo Coelho" to "books by paulo coelho"
        )
        
        testCases.forEach { (message, expected) ->
            val query = extractor.extractSearchQuery(message, ChatIntent.InventorySearch.SearchType.GENERAL)
            assertEquals("Search query extraction failed for '$message'", expected, query.lowercase())
        }
    }
    
    @Test
    fun testSearchQueryByAuthor() {
        val message = "Find books by author James Clear"
        val query = extractor.extractSearchQuery(message, ChatIntent.InventorySearch.SearchType.BY_AUTHOR)
        
        assertTrue("Query should contain author name", query.lowercase().contains("james clear"))
        assertFalse("Query should not contain 'by author'", query.lowercase().contains("by author"))
    }
    
    @Test
    fun testSearchQueryByLocation() {
        val message = "Show books in location A-5"
        val query = extractor.extractSearchQuery(message, ChatIntent.InventorySearch.SearchType.BY_LOCATION)
        
        assertTrue("Query should contain location", query.contains("A-5"))
        assertFalse("Query should not contain 'in location'", query.lowercase().contains("in location"))
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidBookInfoValidation() {
        val validInfo = EntityExtractor.ExtractedBookInfo(
            title = "Test Title",
            author = "Test Author",
            price = 100.0,
            quantity = 5,
            location = "A-1",
            condition = "New"
        )
        
        val errors = extractor.validateBookInfo(validInfo)
        assertTrue("Valid book info should have no errors", errors.isEmpty())
    }
    
    @Test
    fun testInvalidBookInfoValidation() {
        val invalidInfo = EntityExtractor.ExtractedBookInfo(
            title = "", // Invalid: empty title
            author = null, // Invalid: null author
            price = -10.0, // Invalid: negative price
            quantity = 0, // Invalid: zero quantity
            condition = "Invalid" // Invalid: not in allowed conditions
        )
        
        val errors = extractor.validateBookInfo(invalidInfo)
        assertEquals("Should have 5 validation errors", 5, errors.size)
        
        assertTrue("Should have title error", errors.any { it.contains("title") })
        assertTrue("Should have author error", errors.any { it.contains("author") })
        assertTrue("Should have price error", errors.any { it.contains("price") })
        assertTrue("Should have quantity error", errors.any { it.contains("quantity") })
        assertTrue("Should have condition error", errors.any { it.contains("condition") })
    }
    
    // ==================== CONFIDENCE CALCULATION TESTS ====================
    
    @Test
    fun testConfidenceCalculation() {
        // High confidence: all fields present
        val highConfidenceInfo = EntityExtractor.ExtractedBookInfo(
            title = "Test Title",
            author = "Test Author",
            price = 100.0,
            quantity = 5,
            location = "A-1",
            condition = "New"
        )
        val highConfidence = highConfidenceInfo.calculateConfidence()
        assertTrue("High confidence should be > 0.9", highConfidence > 0.9)
        
        // Medium confidence: title and author only
        val mediumConfidenceInfo = EntityExtractor.ExtractedBookInfo(
            title = "Test Title",
            author = "Test Author"
        )
        val mediumConfidence = mediumConfidenceInfo.calculateConfidence()
        assertTrue("Medium confidence should be around 0.8", mediumConfidence >= 0.7 && mediumConfidence <= 0.9)
        
        // Low confidence: title only
        val lowConfidenceInfo = EntityExtractor.ExtractedBookInfo(
            title = "Test Title"
        )
        val lowConfidence = lowConfidenceInfo.calculateConfidence()
        assertTrue("Low confidence should be < 0.5", lowConfidence < 0.5)
    }
    
    // ==================== LANGUAGE DETECTION TESTS ====================
    
    @Test
    fun testLanguageDetection() {
        val englishInfo = extractor.extractBookInfo("Book: English Title by English Author")
        assertEquals("Should detect English", 
            EntityExtractor.ExtractedBookInfo.Language.ENGLISH, englishInfo.language)
        
        // Note: Kannada detection would require actual Kannada text
        // This is a placeholder test for the language detection functionality
    }
    
    // ==================== EDGE CASES AND ERROR HANDLING ====================
    
    @Test
    fun testEmptyMessageExtraction() {
        val info = extractor.extractBookInfo("")
        assertFalse("Empty message should not produce valid book info", info.isValid())
        assertEquals("Confidence should be 0", 0.0, info.confidence, 0.01)
    }
    
    @Test
    fun testVeryLongMessageExtraction() {
        val longTitle = "A".repeat(1000)
        val longAuthor = "B".repeat(1000)
        val message = "Add book: $longTitle by $longAuthor"
        
        val info = extractor.extractBookInfo(message)
        assertTrue("Should handle long messages", info.isValid())
        assertEquals(longTitle, info.title)
        assertEquals(longAuthor, info.author)
    }
    
    @Test
    fun testSpecialCharactersInExtraction() {
        val message = "Book: C++ & Java: The Complete Guide by John O'Reilly & Co."
        val info = extractor.extractBookInfo(message)
        
        assertTrue("Should handle special characters", info.isValid())
        assertTrue("Title should contain special chars", info.title?.contains("&") == true)
        assertTrue("Author should contain apostrophe", info.author?.contains("'") == true)
    }
    
    // ==================== ACCURACY TESTS ====================
    
    @Test
    fun testEntityExtractionAccuracy() {
        val testCases = listOf(
            // Complete extractions
            "Add book: Title1 by Author1 price ₹100 qty 2 location A-1 condition new",
            "Book: Title2 by Author2 Rs 200 quantity 3 shelf B-2 used",
            "title: Title3 author: Author3 cost ₹150 count 1 section C-3 damaged",
            
            // Partial extractions
            "Add book: Title4 by Author4",
            "Book: Title5 by Author5 price ₹300",
            "Title6 by Author6 qty 5",
            
            // Different formats
            "\"Title7\" by \"Author7\"",
            "Book Title8 written by Author8",
            "Add Title9, author is Author9"
        )
        
        var successfulExtractions = 0
        
        testCases.forEach { message ->
            val info = extractor.extractBookInfo(message)
            if (info.isValid()) {
                successfulExtractions++
            } else {
                println("FAILED: Could not extract valid info from '$message'")
            }
        }
        
        val accuracy = successfulExtractions.toDouble() / testCases.size
        println("Entity extraction accuracy: ${(accuracy * 100).toInt()}%")
        
        assertTrue("Entity extraction accuracy should be >85%", accuracy >= 0.85)
    }
}
