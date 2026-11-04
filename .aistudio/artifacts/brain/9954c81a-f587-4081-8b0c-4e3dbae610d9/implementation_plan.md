# Implementation Plan - NetMaster Pro Local-First Architecture & Incremental Sync

## 1. Overview & Objectives
Transform NetMaster Pro into a high-performance Local-First application with:
- **Instant Local Startup**: Load data instantly from Room database without blocking on network requests.
- **Incremental Sync**: Fetch only new, modified, or deleted records from MikroTik RouterOS using ID tracking, timestamps, and revision hashes.
- **Background Synchronization**: Automatic background sync every 15 minutes with WorkManager / coroutine workers.
- **Materialized Aggregates**: Store pre-calculated sales, profits, and summaries in Room to eliminate redundant computation.
- **Database Indexing**: Add strategic indices on foreign keys, timestamps, and status columns for ultra-fast queries.
- **Owner Conflict Resolution**: Prompt the network owner when local edits conflict with router changes.

---

## 2. Proposed Changes

### A. Database & Entities (`Room`)
- **Add Indices**: Add `@Index` annotations on frequently queried columns in `AppDatabase.kt` (e.g., `saved_routers`, `local_cards`, `session_archive`, `system_event_logs`, `network_nodes`).
- **Materialized Aggregates Table**: Create a new entity `MaterializedSummaryEntity` to store cached financial metrics, active card counts, and traffic summaries with a `lastUpdated` timestamp.
- **Sync Tracking Metadata**: Add sync state fields (`isSynced`, `lastModifiedTimestamp`, `syncVersion`) to local entities.

### B. Repository & Incremental Sync (`MikrotikRepository.kt`)
- **Local-First Provider**: Expose Room flows immediately upon connection or startup.
- **Incremental Sync Engine**: Implement delta-fetching comparing local record IDs/timestamps with live MikroTik REST API responses.
- **Conflict Detector**: Compare local un-synced modifications against incoming router updates; trigger an interactive prompt dialog if a conflict is detected.

### C. Background Worker (`AppSyncWorker.kt`)
- **Periodic Sync**: Set up a WorkManager periodic worker (or robust coroutine scheduler) running every 15 minutes to perform incremental sync in the background without freezing the UI.
- **Connectivity Awareness**: Skip or retry gracefully when the network is disconnected, implementing exponential backoff.

### D. ViewModel & UI Enhancements (`MainViewModel.kt` & Screens)
- **Sync Status Bar**: Display clear last-sync timestamps and sync progress indicators in the UI.
- **Manual Sync Action**: Provide a manual pull-to-refresh or force-sync button.
- **Conflict Resolution Dialog**: Composable dialog allowing the network owner to choose between keeping local edits or applying router updates.

---

## 3. Verification Plan

### Automated Tests
- Run unit and Robolectric tests via `gradle :app:testDebugUnitTest`.

### Manual & Integration Verification
1. Verify instant startup showing cached local data without network delay.
2. Verify background sync running every 15 minutes updating delta records.
3. Test conflict resolution dialog when simultaneous modifications occur.
4. Verify performance improvement with large datasets.
