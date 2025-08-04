package com.gemma3n.app

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * ViewHolder classes for different chat message types
 * Text-only implementation for initial chat system
 */

/**
 * ViewHolder for user messages (right-aligned blue bubbles)
 */
class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.messageText)
    private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
    
    fun bind(message: ChatMessage) {
        messageText.text = message.text
        messageTime.text = message.getFormattedTime()
    }
}

/**
 * ViewHolder for AI messages (left-aligned gray bubbles)
 */
class AIMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.messageText)
    private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
    
    fun bind(message: ChatMessage) {
        messageText.text = message.text
        messageTime.text = message.getFormattedTime()
    }
}

/**
 * ViewHolder for system messages (centered, neutral styling)
 * Will be implemented when needed for error messages, status updates, etc.
 */
class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val messageText: TextView = itemView.findViewById(R.id.messageText)
    private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
    
    fun bind(message: ChatMessage) {
        messageText.text = message.text
        messageTime.text = message.getFormattedTime()
    }
}
