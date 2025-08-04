package com.gemma3n.app

/**
 * Data class representing a chat message in the conversation
 * Text-only implementation for initial chat system
 * Image functionality will be added in future phases
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT
) {

    /**
     * Types of messages supported in the chat (text-only for now)
     */
    enum class MessageType {
        TEXT,           // Regular text message
        AI_RESPONSE,    // AI response to user query
        SYSTEM         // System messages (errors, status updates)
        // IMAGE features will be added in future phases
    }

    /**
     * Get formatted timestamp for display
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * Factory methods for creating different message types
     */
    companion object {
        fun createUserMessage(text: String): ChatMessage {
            return ChatMessage(
                text = text,
                isUser = true,
                messageType = MessageType.TEXT
            )
        }

        fun createAIResponse(text: String): ChatMessage {
            return ChatMessage(
                text = text,
                isUser = false,
                messageType = MessageType.AI_RESPONSE
            )
        }

        fun createSystemMessage(text: String): ChatMessage {
            return ChatMessage(
                text = text,
                isUser = false,
                messageType = MessageType.SYSTEM
            )
        }
    }
}
