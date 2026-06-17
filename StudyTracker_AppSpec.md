# StudyFlow — Android App Specification
**Version:** 1.0  
**Target Platform:** Android 8.0+ (API level 26+)  
**Language:** Kotlin  
**Architecture:** MVVM + Clean Architecture  
**Prepared for:** AI Agent Build

---

## 1. App Overview

**StudyFlow** is a voice-first Android study session tracker that integrates with Google Assistant (App Actions) to let users start, annotate, and stop study sessions entirely by voice. Sessions are logged with rich metadata and the app provides analytics, streaks, daily goals, and smart reminders to help users build consistent study habits.

**Core User Loop:**
1. Say *"Hey Google, start studying [subject]"* → session starts automatically
2. Study without touching the phone
3. Say *"Hey Google, I'm done studying"* → session is logged
4. Review insights and progress in the app dashboard

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Repository pattern |
| Local DB | Room (SQLite) |
| Dependency Injection | Hilt |
| Voice Integration | Google App Actions (shortcuts.xml + Custom Intents) |
| Background Work | WorkManager |
| Charts/Analytics | Vico (Compose-native chart library) |
| Notifications | Android NotificationManager + AlarmManager |
| Preferences | DataStore (Proto) |
| Navigation | Jetpack Navigation Compose |

---

## 3. Google Assistant Integration (App Actions)

### 3.1 Custom Intents

Two custom intents are declared in `res/xml/shortcuts.xml`:

#### Intent 1: `com.studyflow.START_STUDY`

**Parameters extracted by Assistant:**

| Parameter Key | Type | Example Value |
|---|---|---|
| `subject` | `@schema.org/Text` | "Math", "Physics", "History" |
| `chapter` | `@schema.org/Text` | "Chapter 3", "Organic Chemistry" |
| `mode` | `@schema.org/Text` | "theory", "practice", "revision", "mock test" |

**Query patterns (in `strings.xml`):**
```
"start studying $subject$"
"start studying $subject$ $chapter$"
"I am going to study $subject$"
"I am going to study $subject$ $chapter$ $mode$"
"begin study session for $subject$"
"studying $subject$ $chapter$ now"
"open study session $subject$"
"I want to study $subject$"
```

#### Intent 2: `com.studyflow.STOP_STUDY`

**No parameters required.**

**Query patterns:**
```
"I am done studying"
"stop study session"
"finished studying"
"end study session"
"I have completed my study"
"study done"
"stop the timer"
```

#### Intent 3: `com.studyflow.TAKE_BREAK`

**Parameters:**
| Parameter Key | Type | Example |
|---|---|---|
| `duration` | `@schema.org/Text` | "10 minutes", "half an hour" |

**Query patterns:**
```
"take a break for $duration$"
"I am taking a break"
"pause study session"
"break time"
```

---

### 3.2 shortcuts.xml Structure

```xml
<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">

  <!-- START STUDY -->
  <capability android:name="com.studyflow.START_STUDY"
    android:queryPatterns="@array/start_study_queries">
    <intent
      android:action="android.intent.action.VIEW"
      android:targetClass="com.studyflow.ui.SessionActivity"
      android:targetPackage="com.studyflow">
      <parameter android:name="subject" android:key="subject" />
      <parameter android:name="chapter" android:key="chapter" />
      <parameter android:name="mode" android:key="mode" />
    </intent>
  </capability>

  <!-- STOP STUDY -->
  <capability android:name="com.studyflow.STOP_STUDY"
    android:queryPatterns="@array/stop_study_queries">
    <intent
      android:action="android.intent.action.VIEW"
      android:targetClass="com.studyflow.ui.SessionActivity"
      android:targetPackage="com.studyflow">
      <parameter android:name="action" android:key="action"
        android:value="STOP" />
    </intent>
  </capability>

  <!-- TAKE BREAK -->
  <capability android:name="com.studyflow.TAKE_BREAK"
    android:queryPatterns="@array/take_break_queries">
    <intent
      android:action="android.intent.action.VIEW"
      android:targetClass="com.studyflow.ui.SessionActivity"
      android:targetPackage="com.studyflow">
      <parameter android:name="duration" android:key="breakDuration" />
    </intent>
  </capability>

</shortcuts>
```

---

