# Gemma 3n Android App

A fully offline Android application featuring Google's Gemma 3n 2B language model with MediaPipe LLM integration for on-device AI chat functionality.

## 🚀 Features

- **Offline AI Chat**: Complete text-based conversations without internet connectivity
- **Google Gemma 3n 2B Model**: State-of-the-art language model running entirely on-device
- **MediaPipe LLM Integration**: Optimized for mobile performance with CPU backend
- **Samsung S23 Optimized**: Tested and optimized for Samsung Galaxy S23 performance
- **Persistent Conversations**: Chat history maintained across app restarts
- **Memory Efficient**: Fresh session management prevents context overflow
- **Fast Responses**: 2-5 second response times on flagship Android devices

## 📱 Technical Specifications

### Model Configuration
- **Model**: Gemma 3n 2B (int4 quantized)
- **Model Size**: 3.1GB
- **Context Window**: 512 tokens per session
- **Backend**: CPU (ARM64-v8a optimized)
- **Vision Modality**: Disabled (text-only mode)

### Performance Metrics
- **Initialization Time**: ~6 seconds on Samsung S23
- **Response Time**: 2-5 seconds per query
- **Memory Usage**: ~2-3GB during inference
- **Architecture**: ARM64-v8a APK

## 🛠️ Architecture

### Core Components
- **ModelManager**: Handles model loading, session management, and inference
- **ChatSystem**: Manages conversation flow and message handling
- **UIStateManager**: Controls UI states and user interactions
- **ChatAdapter**: RecyclerView adapter for chat interface
- **ErrorHandler**: Comprehensive error handling and recovery

### Key Technical Decisions
- **Fresh Session Per Query**: Prevents context overflow and blank responses
- **Background Threading**: All AI processing on IO threads to prevent ANR
- **External Storage**: Model files stored in app-specific external directory
- **Conversation Persistence**: Chat history maintained in memory with UI restoration

## 🔧 Setup and Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Device with ARM64-v8a architecture
- Minimum 4GB RAM (8GB+ recommended)
- 5GB+ available storage

### Model Deployment
1. Download the Gemma 3n model file (3.1GB)
2. Deploy to device external storage: `/storage/emulated/0/Android/data/com.gemma3n.app/files/`
3. Model file: `gemma-3n-E2B-it-int4.task`

### Build Instructions
```bash
git clone https://github.com/mdasheef99/gemma_3n_2B.git
cd gemma_3n_2B
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## 🧪 Testing

### Verified Devices
- ✅ Samsung Galaxy S23 (Primary test device)
- ✅ Android Emulator (ARM64 system images)

### Test Scenarios
- ✅ Model initialization and loading
- ✅ Text-only chat functionality
- ✅ Session management and context handling
- ✅ App restart and conversation persistence
- ✅ Memory management and performance
- ✅ Error handling and recovery

## 🐛 Known Issues & Solutions

### Resolved Issues
- **ANR/Crashes**: Fixed by moving AI processing to background threads
- **Blank Responses**: Resolved with fresh session management
- **UI Display**: Fixed RecyclerView scrolling and message visibility
- **Context Overflow**: Prevented with per-query session creation

### Current Limitations
- Text-only mode (vision modality disabled)
- No conversation history persistence across app uninstalls
- 512 token limit per session (configurable)

## 🔮 Future Enhancements

### Planned Features
- **Image Processing**: Enable vision modality for image analysis
- **Conversation Export**: Save/export chat histories
- **Model Updates**: Support for newer Gemma model versions
- **Performance Optimization**: GPU backend support
- **Extended Context**: Increase token limits for longer conversations

## 📊 Development History

### Major Milestones
1. **Initial Setup**: MediaPipe LLM integration and model loading
2. **Crash Resolution**: Fixed ANR issues with proper threading
3. **UI Implementation**: Complete chat interface with message display
4. **Session Management**: Resolved blank response issues
5. **Performance Optimization**: Samsung S23 deployment and testing

## 🤝 Contributing

This project is part of the Google Gemma 3n Kaggle competition focusing on mobile/edge deployment.

## 📄 License

This project is developed for educational and competition purposes.

## 🙏 Acknowledgments

- Google Gemma Team for the language model
- MediaPipe Team for the mobile LLM framework
- Kaggle for hosting the Gemma 3n competition
