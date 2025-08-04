package com.gemma3n.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the chat system components
 */
class ChatSystemTest {

    @Test
    fun chatMessage_creation_isCorrect() {
        // Test user message creation
        val userMessage = ChatMessage.createUserMessage("Hello AI!")
        assertTrue(userMessage.isUser)
        assertEquals("Hello AI!", userMessage.text)
        assertEquals(ChatMessage.MessageType.TEXT, userMessage.messageType)
        
        // Test AI response creation
        val aiMessage = ChatMessage.createAIResponse("Hello! How can I help you?")
        assertFalse(aiMessage.isUser)
        assertEquals("Hello! How can I help you?", aiMessage.text)
        assertEquals(ChatMessage.MessageType.AI_RESPONSE, aiMessage.messageType)
        
        // Test system message creation
        val systemMessage = ChatMessage.createSystemMessage("System initialized")
        assertFalse(systemMessage.isUser)
        assertEquals("System initialized", systemMessage.text)
        assertEquals(ChatMessage.MessageType.SYSTEM, systemMessage.messageType)
    }

    @Test
    fun chatMessage_timestamp_formatting() {
        val message = ChatMessage.createUserMessage("Test message")
        val formattedTime = message.getFormattedTime()
        
        // Should return time in HH:mm format
        assertTrue(formattedTime.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun chatAdapter_message_management() {
        val adapter = ChatAdapter()
        
        // Initially empty
        assertEquals(0, adapter.itemCount)
        
        // Add a message
        val message = ChatMessage.createUserMessage("Test message")
        adapter.addMessage(message)
        assertEquals(1, adapter.itemCount)
        
        // Add multiple messages
        val messages = listOf(
            ChatMessage.createAIResponse("AI response 1"),
            ChatMessage.createAIResponse("AI response 2")
        )
        adapter.addMessages(messages)
        assertEquals(3, adapter.itemCount)
        
        // Clear messages
        adapter.clearMessages()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun chatAdapter_viewType_detection() {
        val adapter = ChatAdapter()
        
        // Add different message types
        adapter.addMessage(ChatMessage.createUserMessage("User message"))
        adapter.addMessage(ChatMessage.createAIResponse("AI response"))
        adapter.addMessage(ChatMessage.createSystemMessage("System message"))
        
        // Test view type detection (would need to access private constants)
        // This is a basic test to ensure no crashes occur
        assertEquals(3, adapter.itemCount)
    }
}
