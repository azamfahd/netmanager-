# Implementation Plan - Universal Router Event & Log Synchronization

This plan outlines the implementation of a comprehensive, 5-minute interval synchronization engine designed to capture *all* changes and events from the MikroTik router, regardless of the origin (NetManager app, WinBox, WebFig, or CLI).

## User Requirements Addressed
- Capture *all* events/changes from the router.
- Track changes made by external sources (WinBox, WebFig, etc.).
- Ensure professional and complete data integrity.
- Balanced polling (every 5 minutes).

---

## Proposed Changes

### 1. Robust MikroTik Log Fetcher (`RouterEventSyncManager`)
- **Event-Driven Polling Engine**:
  - Implement a background service/scheduler that triggers every 5 minutes.
  - Efficiently fetch the `system log print` or specific monitoring parameters from the router via API.
- **Full Event Normalization & Parsing**:
  - Create a robust parser for MikroTik log messages (`buffer`, `message`, `topics`).
  - Map every incoming raw log entry to the `SystemEventLogEntity` database schema.
  - Ensure full coverage of all log topics: `system`, `firewall`, `interface`, `dhcp`, `hotspot`, `accounts`, etc.

### 2. Enhanced Data Integrity & Deduplication
- **Log Sequence Management**:
  - Implement a fingerprinting mechanism (hash of `timestamp` + `message`) to prevent duplicate record insertion for the same router event in the local database.

### 3. Dashboard Integration
- **Monitoring UI Updates**:
  - Update `SystemEventLogScreen` to display a "Last Sync Time" indicator.
  - Automatically refresh the logs list upon successful sync completion.

---

## Verification Plan

### Automated Tests
- Test the log parsing engine with a suite of real MikroTik log samples (from all topics: firewall, system, dhcp, etc.).
- Verify the deduplication logic ensures unique records.

### Manual Verification
- Perform manual changes via WinBox/WebFig and verify they appear in the app's log screen within 5 minutes.
- Confirm system stability (no impact on router CPU).