### 3.3 Intent Handling in SessionActivity

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val subject   = intent.getStringExtra("subject")
    val chapter   = intent.getStringExtra("chapter")
    val mode      = intent.getStringExtra("mode")
    val action    = intent.getStringExtra("action")
    val breakDur  = intent.getStringExtra("breakDuration")

    when {
        action == "STOP"   -> viewModel.stopSession()
        breakDur != null   -> viewModel.startBreak(parseDuration(breakDur))
        subject != null    -> viewModel.startSession(subject, chapter, mode)
    }
}
```

---

## 4. Data Layer

### 4.1 Room Database — `StudyFlowDatabase`

#### Table: `study_sessions`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `subject` | TEXT NOT NULL | e.g. "Mathematics" |
| `chapter` | TEXT NULLABLE | e.g. "Trigonometry" |
| `mode` | TEXT NULLABLE | theory / practice / revision / mock_test |
| `start_time` | INTEGER (epoch ms) | |
| `end_time` | INTEGER NULLABLE | null if session ongoing |
| `duration_seconds` | INTEGER | computed on stop |
| `break_duration_seconds` | INTEGER DEFAULT 0 | total break time within session |
| `notes` | TEXT NULLABLE | user-added post-session notes |
| `mood_rating` | INTEGER NULLABLE | 1–5 scale (post-session prompt) |
| `focus_score` | INTEGER NULLABLE | 1–5, user self-rated |
| `source` | TEXT | "voice" or "manual" |
| `created_at` | INTEGER (epoch ms) | |

#### Table: `subjects`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `name` | TEXT UNIQUE NOT NULL | |
| `color_hex` | TEXT | for UI color coding |
| `icon_name` | TEXT | material icon name |
| `daily_goal_minutes` | INTEGER DEFAULT 60 | |
| `is_active` | INTEGER DEFAULT 1 | soft delete |

#### Table: `daily_goals`

| Column | Type | Notes |
|---|---|---|
| `date` | TEXT PK | "YYYY-MM-DD" |
| `target_minutes` | INTEGER | |
| `achieved_minutes` | INTEGER | |
| `goal_met` | INTEGER | 0 or 1 |

#### Table: `streaks`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | always 1 row |
| `current_streak` | INTEGER | days |
| `longest_streak` | INTEGER | days |
| `last_study_date` | TEXT | "YYYY-MM-DD" |

---

### 4.2 DAOs

- `StudySessionDao` — insert, update (on stop), getByDate, getBySubject, getStreakData, getTotalBySubject, getWeeklyHours
- `SubjectDao` — insertOrUpdate, getAll, getActive, getById
- `DailyGoalDao` — upsert, getByDate, getLastNDays

---

## 5. Core Features

---

### 5.1 Session Timer Screen (`SessionActivity` / Composable)

**States:** IDLE → ACTIVE → ON_BREAK → STOPPED → RATING

**UI Elements:**
- Large circular timer (MM:SS or HH:MM:SS)
- Subject name + chapter chip displayed prominently
- Mode badge (Theory / Practice / Revision / Mock Test) with distinct color per mode
- "Take Break" button → pauses timer, starts break countdown
- "Stop Session" button → ends session, triggers post-session rating
- Live break countdown shown when ON_BREAK
- Subtle pulsing animation on timer while ACTIVE
- Persistent Notification (foreground service) with subject name, elapsed time, and quick-stop action — so user can stop without opening the app

**Foreground Service:**
- `StudyTimerService` runs as foreground service
- Updates notification every second
- Survives screen off and app backgrounding
- Sends broadcast on stop so SessionActivity can update UI

---

### 5.2 Post-Session Rating Sheet

Appears automatically after every session (voice-stopped or manual).

- **Mood:** 5 emoji buttons (😴 😕 😐 🙂 🔥)
- **Focus Score:** 5-star tap rating
- **Quick Note:** single-line text field (optional, "Add a note...")
- **Save & Close** button
- Dismissible — if dismissed, mood/focus remain null

---

### 5.3 Manual Session Logging

For sessions started without voice. Bottom sheet with:
- Subject picker (dropdown from saved subjects)
- Chapter field (free text, auto-suggests from past sessions for that subject)
- Mode selector (4 chips)
- Date + Start Time picker
- End Time picker
- Notes field

---

### 5.4 Dashboard (Home Screen)

**Today's Summary Card:**
- Total study time today (e.g. "3h 12m")
- Progress ring toward daily goal (e.g. "240/300 min")
- Subjects studied today as colored chips

**Active Session Banner:**
- Shown when a session is live
- Displays subject + elapsed time
- Tap to open Session Timer Screen

**Current Streak Widget:**
- 🔥 Streak count with "days" label
- Longest streak shown below
- Calendar heatmap for last 30 days (green = studied, shade intensity = hours)

**Today's Sessions List:**
- Each session card shows: subject, chapter, mode, duration, mood emoji
- Tap to expand and see notes + focus score
- Long-press to edit or delete

---

### 5.5 Analytics Screen

**Tabs:** Week | Month | All Time

**Charts (using Vico):**

1. **Daily Study Bar Chart** — hours per day for selected period
2. **Subject Breakdown Pie/Donut Chart** — % of time per subject
3. **Mode Breakdown Bar** — theory vs practice vs revision vs mock test hours
4. **Heatmap Calendar** — full year view, GitHub-style (intensity = hours)
5. **Best Study Hour** — histogram of sessions by hour of day (shows when user is most productive)
6. **Average Session Length** — trend line over time

**Stats Cards (numeric):**
- Total sessions this week / month
- Average session length
- Longest single session
- Most studied subject
- Best streak
- Productive time of day (e.g. "You study best at 9–11 AM")

---

### 5.6 Subjects Manager Screen

- List of all subjects with color dot and icon
- Tap to edit: name, color, icon, daily goal minutes
- Add new subject button
- Archive (soft delete) a subject
- Reorder subjects by drag handle

**Preset subject icons:** Math, Science, History, Language, Coding, Art, Economics, Law, Geography, General

---

### 5.7 Smart Reminders & Notifications

#### Reminder Types:

| Type | Description | Trigger |
|---|---|---|
| Daily Study Reminder | "Time to study! You haven't started yet today." | User-set time, only if no session that day |
| Goal Reminder | "You're 45 min away from your daily goal." | When halfway through day and < 50% of goal done |
| Streak at Risk | "⚠️ Study at least 15 min today to keep your streak!" | 2 hours before midnight if no session yet |
| Post-Break Nudge | "Your break was X min ago. Ready to get back?" | When break exceeds set max break duration |
| Weekly Summary | Every Sunday evening — "This week: X hours across Y subjects" | Fixed day/time |

#### Reminder Settings (in Settings screen):
- Enable/disable each reminder type individually
- Set daily reminder time
- Set max break duration before nudge (default: 15 min)
- Do Not Disturb window (e.g. "No reminders after 11 PM")

---

### 5.8 Focus Mode (Pomodoro Support)

Optional mode, enabled per session.

**Flow:**
- User sets: Work interval (default 25 min) + Break interval (default 5 min) + Number of rounds
- Long break after every 4 rounds (default 15 min)
- App shows which Pomodoro round is active (#1 of 4)
- Voice command `"Hey Google, start Pomodoro session for Math"` triggers focus mode with default intervals

**UI:**
- Round indicator dots (e.g. ● ● ○ ○ = round 2 of 4)
- Visual transition animation between work and break phases
- Notification shows phase: "Round 2 — Focus" or "Short Break"

---

### 5.9 Session History Screen

- Full paginated list of all sessions, newest first
- Filter bar: by subject, by mode, by date range
- Search bar: searches subject + chapter + notes
- Each row: date, subject chip, chapter, mode badge, duration, mood emoji
- Tap to open Session Detail Sheet
- Export button (see 5.11)

---

### 5.10 Session Detail Sheet

Full-screen bottom sheet showing:
- Subject, Chapter, Mode
- Start time → End time → Total duration
- Break time (if any) and net study time
- Mood + Focus score
- Notes
- Source (Voice / Manual)
- Edit button — lets user edit chapter, mode, notes, mood, focus score
- Delete button (with confirm dialog)

---

### 5.11 Data Export

- **Export as CSV** — all sessions table exported to Downloads folder
- **Export as PDF Report** — weekly/monthly summary with charts (rendered as HTML → PDF via WebView print API)
- Share button uses Android share sheet

---

### 5.12 Settings Screen

| Setting | Type | Default |
|---|---|---|
| Daily Study Goal | Number picker (min) | 120 min |
| Default Pomodoro Work Duration | Number picker | 25 min |
| Default Pomodoro Break Duration | Number picker | 5 min |
| Default Mode | Dropdown | Theory |
| Theme | Light / Dark / System | System |
| Reminder settings | (see 5.7) | — |
| First day of week | Sun / Mon | Mon |
| Export Data | Button | — |
| Clear All Data | Destructive button | — |
| App version / About | Info | — |

---

## 6. Navigation Structure

```
NavGraph
├── HomeScreen (Dashboard)
├── ActiveSessionScreen
├── HistoryScreen
│   └── SessionDetailSheet (modal)
├── AnalyticsScreen
├── SubjectsScreen
│   └── SubjectEditSheet (modal)
├── SettingsScreen
└── OnboardingFlow (first launch only)
    ├── WelcomeScreen
    ├── SetupSubjectsScreen
    └── SetDailyGoalScreen
