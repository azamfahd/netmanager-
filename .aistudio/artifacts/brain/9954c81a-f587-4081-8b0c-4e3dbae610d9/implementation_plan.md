# خطة التدقيق الشامل والهيكلة الهندسية المتقدمة لـ NetMaster Pro
## (Comprehensive Architectural Audit, Optimization & Repair Plan)

خطة هندسية شاملة ترتكز على معايير مهندسي شبكات MikroTik وكبار معماريي البرمجيات لجعل تطبيق **NetMaster Pro** عالي الموثوقية، فائق السرعة، قابلاً للتوسع، ويعمل بنسبة 100% مع راوترات MikroTik الحقيقية بدون أي بيانات وهمية أو محاكاة.

---

## مراجعة القرارات الحاسمة (User Review & Critical Decisions)

> [!IMPORTANT]
> **القرارات المعتمدة مع المستخدم:**
> 1. **آلية اكتشاف الراوترات الموسعة:** دمج اكتشاف الجيران (`/ip/neighbor`)، ومسح نطاق الشبكة الفرعية المصرح به (Authorized Subnet Scan للـ Ports: 8728, 8729, 8291, 80)، واستعلام واجهات Wireless و RoMON المتاحة دون تعديل إعدادات إنتاج الراوتر.
> 2. **المزامنة التزايدية الذكية:** جدولة مزامنة تزايدية دورية منتظمة مع حفظ محلي فوري دائم في Room Database وعدم تكرار الطلبات غير المتغيرة للحفاظ على سرعة واستجابة الواجهة.

---

## 1. الفحص والتدقيق الجذري للمشكلات الحالية (Discovery & Root Cause Analysis)

### المشكلة 1: اقتصار خريطة الشبكة (Network Map) على 13 عقدة
- **السبب الجذري:**
  1. وجود قائمة ثابتة من العقد التجريبية الأولية داخل `MikrotikRepository.kt` تنتهي عند 13-14 عقداً (الأسماء: `node_ap_main`, `node_ap_east`, `node_home_1..4`, `node_starlink`, `node_ap_west`, `node_ap_north`).
  2. في دالة `syncLiveSessionsAndNodes`، كان الكود يقوم بدمج الـ neighbors فقط بـ `node_neighbor_$idx` مع مطابقة صارمة بـ `distinctBy` تحذف أي جار لا يملك MAC ظاهر، كما أن استعلام `/ip/neighbor/print` في بعض إصدارات RouterOS v7 لا يعيد الأجهزة التي لا تدعم MNDP ما لم يتم فحص جدول الـ ARP وجدول الـ DHCP Leases وجداول RoMON.
  3. حصر العقد المكتشفة في Subnet محدد بدلاً من قراءة جميع الشبكات الفرعية الموجهة في الراوتر (`/ip/route` و `/ip/address`).

### المشكلة 2: تكرار العمليات وبطء الإضافة والتفعيل عند انقطاع الاتصال
- **السبب الجذري:** غياب قفل ذري (Atomic Mutex Lock) لكل مستخدم/كرت يمنع تكرار إرسال الأمر أثناء انتظار الرد، واقتصار فحص الاسترداد على حالة واحدة بدلاً من استرداد شامل.

### المشكلة 3: دقة حدود الباقات (Data Limits) ومزامنة قاعدة البيانات المحلية
- **السبب الجذري:** عدم قراءة جداول القيود المرتبطة `/user-manager/limitation` في بعض السيناريوهات، مما يسبب ظهور "غير محدد" أو "مفتوح" على باقات محددة بحجم فعلي في السيرفر.

---

## 2. الهيكلية المعمارية الجديدة (System Architecture)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           NetMaster Pro Compose UI                           │
│   (NetworkNodesScreen / Dynamic Topology Graph / CardsScreen / Dashboard)    │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ StateFlow & Coroutines
┌──────────────────────────────────────▼───────────────────────────────────────┐
│                           MainViewModel Orchestrator                         │
│   - Periodic Incremental Sync Timer (every 60s)                              │
│   - Immediate Local-First DB Loading                                         │
│   - Non-Blocking Background Operations & Safe Progress Emission              │
└──────────────────┬───────────────────────────────────────┬───────────────────┘
                   │                                       │
┌──────────────────▼─────────────────────┐ ┌───────────────▼───────────────────┐
│     Centralized MikroTik Engine        │ │    Local-First Room Database      │
│     (RouterOsApiClient + REST Api)     │ │     (AppDao + High-Perf Indices)  │
│  - Multi-Method Network Discovery:     │ │  - Instant Startup Hydration      │
│    • /ip/neighbor (MNDP/CDP/LLDP)      │ │  - Incremental Delta Sync         │
│    • /ip/arp + /ip/dhcp-server/lease   │ │  - Safe Offline Persistence       │
│    • Authorized Subnet Fast Probing    │ │  - Materialized Summary Views     │
│    • RoMON Discovery Ports Check       │ │  - Multi-Tenant Isolated Sync     │
│  - Dynamic Socket Timeouts (25s ops)   │ └───────────────────────────────────┘
│  - RouterOS v6 & v7 Auto-Compatibility │
│  - Idempotent 5-Phase Card Execution   │
│  - Linked Limitation Data Limit Parser │
└──────────────────┬─────────────────────┘
                   │ Port 8728 / 8729 / REST
