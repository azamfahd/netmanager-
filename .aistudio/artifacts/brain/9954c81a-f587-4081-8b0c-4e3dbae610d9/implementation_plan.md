# Real-Router Connection Integrity & Data Audit

This plan outlines the steps required to ensure the application operates exclusively on real router data when connected to a real MikroTik device, completely eliminating any demo or placeholder data, and providing a professional empty state experience when the router is connected but lacks data.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by user input and represent the core requirements for this audit and enhancement task:

- **Confirmed Decision 1**: **Comprehensive Audit**: All app sections (Active Users, Cards, Profiles, Stats) will be audited and modified to strictly fetch and display data from the connected router.
- **Confirmed Decision 2**: **Clear Separation**: Demo mode (for testing) will be explicitly separated from the real connection flow, ensuring no data cross-contamination.
- **Confirmed Decision 3**: **Empty State UI**: When a real router is connected but contains no data (e.g., no active users, no cards), the app will display a dedicated, professional "Empty State" screen with clear instructions and a call-to-action to add data, rather than showing placeholder or hidden data.

### 1. Overview & Core Concept

- **What It Does**: This update removes all hardcoded or seeded "demo" data paths that could accidentally inject placeholder information during real-router sessions.
- **Key Value**: Professional and trustworthy tool experience. Users will be confident that the data they see on their screen directly reflects their live network infrastructure.

### 2. User Experience & Visual Design

- **Empty State UX**:
    - A centralized `EmptyStateComponent` will be used across `ActiveUsersScreen`, `CardsScreen`, and `ProfilesScreen`.
    - Design: Minimalist, clean, utilizing Material 3 empty-state illustration patterns (icon + title + descriptive text + primary action button).
- **Demo Indicator**:
    - A clear, unobtrusive banner or chip in the dashboard header when in "Demo Mode" to remind users of the current context.

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Strict State Reset on Connect**:
    - *Chosen Approach*: Explicitly nullify all router data stores (`cards`, `profiles`, `activeUsers`, `bandwidthHistory`) at the exact moment a connection to a real router is initiated.
    - *Why*: Guaranteed removal of stale data and prevention of mixing demo telemetry with real router telemetry.
- **Decision 2: Repository-Level Enforcement**:
    - *Chosen Approach*: Move data-initialization logic out of generic initialization blocks and into specific `demoMode` flags or triggered flows.
    - *Why*: Ensures that the repository remains lean and focused on real API interactions.

### 4. Technical Architecture & Data Strategy

```text
┌───────────────────────────┐
│   MainViewModel (State)   │
└─────────────┬─────────────┘
              │
    ┌─────────┴─────────┐
    │ MikrotikRepository│
    └─────────┴─────────┘
              │
    ┌─────────┴─────────┐
    │   RouterOS API    │
    └───────────────────┘
```

- **Data Flow**:
    1.  `verifyAndConnect(config)` triggers a mandatory `clearLocalRouterData()` call.
    2.  If `isDemoMode == false`, the repository initiates real-time fetching without calling any `populateInitialSampleData()` style functions.
    3.  `MainViewModel` observers switch to empty list as the initial state during the `Verifying...` phase.
    4.  UI components (`ProfilesScreen`, etc.) observe the state and conditionally render `EmptyStateComponent` if the list size is zero, once the connection state is `Connected`.
- **State Cleanup**: All `StateFlow` holders for router data (cards, profiles, logs) in `MikrotikRepository` will have a `reset()` method invoked on `verifyAndConnect`.
