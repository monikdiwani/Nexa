git add app/src/main/java/com/example/frienddebt/NexaApp.java
git add app/src/main/AndroidManifest.xml
git commit -m "feat: Add NexaApp for offline Firestore persistence"
git push

git add app/src/main/java/com/example/frienddebt/model/LedgerBook.java
git add app/src/main/java/com/example/frienddebt/model/CashbookEntry.java
git commit -m "feat: Introduce multi-book architecture models and Udhaar tracking"
git push

git add app/src/main/java/com/example/frienddebt/ui/CreateLedgerBookActivity.java
git add app/src/main/java/com/example/frienddebt/ui/LedgerBookDetailActivity.java
git add app/src/main/java/com/example/frienddebt/ui/fragment/CashbookFragment.java
git add app/src/main/res/layout/activity_create_ledger_book.xml
git add app/src/main/res/layout/activity_ledger_book_detail.xml
git add app/src/main/res/layout/fragment_cashbook.xml
git add app/src/main/res/layout/item_ledger_book.xml
git add app/src/main/res/drawable/ic_book.xml
git commit -m "feat: Implement UI and logic for Multi-Book management"
git push

git add app/src/main/java/com/example/frienddebt/ui/AddCashbookEntryActivity.java
git add app/src/main/res/layout/activity_add_cashbook_entry.xml
git commit -m "feat: Update entry creation with Udhaar tracking and specific book binding"
git push

git add app/src/main/java/com/example/frienddebt/ui/CashCounterActivity.java
git add app/src/main/res/layout/activity_cash_counter.xml
git add app/src/main/res/layout/item_denomination_row.xml
git commit -m "feat: Add physical Cash Counter utility"
git push

git add app/src/main/java/com/example/frienddebt/utils/ReportGenerator.java
git commit -m "feat: Add PDF Report Generator for ledgers"
git push

git add app/src/main/java/com/example/frienddebt/utils/WhatsAppReminderUtil.java
git commit -m "feat: Add WhatsApp Reminder utility for Udhaar"
git push
