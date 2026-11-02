# Implementation Plan - Enhanced Finance & Monthly Profits Section

## Goal
Improve the Finance and Profits section (`FinanceAnalyticsScreen` and related ViewModels/Models) to correctly display **all months and years** from the router, user manager, and local cache without any hardcoded limitations, featuring expandable monthly details with sold vs. unsold cards, revenue breakdown, and multi-year support.

## Proposed Changes

### 1. Data Models & Analytics Extension (`app/src/main/java/com/example/data/model/RouterModels.kt` or new model file)
- Ensure `MonthlySalesSummary` and revenue calculations properly parse and aggregate cards across **all years and months** (extracting year and month from card creation timestamps, comments, or IDs).
- Add support for categorizing cards per month into:
  - Sold cards (used / active / logged in)
  - Unsold cards (unused / valid)
  - Total revenue (sold vs potential revenue)

### 2. ViewModel Logic (`MainViewModel.kt` & `MikrotikRepository.kt`)
- Update `monthlySalesList` and analytics aggregation in `MainViewModel` to dynamically group all loaded hotspot cards, user manager cards, and local database cards by Year and Month (`YYYY-MM`).
- Sort months in descending or chronological order across all detected years.
- Compute accurate per-month metrics: total cards, sold count, unsold count, total revenue, average card price, and profit.

### 3. UI Enhancements in `FinanceAnalyticsScreen.kt`
- Upgrade the "المبيعات الشهرية والأشهر" (Monthly Sales & Records) tab/section:
  - Display a comprehensive chronological list of **all months across all years** (e.g. November 2026, October 2026, ..., 2025, etc.).
  - Add **Year Selector / Filter** or hierarchical year-month view.
  - Make each month card **expandable** to reveal detailed statistics:
    - Sold vs. Unsold cards count & progress bar.
    - Revenue generated from sold cards vs. potential revenue from unsold cards.
    - Breakdown of card profiles/packages sold in that specific month.
    - Quick actions (e.g., export monthly report, view cards of that month).

## Verification Plan
- Compile applet with `compile_applet`.
- Verify no compilation errors and check that monthly analytics display correctly across multiple years.