```

**Bottom Navigation Bar** (persistent except during active session):
- Home | History | Analytics | Subjects | Settings

---

## 7. Onboarding Flow

Shown only on first launch (tracked via DataStore flag).

**Screen 1 — Welcome:**
- App name + tagline: *"Study smarter. Track everything."*
- Brief 3-point value prop

**Screen 2 — Add Your Subjects:**
- Pre-populated chips: Math, Physics, Chemistry, Biology, History, Geography, Polity, Economics, English, Current Affairs
- User taps to select (multi-select)
- Each selected subject gets auto-assigned a color

**Screen 3 — Set Your Daily Goal:**
- Slider: 30 min → 8 hours
- Shown in human format: "2 hours 30 minutes"
- Option to set per-subject goals (collapsible)

**Screen 4 — Enable Voice:**
- Illustration + text explaining App Actions
- Button: "Try it now" → opens Google Assistant with prompt overlay
- Skip option

---

## 8. UI Design Language

**Theme:** Dark-first with AMOLED black support. Light mode available.

**Color Palette:**

| Name | Hex (Dark) | Usage |
|---|---|---|
| Background | `#0A0A0F` | App background (AMOLED) |
| Surface | `#14141C` | Cards, sheets |
| Surface Variant | `#1E1E2A` | Input fields, chips |
| Primary | `#7C6AF7` | CTAs, active state, progress rings |
| Secondary | `#48C9B0` | Break timer, secondary actions |
| Error | `#FF6B6B` | Destructive, stop actions |
| On-Surface | `#E8E8F0` | Primary text |
| Muted | `#6B6B80` | Secondary text, placeholders |

