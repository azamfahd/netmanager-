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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HotspotCard
import com.example.data.model.HotspotProfile
import com.example.ui.theme.CyberGreenBright
import com.example.ui.theme.CyberGreenPrimary
import com.example.ui.theme.NetworkCyan
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val packagePricings by viewModel.packagePricings.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var initialTemplateForAdd by remember { mutableStateOf<PackageTemplate?>(null) }
    var profileToEdit by remember { mutableStateOf<HotspotProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<HotspotProfile?>(null) }

    // Calculate overall statistics across all profiles from real cards
    val totalProfiles = profiles.size
    val totalCardsCount = cards.size
    val activeCardsCount = cards.count { it.isActive }
    val unusedCardsCount = cards.count { it.isUnused }
    val expiredCardsCount = cards.count { it.isExpired }
    val soldCards = cards.filter { it.isActive || it.isExpired || (it.uptime.isNotBlank() && it.uptime != "0s") || it.bytesIn > 0L || it.bytesOut > 0L }
    val soldCardsCount = soldCards.size

    // Calculate real sold revenue and total inventory valuation
    val soldRevenue = soldCards.sumOf { card ->
        val prof = profiles.find { it.name.equals(card.profile, ignoreCase = true) }
        val priceStr = prof?.price ?: card.price
        val digits = priceStr.filter { it.isDigit() }
        digits.toDoubleOrNull() ?: 500.0
    }

    val totalInventoryValue = cards.sumOf { card ->
        val prof = profiles.find { it.name.equals(card.profile, ignoreCase = true) }
        val priceStr = prof?.price ?: card.price
        val digits = priceStr.filter { it.isDigit() }
        digits.toDoubleOrNull() ?: 500.0
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الباقات الحالية في السيرفر (${profiles.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (profiles.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.Default.Tune,
                            title = "لا توجد باقات في سيرفر الميكروتك حالياً",
                            description = "يتم قراءة الباقات مباشرة وحصرياً من راوتر الميكروتك المتصل (/ip/hotspot/user/profile). يمكنك إضافة باقة جديدة الآن أو استخدام أحد القوالب الجاهزة بالأعلى.",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(profiles, key = { it.id }) { profile ->
                        val pricing = packagePricings.find { it.profileName.equals(profile.name, ignoreCase = true) }
                        ProfileCardItem(
                            profile = profile,
                            cards = cards,
                            pricing = pricing,
                            onEditClick = { profileToEdit = profile },
                            onDeleteClick = { profileToDelete = profile },
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

    profileToEdit?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { profileToEdit = null },
            onConfirm = { updated ->
                viewModel.updateProfile(updated)
                val saleNum = updated.price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
                viewModel.updatePackageCostAndPrice(updated.name, updated.costPrice, saleNum)
                profileToEdit = null
            }
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
    cards: List<HotspotCard>,
    pricing: com.example.data.local.PackagePricingEntity? = null,
    onEditClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onEditPricing: ((cost: Double, sale: Double) -> Unit)? = null
) {
    // Filter cards belonging to this profile
    val profileCards = cards.filter { it.profile.equals(profile.name, ignoreCase = true) }
    val totalCount = profileCards.size
    val activeCount = profileCards.count { it.isActive }
    val unusedCount = profileCards.count { it.isUnused }
    val expiredCount = profileCards.count { it.isExpired }
    val soldCount = profileCards.count { it.isActive || it.isExpired || (it.uptime.isNotBlank() && it.uptime != "0s") || it.bytesIn > 0L || it.bytesOut > 0L }

    val salePrice = pricing?.salePrice ?: profile.priceNumber.takeIf { it > 0 } ?: profile.price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
    val costPrice = pricing?.costPrice ?: profile.costPrice.takeIf { it > 0 } ?: (salePrice * 0.40)
    val profitPerCard = (salePrice - costPrice).coerceAtLeast(0.0)
    val profileRevenue = soldCount * salePrice
    val profileNetProfit = soldCount * profitPerCard

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
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل الباقة",
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
fun EditProfileDialog(
    profile: HotspotProfile,
    onDismiss: () -> Unit,
    onConfirm: (HotspotProfile) -> Unit
) {
    var rateLimit by remember { mutableStateOf(profile.rateLimit) }
    var sharedUsers by remember { mutableIntStateOf(profile.sharedUsers) }
    var sessionTimeout by remember { mutableStateOf(profile.sessionTimeout) }
    var price by remember { mutableStateOf(profile.price) }
    var costPriceStr by remember {
        mutableStateOf(if (profile.costPrice > 0) profile.costPrice.toInt().toString() else "")
    }
    var dataLimit by remember { mutableStateOf(profile.dataLimit) }

    val quickPrices = listOf("100 ر.ي", "200 ر.ي", "300 ر.ي", "500 ر.ي", "1000 ر.ي", "2000 ر.ي", "5000 ر.ي")
    val quickTimes = listOf("30m", "1h", "2h", "3h", "6h", "12h", "1d", "3d", "7d", "30d")
    val quickSpeeds = listOf("512k/1M", "1M/2M", "2M/3M", "2M/4M", "4M/8M", "5M/10M", "10M/20M")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل باقة: ${profile.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("سعر البيع للكرت (مثال: 500 ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_price_input")
                )

                OutlinedTextField(
                    value = costPriceStr,
                    onValueChange = { costPriceStr = it },
                    label = { Text("سعر التكلفة للكرت الواحد (مثال: 200 ر.ي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_profile_cost_input")
                )

                // Quick Price Chips
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
                    label = { Text("السرعة Rx/Tx (مثال: 2M/4M أو 1024k/2048k)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Speed Chips
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
                    Text("عدد الأجهزة:")
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

                // Quick Timeout Chips
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saleNum = price.filter { it.isDigit() }.toDoubleOrNull() ?: 500.0
                    val costNum = costPriceStr.filter { it.isDigit() }.toDoubleOrNull() ?: (saleNum * 0.40)
                    onConfirm(
                        profile.copy(
                            rateLimit = rateLimit.trim(),
                            sharedUsers = sharedUsers,
                            sessionTimeout = sessionTimeout.trim(),
                            price = price.trim(),
                            priceNumber = saleNum,
                            costPrice = costNum,
                            dataLimit = dataLimit.trim()
                        )
                    )
                },
                modifier = Modifier.testTag("save_profile_btn")
            ) {
                Text("حفظ التعديلات في الراوتر")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
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
                modifier = Modifier.padding(vertical = 4.dp),
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
                                comment = if (initialTemplate != null) "Template: ${initialTemplate.badge}" else ""
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

