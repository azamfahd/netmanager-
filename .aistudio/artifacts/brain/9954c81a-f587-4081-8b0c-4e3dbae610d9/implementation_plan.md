# Implementation Plan - On-Demand Deep App Traffic Inspection

## Overview
Add an on-demand "فحص وتحليل التطبيقات الذكي" (Deep App Inspection) button on subscriber usage analytics cards (`UsageAnalyticsScreen`) that fetches and analyzes live connection and DNS cache records from the MikroTik router (`/ip/dns/cache` and `/ip/firewall/connection`), dynamically identifying real applications (YouTube, Instagram, Facebook, TikTok, PUBG, WhatsApp, Telegram, Netflix, Zoom, etc.) used by that specific subscriber without being restricted to a hardcoded list.

## Proposed Changes

### 1. Repository & API Client (`RouterOsApiClient.kt`, `MikrotikRepository.kt`)
- Add methods to query router DNS cache (`/ip/dns/cache/print`) and active firewall connections (`/ip/firewall/connection/print`) filtered by the subscriber's IP address.
- Implement domain-to-app pattern matching that resolves domains (e.g. `*.googlevideo.com` -> YouTube, `*.instagram.com` -> Instagram, `*.tiktokcdn.com` -> TikTok, `*.igdb.com` / `*.pubgmobile.com` -> PUBG, `*.whatsapp.net` -> WhatsApp) dynamically.

### 2. ViewModel (`MainViewModel.kt`)
- Add `inspectSubscriberApps(username: String, subscriberIp: String)` method to trigger live deep inspection of subscriber traffic.
- Expose `_inspectedAppUsage: StateFlow<List<AppUsageItem>>` to display real-time detected applications and byte counts.

### 3. UI Layer (`UsageAnalyticsScreen.kt`, `UserDetailDialog.kt`)
- Add a prominent "بدء الفحص العميقة للتطبيقات ⚡" (Start Deep App Inspection) button on usage breakdown cards.
- Display real-time progress, detected application breakdown, domain lists, and traffic volume per application.

## Verification Plan
- Compile applet with `compile_applet`.
- Verify the deep inspection button triggers live traffic analysis and displays discovered applications accurately.