┌──────────────────▼───────────────────────────────────────────────────────────┐
│                       Real MikroTik RouterOS Fleet                           │
│  Gateway Router ───► Discovered Sub-APs ───► RoMON Neighbors ──► Hotspot/UM  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. مراحل التنفيذ التفصيلية (Phased Implementation Strategy)

### المرحلة 1: ترقية محرك اكتشاف الشبكة (Network Discovery & Full Map)
1. **توسيع مصادر الاكتشاف في `MikrotikRepository.kt` و `RouterOsApiClient.kt`:**
   - قراءة `/ip/neighbor/print` بالكامل (MNDP, LLDP, CDP).
   - قراءة جميع الأجهزة الحية النشطة في الشبكة من `/ip/arp/print` و `/ip/dhcp-server/lease/print` و `/interface/wireless/registration-table/print`.
   - قراءة عقد RoMON إن كانت مفعلة عبر استعلام آمن `/tool/romon/neighbor/print` أو `/romon/neighbor/print` بدون تفعيلها إجبارياً أو تعديل إعدادات الراوتر.
   - مسح العناوين الفرعية النشطة في الراوتر (`/ip/address/print`) واكتشاف الراوترات ونقاط التوزيع المتاحة.
2. **إزالة أي قيود عددية أو قوائم تجريبية تحصر الشبكة بـ 13 نقطة:**
   - السماح بظهور جميع الراوترات والنقاط المكتشفة فعلياً وتصنيفها بدقة:
     - **Main Gateway (الراوتر الرئيسي)**
     - **Distribution / AP (أكسس بوينت / برج)**
     - **Neighbor Router (راوتر متصل / جار)**
     - **Connected Node / Client (نقطة توزيع فرعية)**
3. **الربط مع قاعدة البيانات المحلية (`network_nodes`):**
   - حفظ جميع الأجهزة المكتشفة في SQLite Room محلياً، بحيث حتى وإن أُغلق راوتر أو تعطل اتصاله يظهر كـ "مغلق / Offline" بلون أحمر واضح مع الاحتفاظ ببياناته وإحصائياته السابقة.

### المرحلة 2: الاتصال الموثوق ومنع تكرار العمليات (MikroTik Connectivity & Idempotency)
1. الحفاظ على مهل السوكيت الآمنة (25 ثانية لقراءة User Manager و 8 ثوانٍ للاتصال) مع `Keep-Alive`.
2. حماية عمليات الإضافة والتعديل بقفل على مستوى اسم الكرت لمنع إرسال طلبين في نفس الوقت لنفس الكرت.
3. التمييز التام بين RouterOS 6 و RouterOS 7 في الأوامر والمسارات.

### المرحلة 3: التخزين المحلي والمزامنة التزايدية الدورية (Local Database & Sync)
1. **التحميل الفوري عند التشغيل (Instant Startup Hydration):**
   - تحميل الكروت والعقد والإعدادات المحفوظة محلياً في الـ Room فوراً عند فتح التطبيق دون انتظار استجابة الراوتر.
2. **المزامنة التزايدية الدورية (Incremental Delta Sync):**
   - تشغيل موقت ذكي كل دقيقة يقوم بتحديث المتغيرات فقط ومقارنة الـ Timestamps، دون مسح أو استبدال البيانات القديمة عند حدوث انقطاع في الشبكة.
3. **عزل المستأجرين (Tenant Isolation):**
   - تأكيد ربط عمليات السحابة بمعرف المستخدم `Firebase.auth.currentUser.uid` فقط.

### المرحلة 4: دقة قراءة وتثبيت حدود باقات User Manager
1. تثبيت القراءة المباشرة من `/user-manager/limitation` و `/user-manager/profile-limitation` في v7 و v6.
2. منع ظهور "مفتوح" أو "غير محدد" على الباقات محددة الحجم.

---

## 4. التحقق والاختبار العملي (Verification Plan)
1. اختبار اكتشاف الراوترات ومقارنة عدد النقاط قبل وبعد في شاشة Network Map.
2. اختبار إضافة وتفعيل الكروت المتتالية والتأكد من سرعة الاستجابة واستقرار السوكيت.
3. اختبار عمل التطبيق في حالة انقطاع الاتصال (Offline Mode) والتأكد من بقاء كل البيانات المحلية.
4. إجراء فحص التجميع الكامل `compile_applet`.
