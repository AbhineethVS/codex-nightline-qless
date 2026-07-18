# Parayoo App — Master Plan

## What We're Building

A floating bubble Android app that converts spoken Malayalam into natural Manglish
(romanized Malayalam+English mix) and auto-types or copies it into any app —
primarily WhatsApp.

Think Wispr Flow, but for Manglish.

---

## The Pipeline

```
User taps floating bubble
        ↓
Records audio (in-app microphone)
        ↓
Sarvam AI STT API → Malayalam script
        ↓
Claude/GPT-4o API → Natural Manglish romanization
        ↓
Auto-types into active text field (Accessibility Service)
OR copies to clipboard (user's choice in settings)
```

---

## Tech Stack

- **Language:** Kotlin
- **Platform:** Android (min SDK 26)
- **STT:** Sarvam AI API (Malayalam speech to text)
- **LLM:** Claude API or GPT-4o (Manglish conversion prompt)
- **Analytics:** PostHog (anonymous, no login required)
- **Local Storage:** SharedPreferences (device UUID, daily count, settings)
- **No backend needed for v1**

---

## Android Permissions Required

- `SYSTEM_ALERT_WINDOW` — floating bubble overlay over all apps
- `RECORD_AUDIO` — microphone access
- `BIND_ACCESSIBILITY_SERVICE` — auto-type into any text field
- `INTERNET` — API calls to Sarvam and Claude/GPT
- `FOREGROUND_SERVICE` — keep bubble alive in background

---

## Project Structure

```
manglish/
├── app/
│   ├── manifest/
│   │   └── AndroidManifest.xml
│   ├── src/main/kotlin/com/manglish/
│   │   ├── MainActivity.kt              # Entry point, permission handling
│   │   ├── FloatingBubbleService.kt     # Overlay bubble, always on top
│   │   ├── RecordingManager.kt          # Audio recording logic
│   │   ├── SarvamSTTService.kt          # Sarvam API call (STT)
│   │   ├── ManglishConverter.kt         # Claude/GPT API call (LLM)
│   │   ├── AccessibilityTypingService.kt # Auto-type into active text field
│   │   ├── UsageLimitManager.kt         # 20 recordings/day per device UUID
│   │   ├── AnalyticsManager.kt          # PostHog anonymous analytics
│   │   └── SettingsActivity.kt          # Toggle auto-type vs copy mode
│   └── res/
│       ├── layout/
│       │   ├── bubble_layout.xml        # Floating bubble UI
│       │   ├── result_card.xml          # Manglish result display
│       │   └── activity_settings.xml   # Settings screen
│       └── drawable/
│           └── ic_mic.xml              # Mic icon
```

---

## UI — What It Looks Like

### Floating Bubble (always visible)

- Small circular button with mic icon
- Draggable anywhere on screen
- Stays on top of all apps including WhatsApp
- Tap to start recording

### Recording State

- Bubble pulses/animates while listening
- Tap again to stop recording

### Result Card (appears above bubble after processing)

- Shows Manglish text output
- If auto-type mode: automatically types into active field, card dismisses
- If copy mode: "Copy" button on card, tap to copy, card dismisses
- "Re-record" button to try again
- Auto-dismisses after 5 seconds

### Settings Screen (accessed by long-pressing bubble)

- Toggle: Auto-type mode ON/OFF (default OFF)
- Explanation of accessibility permission when toggling ON
- Daily usage counter: "X of 20 recordings used today"
- App version

---

## Core Features — V1

### 1. Floating Bubble

- Implemented as a Foreground Service
- Uses SYSTEM_ALERT_WINDOW permission
- WindowManager to draw over other apps
- Draggable with touch listener

### 2. Audio Recording

- MediaRecorder in Kotlin
- Records as WAV or FLAC (Sarvam accepts both)
- Max recording duration: 30 seconds
- Auto-stops at 30 seconds if user forgets

### 3. Sarvam STT Integration

```
POST https://api.sarvam.ai/speech-to-text
Headers: api-subscription-key: YOUR_KEY
Body: audio file + language_code: ml-IN
Response: { transcript: "Malayalam script text" }
```

### 4. Manglish LLM Conversion

Send Malayalam transcript to Claude/GPT with this prompt:

```
You are a Manglish converter. Convert the following
Malayalam script text into natural Manglish — the way
a young Malayali would type it on WhatsApp.
Manglish is romanized Malayalam mixed with English words.
Keep English words in English. Romanize Malayalam words
phonetically the way Malayalis naturally text.
Do not translate. Do not add punctuation artificially.
Just output the Manglish text, nothing else.

Malayalam text: {transcript}
```

### 5. Auto-type via Accessibility Service

- Extend AccessibilityService
- Find focused EditText in active window
- Inject Manglish text as input
- Fallback to clipboard if no focused field found

### 6. Usage Limit

- Generate UUID on first install, store in SharedPreferences
- Store daily count + date in SharedPreferences
- Reset count at midnight
- At 20 recordings: show "Come back tomorrow or share the app" screen
- Share button opens WhatsApp share with pre-written message

### 7. PostHog Analytics (anonymous)

Track these events:

- `app_opened`
- `recording_started`
- `recording_completed` (with duration)
- `manglish_generated` (success)
- `api_error` (with error type)
- `auto_type_used`
- `copy_used`
- `limit_reached`
- `share_tapped`

---

## API Keys Needed

- Sarvam AI API key (already redeemed $1000 credits)
- Claude API key OR OpenAI API key (for LLM pass)
- PostHog API key (free tier)

Store all keys in local.properties (never commit to git).

---

## Build Order (Do This Exactly)

### Phase 1 — Skeleton (Day 1)

1. Create new Android project in Kotlin
2. Set up all permissions in AndroidManifest.xml
3. Build floating bubble that appears and is draggable
4. Get bubble to survive app close (Foreground Service)

### Phase 2 — Recording (Day 1-2)

5. Add mic button tap → start recording
6. Add tap again → stop recording
7. Show recording animation on bubble

### Phase 3 — STT (Day 2)

8. Send recorded audio to Sarvam API
9. Display raw Malayalam transcript in result card
10. Test with real Malayalam speech

### Phase 4 — LLM (Day 2-3)

11. Send Malayalam transcript to Claude/GPT
12. Display Manglish output in result card
13. Test quality of Manglish output

### Phase 5 — Output (Day 3)

14. Implement clipboard copy
15. Implement Accessibility Service auto-type
16. Add toggle in settings

### Phase 6 — Limits + Analytics (Day 3-4)

17. Implement 20/day usage limit
18. Add share screen at limit
19. Integrate PostHog

### Phase 7 — Polish (Day 4-5)

20. Fix UI, animations, edge cases
21. Test on multiple Android devices
22. Test with real WhatsApp usage
23. Fix bugs

---

## What We Are NOT Building in V1

- No login / signup
- No backend / database
- No payment system
- No iOS version
- No language other than Malayalam→Manglish
- No history of past recordings
- No onboarding screens
- No word-by-word streaming output

All of these are v2+ features.

---

## Success Metric for V1

50 real Malayali users using it daily on WhatsApp.
That's it. Nothing else matters until you hit that.