**Mode Badge Colors:**

| Mode | Color |
|---|---|
| Theory | `#5B8AF0` (Blue) |
| Practice | `#F0955B` (Orange) |
| Revision | `#B05BF0` (Purple) |
| Mock Test | `#F05B5B` (Red) |

**Typography:**
- Display/Timer: `JetBrains Mono` (monospace, weight 600) — gives timer a precise, focused feel
- Headings: `Inter` (weight 700)
- Body: `Inter` (weight 400)
- Chips/Labels: `Inter` (weight 500, uppercase tracking)

**Shape:**
- Cards: `12dp` corner radius
- Chips: fully rounded (`50dp`)
- Buttons: `10dp`

**Motion:**
- Session timer starts with a scale-in + fade animation
- Break transition: soft color shift (purple → teal) over 400ms
- Goal ring fills with animated sweep

---

## 9. Foreground Service Specification

**Service Name:** `StudyTimerService`

**Notification Channel:** `study_session_channel` (importance: LOW — no sound)

**Notification Content:**
- Title: `📚 Studying: [Subject]` or `☕ Break — [Subject]`
- Text: Elapsed time (updates every second via `setUsesChronometer`)
- Actions:
  - `Take Break` (only shown when ACTIVE)
  - `Stop Session`
- On notification tap: opens `SessionActivity`

**The foreground service must:**
- Start when a session begins (voice or manual)
- Update elapsed time via `RemoteViews` or chronometer
- Handle break state (pause elapsed time, show break timer)
- Stop cleanly via bound service or broadcast receiver
- Acquire `WAKE_LOCK` to prevent timer drift on screen-off

---

## 10. Permissions Required

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

- `POST_NOTIFICATIONS`: request at runtime (Android 13+)
- `SCHEDULE_EXACT_ALARM`: required for streak-at-risk reminder precision
- Explain each permission with in-app rationale dialog before requesting

