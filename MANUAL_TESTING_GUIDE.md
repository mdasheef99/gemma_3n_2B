# 🧪 MANUAL TESTING GUIDE - Gemma 3n Download Flow

## 📋 Pre-Testing Setup

### Requirements
- Android emulator (Pixel 3 API 30 or similar)
- APK file: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Test mode enabled (10MB download instead of 3.1GB)

### Installation
```bash
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

---

## 🎯 TEST SCENARIOS

### 1. Initial App Launch - Download UI Display
**Expected Behavior:**
- App shows prominent download section when model is missing
- Download button displays "📥 Download AI Model (3.1GB) for Offline Use"
- Storage information shows available space
- AI features (chat input, image buttons) are disabled
- Settings button (⚙️) remains enabled

**Test Steps:**
1. Launch app for first time
2. Verify download section is visible
3. Check that chat input is disabled with hint "AI model loading..."
4. Verify image buttons are disabled and grayed out
5. Confirm settings button is clickable

**✅ Pass Criteria:**
- Download section prominently displayed
- All AI features properly disabled
- Storage info shows accurate available space
- Settings button functional

---

### 2. Storage Validation
**Expected Behavior:**
- Shows current free space vs required space
- Download button disabled if insufficient storage
- Clear warning message for low storage

**Test Steps:**
1. Check storage display shows "📦 Model Size: 10MB (Test Mode)"
2. Verify available space is displayed accurately
3. If sufficient space: download button enabled
4. If insufficient space: download button disabled with warning

**✅ Pass Criteria:**
- Storage information accurate and clear
- Download button state matches storage availability
- Warning messages helpful and actionable

---

### 3. Download Process - Progress Tracking
**Expected Behavior:**
- Real-time progress updates every 500ms
- Dual display: percentage + MB transferred
- Download speed in MB/s
- ETA calculation and display
- Visual progress bar updates

**Test Steps:**
1. Tap "Download AI Model" button
2. Observe progress bar appears
3. Watch for real-time updates showing:
   - Percentage (0% → 100%)
   - Data transferred (0MB → 10MB)
   - Download speed (X.X MB/s)
   - Time remaining (decreasing)
4. Verify progress bar fills proportionally
5. Check cancel button appears during download

**✅ Pass Criteria:**
- Progress updates smoothly and frequently
- All progress metrics display correctly
- Visual progress bar matches percentage
- Cancel button available during download

---

### 4. Download Cancellation
**Expected Behavior:**
- Cancel button stops download immediately
- Partial files cleaned up automatically
- UI resets to initial download state
- Clear cancellation message displayed

**Test Steps:**
1. Start download
2. Wait for 20-30% progress
3. Tap "❌ Cancel Download" button
4. Verify download stops immediately
5. Check UI returns to initial state
6. Confirm no partial files remain
7. Verify cancellation message in chat

**✅ Pass Criteria:**
- Download stops immediately when cancelled
- UI cleanly resets to initial state
- Partial files automatically cleaned up
- User feedback provided

---

### 5. Error Handling - Network Issues
**Expected Behavior:**
- Specific error messages for different failure types
- Automatic retry with exponential backoff
- Clean error recovery and UI reset

**Test Steps:**
1. Start download
2. Disable Wi-Fi/network during download
3. Observe error handling:
   - Specific network error message
   - Automatic retry attempts
   - Final failure state with retry button
4. Re-enable network and test retry

**✅ Pass Criteria:**
- Specific error messages displayed
- Automatic retry attempts occur
- Retry button functional after failure
- Clean recovery when network restored

---

### 6. Download Completion
**Expected Behavior:**
- Success notification displayed
- Download section disappears
- AI features become enabled
- Chat interface becomes functional

**Test Steps:**
1. Complete full download (10MB in test mode)
2. Verify success message appears
3. Check download section disappears
4. Confirm AI features are enabled:
   - Chat input becomes active
   - Image buttons become enabled
   - Send button functional
5. Test basic chat functionality

**✅ Pass Criteria:**
- Clear success notification
- Download UI disappears
- All AI features properly enabled
- Chat interface fully functional

---

### 7. Model Settings Activity
**Expected Behavior:**
- Settings accessible via ⚙️ button
- Model information displayed accurately
- Delete/redownload options functional
- Storage information updated

**Test Steps:**
1. Tap ⚙️ settings button
2. Verify settings activity opens
3. Check model information display:
   - Status (Available/Not Found)
   - File location
   - File size
   - Download date
4. Test "Refresh Information" button
5. Test "Delete Model" with confirmation
6. Test "Redownload Model" functionality

**✅ Pass Criteria:**
- Settings activity opens correctly
- Model information accurate and detailed
- All management functions work properly
- Confirmations prevent accidental actions

---

### 8. Resume Capability (Advanced)
**Expected Behavior:**
- Interrupted downloads can be resumed
- Progress continues from interruption point
- No duplicate data downloaded

**Test Steps:**
1. Start download
2. Force-close app at 50% progress
3. Reopen app
4. Start download again
5. Verify resume from previous point

**✅ Pass Criteria:**
- Download resumes from interruption point
- No duplicate downloading occurs
- Progress tracking accurate after resume

---

### 9. UI Responsiveness During Download
**Expected Behavior:**
- App remains responsive during download
- Non-AI features continue to work
- Settings accessible during download
- Smooth UI interactions

**Test Steps:**
1. Start download
2. Test UI responsiveness:
   - Scroll through chat
   - Access settings
   - Navigate between screens
   - Interact with non-AI elements
3. Verify no UI freezing or lag

**✅ Pass Criteria:**
- App remains fully responsive
- All non-AI features functional
- Smooth animations and transitions
- No performance degradation

---

## 📊 TEST RESULTS TEMPLATE

### Test Session Information
- **Date:** ___________
- **Device/Emulator:** ___________
- **Android Version:** ___________
- **App Version:** ___________

### Test Results
| Test Scenario | Status | Notes |
|---------------|--------|-------|
| Initial Launch | ⬜ Pass ⬜ Fail | |
| Storage Validation | ⬜ Pass ⬜ Fail | |
| Progress Tracking | ⬜ Pass ⬜ Fail | |
| Download Cancellation | ⬜ Pass ⬜ Fail | |
| Error Handling | ⬜ Pass ⬜ Fail | |
| Download Completion | ⬜ Pass ⬜ Fail | |
| Settings Activity | ⬜ Pass ⬜ Fail | |
| Resume Capability | ⬜ Pass ⬜ Fail | |
| UI Responsiveness | ⬜ Pass ⬜ Fail | |

### Overall Assessment
- **Total Tests:** 9
- **Passed:** ___/9
- **Failed:** ___/9
- **Success Rate:** ___%

### Issues Found
1. ________________________________
2. ________________________________
3. ________________________________

### Recommendations
1. ________________________________
2. ________________________________
3. ________________________________

---

## 🚀 Production Readiness Checklist

- ⬜ All test scenarios pass
- ⬜ No critical bugs identified
- ⬜ Performance acceptable on target devices
- ⬜ Error handling comprehensive
- ⬜ User experience smooth and intuitive
- ⬜ Storage management functional
- ⬜ Download flow robust and reliable

**Ready for Production:** ⬜ Yes ⬜ No (with conditions)
