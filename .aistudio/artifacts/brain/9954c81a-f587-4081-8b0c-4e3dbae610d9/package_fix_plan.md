# Implementation Plan - Router Package Synchronization & Data Integrity Fix

This plan addresses synchronization delays, incorrect package parsing, and data discrepancies for MikroTik Hotspot Profiles.

## User Requirements Addressed
- Fix incorrect package details (price, validity, internet quota/data-limit).
- Ensure 100% accurate synchronization with the router server.
- Optimize network fetching performance to handle large datasets efficiently.

---

## Proposed Changes

### 1. Robust Package Attribute Parsing (`MikrotikRepository.kt`)
- **Improve `extractPriceFromText`**: Upgrade this utility to be more tolerant of variations in router comment formats, ensuring prices are extracted correctly from diverse naming conventions.
- **Implement Quota Extraction**: Extend the parser to specifically look for and map `limit-bytes` (quota) from the router's profile configuration to the `HotspotProfile.dataLimit` attribute, which is currently empty.
- **Validity Mapping**: Map `session-timeout` and `keepalive-timeout` more accurately to the `validity` attribute.

### 2. Synchronization Optimization
- **Efficient REST Queries**: Modify the REST requests to specifically request only fields needed for profiles (`name`, `rate-limit`, `comment`, `shared-users`, `session-timeout`, `limit-bytes`), reducing the JSON payload size and improving processing speed for high-volume environments.
- **Asynchronous Data Refresh**: Ensure profile fetching happens asynchronously without blocking other router data refreshes, improving the overall perception of synchronization speed.

### 3. Verification & Validation
- **Data Validation Logic**: Add a post-parsing validation step that checks if the extracted `price` or `quota` looks suspicious (e.g., if price = 0 for a non-free package) and log an error for manual review.

---

## Verification Plan

### Automated Tests
- Create unit tests for `extractPriceFromText` with a variety of "edge-case" comment strings (e.g., "Abu 200", "500 ر.ي", "1000MB Quota").
- Run tests in a debug environment, comparing the router's raw JSON response with the final parsed `HotspotProfile` objects.

### Manual Verification
- Verify the "Abu 200" and other problematic packages appear with correct Price, Validity, and Quota in the app.
- Check "Sync Speed" during the fetching process to ensure a visible improvement.
