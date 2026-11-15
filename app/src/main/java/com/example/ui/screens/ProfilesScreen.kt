package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import com.example.data.model.PREDEFINED_PACKAGE_TEMPLATES
import com.example.data.model.PackageTemplate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.ui.components.EmptyStateComponent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.ui.theme.CyberGreenBright
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.NetworkCyan
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ProfileSystemFilter
import androidx.compose.material3.FilterChip

data class ProfileDisplayStats(
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val unusedCount: Int = 0,
    val expiredCount: Int = 0,
    val soldCount: Int = 0,
    val salePrice: Double = 500.0,
    val costPrice: Double = 200.0,
    val profitPerCard: Double = 300.0,
    val profileRevenue: Double = 0.0,
    val profileNetProfit: Double = 0.0
)

@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val filteredProfiles by viewModel.filteredProfiles.collectAsStateWithLifecycle()
    val currentProfileFilter by viewModel.profileSystemFilter.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val packagePricings by viewModel.packagePricings.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var initialTemplateForAdd by remember { mutableStateOf<PackageTemplate?>(null) }
    var profileToViewDetails by remember { mutableStateOf<HotspotProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<HotspotProfile?>(null) }

    val pricingMap = remember(packagePricings) {
        packagePricings.associateBy { it.profileName.trim().lowercase() }
    }

    val cardsByProfile = remember(cards) {
        cards.groupBy { it.profile.trim().lowercase() }
    }

    val profileStatsMap = remember(profiles, cards, pricingMap) {
        profiles.associate { profile ->
            val pCards = cardsByProfile[profile.name.trim().lowercase()].orEmpty()
            val total = pCards.size
            val active = pCards.count { it.isActive }
            val unused = pCards.count { it.isUnused }
            val expired = pCards.count { it.isExpired }
            val sold = pCards.count { it.isActive || it.isExpired || (it.uptime.isNotBlank() && it.uptime != "0s") || it.bytesIn > 0L || it.bytesOut > 0L }

            val pricing = pricingMap[profile.name.trim().lowercase()]
            val salePrice = pricing?.salePrice ?: profile.priceNumber.takeIf { it > 0 } ?: profile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
            val costPrice = pricing?.costPrice ?: profile.costPrice.takeIf { it > 0 } ?: (salePrice * 0.40)
            val profitPerCard = (salePrice - costPrice).coerceAtLeast(0.0)
            val rev = sold * salePrice
            val net = sold * profitPerCard

            profile.name to ProfileDisplayStats(
                totalCount = total,
                activeCount = active,
                unusedCount = unused,
                expiredCount = expired,
                soldCount = sold,
                salePrice = salePrice,
                costPrice = costPrice,
                profitPerCard = profitPerCard,
                profileRevenue = rev,
                profileNetProfit = net
            )
        }
    }

    // Calculate overall statistics efficiently from memoized maps
    val totalProfiles = profiles.size
    val totalCardsCount = cards.size
    val activeCardsCount = remember(cards) { cards.count { it.isActive } }
    val unusedCardsCount = remember(cards) { cards.count { it.isUnused } }
    val expiredCardsCount = remember(cards) { cards.count { it.isExpired } }
    val soldCardsCount = remember(profileStatsMap) { profileStatsMap.values.sumOf { it.soldCount } }
    val soldRevenue = remember(profileStatsMap) { profileStatsMap.values.sumOf { it.profileRevenue } }
    val totalInventoryValue = remember(profileStatsMap) { profileStatsMap.values.sumOf { it.totalCount * it.salePrice } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "باقات السرعات والأسعار (Hotspot Profiles)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "إدارة الباقات، التسعيرات، وتتبع تفصيلي لأرباح وحالة كل باقة في السيرفر",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshData() },
                    modifier = Modifier.testTag("btn_refresh_profiles")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث من السيرفر",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overall Statistics Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 إحصائيات باقات سيرفر الميكروتك",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "مبيعات الكروت: ${soldRevenue.toLong()} ر.ي",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBadgeItem("الباقات بالسيرفر", "$totalProfiles باقة", CyberGreenPrimary)
                        StatBadgeItem("كروت مباعة", "$soldCardsCount كرت", NetworkCyan)
                        StatBadgeItem("نشطة الآن", "$activeCardsCount", MaterialTheme.colorScheme.tertiary)
                        StatBadgeItem("مخزون متبقي", "$unusedCardsCount", MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PackageTemplatesBanner(
                        templates = PREDEFINED_PACKAGE_TEMPLATES,
                        onSelectTemplate = { tpl ->
                            initialTemplateForAdd = tpl
                            showAddDialog = true
                        }
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الباقات الحالية في السيرفر (${filteredProfiles.size} من ${profiles.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Filter Chips by subsystem
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val countHotspot = profiles.count { it.isHotspot }
                            val countUserManager = profiles.count { it.isUserManager }

                            FilterChip(
                                selected = currentProfileFilter == ProfileSystemFilter.ALL,
                                onClick = { viewModel.updateProfileSystemFilter(ProfileSystemFilter.ALL) },
                                label = { Text("الكل (${profiles.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = currentProfileFilter == ProfileSystemFilter.HOTSPOT,
                                onClick = { viewModel.updateProfileSystemFilter(ProfileSystemFilter.HOTSPOT) },
                                label = { Text("🌐 هوتسبوت ($countHotspot)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = currentProfileFilter == ProfileSystemFilter.USER_MANAGER,
                                onClick = { viewModel.updateProfileSystemFilter(ProfileSystemFilter.USER_MANAGER) },
                                label = { Text("👤 يوزرمانجر ($countUserManager)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                if (filteredProfiles.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.Default.Tune,
                            title = if (profiles.isEmpty()) "لا توجد باقات في سيرفر الميكروتك حالياً" else "لا توجد باقات في هذا القسم",
                            description = "يمكنك إضافة باقة جديدة الآن أو تغيير قسم التصفية، أو مزامنة الباقات بين الهوتسبوت ويوزرمانجر بضغطة زر.",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(filteredProfiles, key = { "${it.id}_${it.name}_${it.source}" }) { profile ->
                        val pricing = packagePricings.find { it.profileName.equals(profile.name, ignoreCase = true) }
                        val stats = profileStatsMap[profile.name] ?: ProfileDisplayStats()
                        ProfileCardItem(
                            profile = profile,
                            stats = stats,
                            pricing = pricing,
                            onViewDetailsClick = { profileToViewDetails = profile },
                            onDeleteClick = { profileToDelete = profile },
                            onSyncClick = {
                                val target = if (profile.isUserManager) "hotspot" else "usermanager"
                                viewModel.syncProfileBetweenSystems(profile, target)
                            },
                            onEditPricing = { cost, sale ->
                                viewModel.updatePackageCostAndPrice(profile.name, cost, sale)
                            }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = {
                initialTemplateForAdd = null
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_profile"),
            containerColor = CyberGreenPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = "إضافة باقة",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            initialTemplate = initialTemplateForAdd,
            onDismiss = {
                showAddDialog = false
                initialTemplateForAdd = null
            },
            onConfirm = { newProfile ->
                viewModel.createProfile(newProfile)
                val saleNum = newProfile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
                viewModel.updatePackageCostAndPrice(newProfile.name, newProfile.costPrice, saleNum)
                showAddDialog = false
                initialTemplateForAdd = null
            }
        )
    }

    profileToViewDetails?.let { profile ->
        ProfileDetailsDialog(
            profile = profile,
            onDismiss = { profileToViewDetails = null }
        )
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف الباقة من الراوتر", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف باقة (${profile.name}) نهائياً من راوتر ميكروتك؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف الباقة")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ProfileCardItem(
    profile: HotspotProfile,
    stats: ProfileDisplayStats,
    pricing: com.example.data.local.PackagePricingEntity? = null,
    onViewDetailsClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    onEditPricing: ((cost: Double, sale: Double) -> Unit)? = null
) {
    val totalCount = stats.totalCount
    val activeCount = stats.activeCount
    val unusedCount = stats.unusedCount
    val expiredCount = stats.expiredCount
    val soldCount = stats.soldCount
    val salePrice = stats.salePrice
    val costPrice = stats.costPrice
    val profitPerCard = stats.profitPerCard
    val profileRevenue = stats.profileRevenue
    val profileNetProfit = stats.profileNetProfit

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberGreenBright.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyberGreenBright,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val isUm = profile.isUserManager
                            val badgeColor = if (isUm) Color(0xFF00897B) else Color(0xFF1976D2)
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (isUm) "👤 يوزرمانجر" else "🌐 هوتسبوت",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                        if (profile.downloadLimit.isNotBlank() && profile.downloadLimit != "غير محدد") {
                            Text(
                                text = "التحميل: ${profile.downloadLimit} • الرفع: ${profile.uploadLimit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "مهلة الجلسة: ${profile.sessionTimeout}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = profile.price.ifBlank { "500 ر.ي" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onViewDetailsClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "عرض تفاصيل باقة السيرفر المعتمدة",
                            tint = CyberGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف الباقة",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Rate Limit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CyberGreenPrimary
                    )
                    Column {
                        Text(
                            text = "تحديد السرعة (Rate)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = profile.rateLimit,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Shared Users
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NetworkCyan
                    )
                    Column {
                        Text(
                            text = "الأجهزة المسموحة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${profile.sharedUsers} جهاز",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Session Timeout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Column {
                        Text(
                            text = "مهلة الجلسة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = profile.sessionTimeout,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Profile Card Statistics Breakdown & Revenue
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الكروت: إجمالي ($totalCount) • مباعة ($soldCount) • نشطة ($activeCount)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = CyberGreenPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ربح الكرت: +${profitPerCard.toInt()} ر.ي",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyberGreenBright,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "التكلفة: ${costPrice.toInt()} ر.ي • البيع: ${salePrice.toInt()} ر.ي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "صافي الربح: ${profileNetProfit.toLong()} ر.ي",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberGreenBright
                        )
                    }

                    if (onSyncClick != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onSyncClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (profile.isUserManager) Color(0xFF1976D2) else Color(0xFF00897B)
                            ),
                            border = BorderStroke(1.dp, (if (profile.isUserManager) Color(0xFF1976D2) else Color(0xFF00897B)).copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (profile.isUserManager) "🔄 مزامنة وتثبيت الباقة في الهوتسبوت (Hotspot)" else "🔄 مزامنة وتثبيت الباقة في يوزرمانجر (User Manager)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBadgeItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PackageTemplatesBanner(
    templates: List<PackageTemplate>,
    onSelectTemplate: (PackageTemplate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberGreenPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "⚡ قوالب مقترحة للإضافة السريعة إلى السيرفر",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "انقر على أي قالب لتعبئة بياناته وإنشاء باقة جديدة حقيقية في راوتر الميكروتك مباشرة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templates, key = { it.id }) { tpl ->
                    TemplateItemCard(
                        template = tpl,
                        onClick = { onSelectTemplate(tpl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateItemCard(
    template: PackageTemplate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(210.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (template.badge.isNotBlank()) {
                    Surface(
                        color = CyberGreenPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = template.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberGreenBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = template.defaultPrice,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyberGreenBright,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = template.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Text(
                text = template.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🚀 ${template.defaultRateLimit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetworkCyan,
                    fontSize = 10.sp
                )
                Text(
                    text = "⏳ ${template.defaultSessionTimeout}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            }

            if (template.defaultDataQuota.isNotBlank()) {
                Text(
                    text = "📦 الحصة: ${template.defaultDataQuota}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
            ) {
                Text("تطبيق القالب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileDetailsDialog(
    profile: HotspotProfile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = CyberGreenPrimary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "تفاصيل باقة السيرفر: ${profile.name}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CyberGreenPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberGreenPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyberGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "🔒 هذه الباقة مستوردة ومعتمدة من سيرفر الميكروتك مباشرة، وهي غير قابلة للتعديل للحفاظ على استقرار وتطابق كروت الهوتسبوت تماماً مع السيرفر وبدون أي تعارض.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                ProfileDetailItem(label = "اسم الباقة في السيرفر", value = profile.name)
                ProfileDetailItem(label = "تحديد السرعة (Rate Limit)", value = if (profile.rateLimit.isNotBlank() && profile.rateLimit != "غير محدد") profile.rateLimit else "مفتوح (حسب إعدادات الراوتر)")
                ProfileDetailItem(label = "الأجهزة المسموحة (Shared Users)", value = "${profile.sharedUsers} جهاز")
                ProfileDetailItem(label = "مهلة الجلسة (Session Timeout)", value = if (profile.sessionTimeout.isNotBlank() && profile.sessionTimeout != "غير محدد") profile.sessionTimeout else "غير محدد")
                ProfileDetailItem(label = "مهلة الخمول (Keepalive)", value = profile.keepaliveTimeout.ifBlank { "5m" })
                ProfileDetailItem(label = "سعر البيع المقترح", value = profile.price.ifBlank { "500 ر.ي" })
                ProfileDetailItem(label = "نوع الباقة ومصدرها", value = if (profile.source.contains("usermanager")) "يوزرمانجر (User Manager)" else "هوتسبوت ميكروتك (Hotspot Profile)")
                if (profile.comment.isNotBlank()) {
                    ProfileDetailItem(label = "ملاحظات الراوتر", value = profile.comment)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreenPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ProfileDetailItem(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AddProfileDialog(
    initialTemplate: PackageTemplate? = null,
    onDismiss: () -> Unit,
    onConfirm: (HotspotProfile) -> Unit
) {
    var name by remember(initialTemplate) { mutableStateOf(initialTemplate?.title?.split("(")?.first()?.trim() ?: "") }
    var rateLimit by remember(initialTemplate) { mutableStateOf(initialTemplate?.defaultRateLimit ?: "3M/3M") }
    var sharedUsers by remember(initialTemplate) { mutableIntStateOf(initialTemplate?.sharedUsers ?: 1) }
    var sessionTimeout by remember(initialTemplate) { mutableStateOf(initialTemplate?.defaultSessionTimeout ?: "1d") }
    var price by remember(initialTemplate) { mutableStateOf(initialTemplate?.defaultPrice ?: "500 ر.ي") }
    var dataLimit by remember(initialTemplate) { mutableStateOf(initialTemplate?.defaultDataQuota ?: "") }
    var targetSystem by remember { mutableStateOf("hotspot") }

    val quickPrices = listOf("100 ر.ي", "200 ر.ي", "300 ر.ي", "500 ر.ي", "1000 ر.ي", "2000 ر.ي", "5000 ر.ي")
    val quickTimes = listOf("30m", "1h", "2h", "3h", "6h", "12h", "1d", "3d", "7d", "30d")
    val quickSpeeds = listOf("512k/1M", "1M/2M", "2M/3M", "2M/4M", "4M/8M", "5M/10M", "10M/20M")
    val quickQuotas = listOf("500MB", "1024MB", "3072MB", "5120MB", "10240MB", "مفتوح")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGreenPrimary)
                Text(
                    text = if (initialTemplate != null) "إضافة باقة من قالب: ${initialTemplate.badge}" else "إضافة باقة هوتسبوت جديدة",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Templates Row
                Text(
                    text = "اختيار سريع من القوالب:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PREDEFINED_PACKAGE_TEMPLATES) { tpl ->
                        Surface(
                            onClick = {
                                name = tpl.title.split("(").first().trim()
                                rateLimit = tpl.defaultRateLimit
                                sessionTimeout = tpl.defaultSessionTimeout
                                price = tpl.defaultPrice
                                sharedUsers = tpl.sharedUsers
                                dataLimit = tpl.defaultDataQuota
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = if (name == tpl.title.split("(").first().trim()) BorderStroke(1.dp, CyberGreenPrimary) else null
                        ) {
                            Text(
                                text = "${tpl.badge} (${tpl.defaultPrice})",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // System Destination Selector
                Column {
                    Text(
                        text = "وجهة حفظ الباقة في الراوتر:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = targetSystem == "hotspot",
                            onClick = { targetSystem = "hotspot" },
                            label = { Text("🌐 هوتسبوت", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = targetSystem == "usermanager",
                            onClick = { targetSystem = "usermanager" },
                            label = { Text("👤 يوزرمانجر", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = targetSystem == "both",
                            onClick = { targetSystem = "both" },
                            label = { Text("✨ كلاهما", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الباقة (مثال: Daily 2GB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("السعر المقترح (مثال: 500 ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_price_input")
                )

                // Quick Prices
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickPrices) { qp ->
                        Surface(
                            onClick = { price = qp },
                            shape = RoundedCornerShape(8.dp),
                            color = if (price == qp) CyberGreenPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (price == qp) BorderStroke(1.dp, CyberGreenPrimary) else null
                        ) {
                            Text(
                                text = qp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (price == qp) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = rateLimit,
                    onValueChange = { rateLimit = it },
                    label = { Text("تحديد السرعة Rx/Tx (مثال: 2M/4M)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_rate_input")
                )

                // Quick Speeds
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickSpeeds) { qs ->
                        Surface(
                            onClick = { rateLimit = qs },
                            shape = RoundedCornerShape(8.dp),
                            color = if (rateLimit == qs) NetworkCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (rateLimit == qs) BorderStroke(1.dp, NetworkCyan) else null
                        ) {
                            Text(
                                text = qs,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (rateLimit == qs) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عدد الأجهزة المسموح بها:")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 3, 5).forEach { num ->
                            OutlinedButton(
                                onClick = { sharedUsers = num },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$num",
                                    fontWeight = if (sharedUsers == num) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = sessionTimeout,
                    onValueChange = { sessionTimeout = it },
                    label = { Text("مهلة الجلسة (مثال: 1h, 1d, 7d)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Timeouts
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickTimes) { qt ->
                        Surface(
                            onClick = { sessionTimeout = qt },
                            shape = RoundedCornerShape(8.dp),
                            color = if (sessionTimeout == qt) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (sessionTimeout == qt) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null
                        ) {
                            Text(
                                text = qt,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (sessionTimeout == qt) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dataLimit,
                    onValueChange = { dataLimit = it },
                    label = { Text("حصة البيانات (اختياري، مثال: 2048MB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Quotas
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickQuotas) { qq ->
                        Surface(
                            onClick = { dataLimit = if (qq == "مفتوح") "" else qq },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = qq,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            HotspotProfile(
                                id = "prof_${System.currentTimeMillis()}",
                                name = name.trim(),
                                rateLimit = rateLimit.trim(),
                                sharedUsers = sharedUsers,
                                sessionTimeout = sessionTimeout.trim(),
                                price = price.trim(),
                                dataLimit = dataLimit.trim(),
                                comment = if (initialTemplate != null) "Template: ${initialTemplate.badge}" else "",
                                source = targetSystem
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("submit_profile_btn")
            ) {
                Text("حفظ وتطبيق في السيرفر")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

