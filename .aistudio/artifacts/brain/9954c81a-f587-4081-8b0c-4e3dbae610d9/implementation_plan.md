# Implementation Plan - 100% Data Retention & Hybrid Cloud-Local Sync System

This plan outlines the transition of the application to a high-volume, professional system with zero data loss, 100% record retention, and real-time cloud synchronization.

## User Requirements Addressed
- **100% Data Retention**: No record (session, log, payment, card) is ever automatically or manually purged from the system.
- **Hybrid Storage Architecture**: Every local record in Room is mirrored to Firebase Firestore for permanent cloud backup and multi-device access.
- **MicroTik Parity**: Generated cards must match router configurations perfectly and be ready for immediate sale/use.
- **Professional Stability**: Optimized indexing for high-volume data handling without performance degradation.

---

## Completed & Ongoing Changes

### 1. Permanent Retention Policy
- **Removal of Purge Logic**: All automatic cleanup routines, row-count limits, and "Clear/Purge" UI buttons have been removed from the application.
- **Schema Hardening**: Entities like `SessionArchiveEntity`, `SystemEventLogEntity`, and `UserLoginHistoryEntity` are now persistent stores that only grow.

### 2. Hybrid Cloud Synchronization (Firestore)
- **Real-Time Mirroring**: `MainViewModel` now integrates `syncToCloud` across all critical operations:
  - System Logs & Router Event Logs.
  - Voucher Generation (Batch History & POS Assignments).
  - POS Operations (Agent management, Payments, Card sales).
  - Network Node Heartbeats and Performance Telemetry.
- **Zero-Trust Security**: `firestore.rules` and `firebase-blueprint.json` configured to gate all writes behind Google Sign-In UID checks and schema validation.

### 3. Authentication & Security
- **Google Sign-In**: Mandatory authentication via Jetpack Credential Manager to secure cloud data access.
- **Auth Gate**: A dedicated `AuthScreen` protects the main application dashboard, ensuring only authorized owners access sensitive network and financial data.

### 4. Database Performance (Room)
- **Indexing**: Added composite and single-column indexes in `AppDao` and Entity classes for `timestamp`, `username`, and `agentId` to maintain speed at high row counts.
- **Transactions**: Bulk operations (like generating 1000+ cards) use SQLite transactions for atomic, fast execution.

---

## Verification Plan

### Automated Tests
- **Firestore Rules**: Verified via JavaScript and Robolectric tests to ensure only the authenticated owner can write/read their data.
- **Unit Tests**: Verified data persistence and relationship integrity in Room.

### Manual Verification
- **Full Retention Check**: Verified that no "Purge" or "Clear" options exist in the System Logs or Network Reports screens.
- **Cloud Sync Verification**: Confirmed that local actions (generating cards, recording payments) trigger corresponding Firestore writes.
- **Professional Flow**: Verified that card generation is ready for sale and mirrors Mikrotik logic exactly.
