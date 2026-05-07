# Nexa — The Personal Daily Super App

Nexa is a modern, premium Android application designed to serve as a comprehensive daily helper. It integrates personal finance tracking, group expense splitting, task management, notes, reminders, and visual analytics into a unified, high-performance super app.

---

## 🌟 Key Modules & Features

### 1. Today's Dashboard (Home)
- **Personalized Greeting**: Dynamic, hour-based greeting card (Morning/Afternoon/Evening/Night) with user profile avatar.
- **Financial Status at a Glance**: Clear display of cash balance, bank balance, and net total balance.
- **Productivity Previews**: Shows next pending task, next upcoming reminder (with trigger time), and active group count.
- **Quick Action Bar**: Fast navigation to add cashbook entries, schedule tasks, set reminders, or view reports.

### 2. Double-Column Cashbook
- **Double Entry Tracking**: Separated monitoring for **Cash** and **Bank** mediums.
- **Flexible Transaction Categorization**: Log entries under preset categories (Sales, Rent, Salary, Office, Personal, Food, Transport, Shopping, Bills) with search particulars and additional notes.
- **Quick Filtering**: Filter transaction history by medium (All/Cash/Bank) or timeline (Today/This Week/This Month).
- **Long-Press Deletion**: Delete incorrect entries with a single long-press, instantly recalculating running balances.

### 3. Fair Share (Group Expense Splitting)
- **Collaborative Group Creation**: Create groups with custom invite codes, or join existing groups using a 6-character code.
- **Smart Debt Simplification**: Computes complex debts between multiple members and uses a greedy settlement algorithm to suggest the minimum number of payments.
- **Payment Verification**: Mark settlement suggestions as paid, instantly updating group balances in real-time.
- **PDF Report Export**: Generate and share clean, professional PDF reports listing all transactions, shared expense breakdowns, and outstanding debt summaries.

### 4. Smart Tasks
- **Priority-Driven Sort**: Sorts pending tasks automatically by priority (**High**, **Medium**, **Low**).
- **Interactive Checklists**: Complete tasks directly from the list with a striking line-through styling.
- **Timeframe Filters**: Access views for Today, This Week, Completed, or All tasks.

### 5. Staggered Notes Grid
- **Modern Grid Layout**: Uses a staggered grid layout for easy readability of note cards.
- **Rich Previews**: Shows the note title, a preview snippet of the body, and the last modified date.
- **Auto-saved Updates**: Easily create, edit, or delete notes with automated timestamp tracking.

### 6. Reminders & Background Work Manager
- **Exact Notifications**: Utilizes `AlarmManager` to trigger notifications at the exact minute set by the user, with custom snooze (15-minute delay) and complete actions.
- **SDK 36 Compatibility**: Safe fallback mechanism to inexact alarms on Android 12+ (SDK 31+) if exact scheduling is restricted by the OS, preventing crashes.
- **Daily morning Digest (8:00 AM)**: Displays a notification overview of the day's pending tasks and weekly expenses.
- **Night Summary (9:30 PM)**: Highlights completed tasks, new notes, and spending trends from the day.

### 7. Reports & Analytics
- **Visual Trends**: Custom `SimpleBarChartView` drawn directly on `Canvas` using vector mathematics (no external chart libraries required), rendering daily spending patterns over a 7-day or 30-day window.
- **Completion Gauges**: Circular progress gauges showing task completion rates.
- **Category Expenditures**: Linear progress indicators displaying category spending breakdowns as percentages of the total.

### 8. Settings & Profile
- **Global Dark Mode**: Persistent, launch-safe dark mode toggle that applies immediately at app startup.
- **Notification Controls**: Toggle daily morning/night digests on or off.

---

## 🚀 Recent Updates

- **Unified Shared Ledger Architecture**: Merged Groups and Ledgers to simplify operations.
- **Improved Build Compatibility**: Resolved issues with deprecated classes and Gradle upgrades.
- **Robust SDK 36 Support**: Enhanced exact alarm management on newer Android releases.
- **Invite Code Generation**: Automatic fallback generation for missing ledger invite codes.

---

## 🛠️ Technology Stack

- **Platform**: Native Android (Java 11)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 16)
- **Database**: Real-time Firebase Firestore
- **Authentication**: Firebase Authentication (Email/Password)
- **Background Operations**: WorkManager (Daily/Night Digests) + AlarmManager (Reminders)
- **UI Architecture**: Material3 theme integration, ConstraintLayout, StaggeredGridLayout, ViewPager2, and custom Canvas graphics.
- **Core Dependencies**:
  - `androidx.appcompat` & `com.google.android.material:material`
  - `com.google.firebase:firebase-auth` & `com.google.firebase:firebase-firestore`
  - `androidx.work:work-runtime` (Background Work)
  - `com.google.guava:guava` (ListenableFuture implementation for WorkManager tasks)

---

## 📂 Codebase Architecture

```
com.example.frienddebt/
├── data/
│   └── AppData.java                 # In-memory data repository
├── dsa/
│   ├── CashbookCalculator.java      # Financial balance computations
│   ├── DebtCalculator.java          # Greedy debt-simplification algorithm
│   └── ReportCalculator.java        # Statistical analysis calculations
├── model/
│   ├── CashbookEntry.java           # Cashbook data model
│   ├── Group.java                   # Split-expense group model
│   ├── Note.java                    # Personal notes model
│   ├── Reminder.java                # Alarm reminder model
│   ├── Task.java                    # To-do checklist model
│   └── User.java                    # Group member model
├── notification/
│   ├── BootReceiver.java            # Alarm scheduler on device boot
│   ├── DailySummaryWorker.java      # Morning digest task worker
│   ├── NightSummaryWorker.java      # Night digest task worker
│   ├── NotificationHelper.java      # Notification channel builder
│   ├── ReminderReceiver.java        # Broadcast receiver for reminder events
│   └── ReminderScheduler.java       # AlarmManager helper
└── ui/
    ├── fragment/
    │   ├── CashbookFragment.java    # Personal cashbook ledger UI
    │   ├── FairShareFragment.java   # Expense splitting groups tab
    │   ├── HomeFragment.java        # Main Today dashboard
    │   ├── NotesFragment.java       # Grid view notes dashboard
    │   └── ProfileFragment.java     # User settings and dark mode toggle
    ├── view/
    │   └── SimpleBarChartView.java  # Custom Canvas-rendered bar chart
    └── ...                          # Activities for auth, add/edit, and details
```

---

## ⚙️ Setup & Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/monikdiwani/Nexa.git
   ```
2. **Open in Android Studio**:
   Import the folder directly into the latest Android Studio build.
3. **Firebase Configuration**:
   Register the application package `com.fairshare.app` in your Firebase Console and download `google-services.json`. Place this file into the `app/` directory of the project.
4. **Build and Run**:
   - **On Windows**:
     ```bash
     .\gradlew.bat assembleDebug
     ```
   - **On Linux/macOS**:
     ```bash
     ./gradlew assembleDebug
     ```
