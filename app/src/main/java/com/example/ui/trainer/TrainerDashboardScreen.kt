package com.example.ui.trainer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.coaching.*
import com.example.data.trainer.MemberStatus
import com.example.ui.theme.DangerRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.ManilaDateTime
import com.example.utils.QrCodeGenerator

@Composable
fun TrainerDashboardScreen(
    trainerAccount: UserAccountDoc?,
    inviteCode: String?,
    inviteError: String? = null,
    linksState: CoachingUiState<List<TrainerMemberLink>>,
    chatThreads: List<ChatThread>,
    totalUnreadMessages: Int,
    unreadCancellationAlerts: Int = 0,
    onRetryInvite: () -> Unit,
    onOpenMember: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSchedule: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }
    var showEnlargedQr by remember { mutableStateOf(false) }

    val links = (linksState as? CoachingUiState.Success)?.data.orEmpty()
    val activeLinks = remember(links) { links.filter { it.status == LinkStatus.ACTIVE } }
    val pendingLinks = remember(links) { links.filter { it.status == LinkStatus.PENDING_CONFIRMATION } }
    val onTrackCount = remember(activeLinks) { activeLinks.count { it.memberStatus == MemberStatus.ACTIVE } }
    val threadByMember = remember(chatThreads) { chatThreads.associateBy { it.memberId } }

    val qrBitmap = remember(inviteCode) {
        inviteCode?.takeIf { it.isNotBlank() }?.let { code ->
            QrCodeGenerator.generateQrImageBitmap(CoachingMappers.buildInviteQrPayload(code), sizePx = 512)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Coach Welcome Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Welcome, ${trainerAccount?.name?.ifBlank { "Coach" } ?: "Coach"}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = trainerAccount?.trainerTitle ?: "Certified Personal Trainer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trainerAccount?.specialization ?: "Strength, Conditioning & Form",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Schedule & Availability Quick Entry Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSchedule() }
                    .testTag("trainer_open_schedule_button"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (unreadCancellationAlerts > 0) DangerRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = FlameOrange.copy(alpha = 0.16f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Schedule and Availability",
                                tint = FlameOrange
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schedule & Availability",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set weekly hours, date overrides, view sessions & cancellations (Asia/Manila)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unreadCancellationAlerts > 0) {
                        Surface(
                            shape = CircleShape,
                            color = DangerRed
                        ) {
                            Text(
                                text = unreadCancellationAlerts.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Schedule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Trainer Invite Code & Scannable QR Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionHeader(icon = Icons.Default.QrCode2, title = "Invite Members")
                    Text(
                        text = "Members can scan this QR code or enter your invite code in My Coach to link with you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (inviteCode.isNullOrBlank()) {
                        if (!inviteError.isNullOrBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = inviteError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = com.example.ui.theme.DangerRed
                                )
                                OutlinedButton(onClick = onRetryInvite) {
                                    Text("Retry Invite Sync")
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FlameOrange, strokeWidth = 2.dp)
                                Text("Generating invite code...", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = onRetryInvite) { Text("Retry") }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (qrBitmap != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { showEnlargedQr = true }
                                        .testTag("trainer_invite_qr_container")
                                ) {
                                    Box(
                                        modifier = Modifier.size(116.dp),
                                        contentAlignment = Alignment.BottomEnd
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color.White,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Image(
                                                bitmap = qrBitmap,
                                                contentDescription = "Trainer Invite QR Code $inviteCode (Tap to enlarge)",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                                    .testTag("trainer_invite_qr_image")
                                            )
                                        }

                                        // Small expand icon badge overlay
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 14.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "Tap to enlarge",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to enlarge",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "INVITE CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = inviteCode,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = FlameOrange,
                                    modifier = Modifier.testTag("trainer_invite_code_text")
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(inviteCode))
                                        copiedBanner = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("trainer_copy_invite_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedBanner) "Copied" else "Copy Code")
                                }
                            }
                        }
                    }
                }
            }
        }

        // KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = "Linked Members",
                        value = "${activeLinks.size}",
                        subtitle = "Active coaching links",
                        icon = Icons.Default.Groups,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).testTag("stat_linked_members")
                    )
                    DashboardStatCard(
                        title = "Active Status",
                        value = "$onTrackCount",
                        subtitle = "On-track members",
                        icon = Icons.Default.VerifiedUser,
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1f).testTag("stat_active_members")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardStatCard(
                        title = "Pending Requests",
                        value = "${pendingLinks.size}",
                        subtitle = "Awaiting confirmation",
                        icon = Icons.Default.HourglassTop,
                        accentColor = WarningAmber,
                        modifier = Modifier.weight(1f).testTag("stat_pending_requests")
                    )
                    DashboardStatCard(
                        title = "Unread Messages",
                        value = "$totalUnreadMessages",
                        subtitle = "Tap to view inbox",
                        icon = Icons.Default.Forum,
                        accentColor = FlameOrange,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMessages() }
                            .testTag("stat_unread_messages")
                    )
                }
            }
        }

        // Linked Members Overview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(icon = Icons.Default.PeopleAlt, title = "Recent Member Activity")
                    Spacer(modifier = Modifier.height(10.dp))
                    when (linksState) {
                        is CoachingUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = FlameOrange, modifier = Modifier.size(24.dp))
                            }
                        }

                        is CoachingUiState.Error -> {
                            Text(
                                text = linksState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        is CoachingUiState.Success -> {
                            if (links.isEmpty()) {
                                Text(
                                    text = "No members linked yet. Share your code above to connect with your first athlete.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                links.take(6).forEachIndexed { idx, link ->
                                    val unread = threadByMember[link.memberId]?.trainerUnreadCount ?: 0
                                    val lastSeen = link.lastActiveAtMs?.let { ManilaDateTime.formatDate(it, fallback = "Just linked") } ?: "Just linked"
                                    val statusColor = when {
                                        link.status == LinkStatus.PENDING_CONFIRMATION -> WarningAmber
                                        link.memberStatus == MemberStatus.ACTIVE -> SuccessGreen
                                        link.memberStatus == MemberStatus.NEEDS_ATTENTION -> WarningAmber
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val statusText = if (link.status == LinkStatus.PENDING_CONFIRMATION) {
                                        "Pending Consent"
                                    } else {
                                        link.memberStatus.label
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onOpenMember(link.memberId) }
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = link.memberName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Last active: $lastSeen" + if (unread > 0) " • $unread unread" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusColor.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (idx < links.take(6).lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEnlargedQr && qrBitmap != null && !inviteCode.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showEnlargedQr = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 400.dp)
                    .padding(vertical = 24.dp)
                    .testTag("enlarged_qr_dialog_surface"),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Invite Code",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Hold member camera to scan",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                        IconButton(
                            onClick = { showEnlargedQr = false },
                            modifier = Modifier.testTag("close_enlarged_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF111827)
                            )
                        }
                    }

                    // Large QR Code keeping square shape with quiet zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Enlarged Trainer Invite QR Code $inviteCode",
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("enlarged_trainer_invite_qr_image")
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "INVITE CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = FlameOrange,
                            modifier = Modifier.testTag("enlarged_invite_code_text")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