---

## 11. Edge Cases & Business Logic

| Scenario | Behavior |
|---|---|
| Voice starts session, app is not running | `SessionActivity` launches fresh, starts session |
| Voice stops session, no session is active | Show toast: "No active session found" |
| App killed mid-session | On relaunch, check DB for session with null end_time → offer to resume or discard |
| Break exceeds max break duration | Fire nudge notification |
| Two sessions started without stopping first | Auto-stop the current session and start the new one; show snackbar informing user |
| Session shorter than 1 minute | Flag as `too_short`, still log but exclude from analytics streaks calculation |
| Streak calculation | A day counts if total study time ≥ 15 minutes |
| Midnight crossover | Session that crosses midnight is attributed to the day it started |

---

## 12. File / Package Structure

```
com.studyflow
├── data
│   ├── db
│   │   ├── StudyFlowDatabase.kt
│   │   ├── dao/
│   │   │   ├── StudySessionDao.kt
│   │   │   ├── SubjectDao.kt
│   │   │   └── DailyGoalDao.kt
│   │   └── entity/
│   │       ├── StudySession.kt
│   │       ├── Subject.kt
│   │       └── DailyGoal.kt
│   ├── repository/
│   │   ├── SessionRepository.kt
│   │   └── SubjectRepository.kt
│   └── datastore/
│       └── UserPreferencesDataStore.kt
├── domain
│   ├── usecase/
│   │   ├── StartSessionUseCase.kt
│   │   ├── StopSessionUseCase.kt
│   │   ├── GetWeeklyStatsUseCase.kt
│   │   └── CalculateStreakUseCase.kt
│   └── model/
│       ├── Session.kt
│       └── StreakInfo.kt
├── service
│   └── StudyTimerService.kt
├── receiver
│   ├── BootReceiver.kt
│   └── AlarmReceiver.kt
├── ui
│   ├── MainActivity.kt
│   ├── SessionActivity.kt
│   ├── navigation/
│   │   └── AppNavGraph.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── session/
│   │   ├── SessionScreen.kt
│   │   └── SessionViewModel.kt
│   ├── history/
│   │   ├── HistoryScreen.kt
│   │   └── HistoryViewModel.kt
│   ├── analytics/
│   │   ├── AnalyticsScreen.kt
│   │   └── AnalyticsViewModel.kt
│   ├── subjects/
│   │   ├── SubjectsScreen.kt
│   │   └── SubjectsViewModel.kt
│   ├── settings/
│   │   └── SettingsScreen.kt
│   ├── onboarding/
│   │   └── OnboardingFlow.kt
│   └── theme/
│       ├── Color.kt
│       ├── Typography.kt
│       └── Theme.kt
├── di/
│   └── AppModule.kt
└── StudyFlowApp.kt
```

---

## 13. Build Configuration

```kotlin
// build.gradle (app)
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")

    // DataStore
    implementation("androidx.datastore:datastore:1.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Charts
    implementation("com.patrykandpatrick.vico:compose-m3:1.14.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Google Shortcuts (App Actions)
    implementation("androidx.core:core-google-shortcuts:1.1.0")
}
```

---

## 14. Play Store Requirements for App Actions

- App must be **published** (internal testing track is sufficient for development)
- `shortcuts.xml` must be referenced in `AndroidManifest.xml` under the activity:
```xml
<meta-data
    android:name="android.app.shortcuts"
    android:resource="@xml/shortcuts" />
```
- Use the **Google Assistant Plugin** in Android Studio for local testing
- Same Google account must be used in Android Studio, the device Google app, and Play Console
- App Actions testing only works on physical devices, not emulators

---

## 15. Future Feature Roadmap (v2)

| Feature | Description |
|---|---|
| Gemini Integration | End-of-session AI analysis: "You studied Trigonometry for 90 min. Here's a 5-question quiz to test retention." |
| Timetable Planner | Weekly schedule view, set which subjects to study on which days |
| Study Group / Share | Share weekly stats card as image to WhatsApp/Instagram |
| Offline-first Sync | Room → Firebase Firestore for multi-device history |
| Widget | Home screen widget showing today's progress ring and streak |
| Wear OS | Start/stop session from smartwatch |
| Subject-Specific Notes | Quick notes per subject, timestamped, linked to session |

---

*End of Specification — StudyFlow v1.0*
