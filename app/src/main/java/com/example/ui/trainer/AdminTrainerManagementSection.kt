package com.example.ui.trainer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.trainer.CertificateStatus
import com.example.data.trainer.TrainerProfileDoc
import com.example.data.trainer.TrainerProfileValidator
import com.example.ui.theme.BoldRed
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTrainerManagementSection(
    trainers: List<TrainerProfileDoc>,
    currentFilter: CertificateStatus?,
    isBusy: Boolean,
    onFilterChange: (CertificateStatus?) -> Unit,
    onApproveCert: (trainerId: String) -> Unit,
    onRejectCert: (trainerId: String) -> Unit,
    onAddTrainer: (userId: String, name: String, email: String, phone: String, specialization: String, onSuccess: () -> Unit) -> Unit,
    onEditTrainer: (trainerId: String, name: String, email: String, phone: String, specialization: String, onSuccess: () -> Unit) -> Unit,
    onDeleteTrainer: (trainerId: String) -> Unit,
    onOpenSchedule: (TrainerProfileDoc) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var trainerToEdit by remember { mutableStateOf<TrainerProfileDoc?>(null) }
    var trainerToDelete by remember { mutableStateOf<TrainerProfileDoc?>(null) }
    var reviewTrainer by remember { mutableStateOf<TrainerProfileDoc?>(null) }

    val filteredList = remember(trainers, currentFilter, searchQuery) {
        trainers.filter { trainer ->
            val matchesFilter = currentFilter == null || trainer.statusEnum == currentFilter
            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                    trainer.name.lowercase().contains(query) ||
                    trainer.email.lowercase().contains(query) ||
                    trainer.specialization.lowercase().contains(query) ||
                    trainer.trainerId.lowercase().contains(query)
            matchesFilter && matchesSearch
        }
    }

    val pendingCount = remember(trainers) { trainers.count { it.statusEnum == CertificateStatus.PENDING } }
    val approvedCount = remember(trainers) { trainers.count { it.statusEnum == CertificateStatus.APPROVED } }
    val rejectedCount = remember(trainers) { trainers.count { it.statusEnum == CertificateStatus.REJECTED } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header and Add Trainer button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trainer Management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Manage profiles & review certificates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("admin_add_trainer_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Trainer", fontWeight = FontWeight.Bold)
            }
        }

        // --- Search Bar ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, email, or specialization...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_trainer_search_input")
        )

        // --- Filter Chips Row ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = currentFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("All (${trainers.size})") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_all")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == CertificateStatus.PENDING,
                    onClick = { onFilterChange(CertificateStatus.PENDING) },
                    label = { Text("Pending ($pendingCount)") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WarningAmber)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_pending")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == CertificateStatus.APPROVED,
                    onClick = { onFilterChange(CertificateStatus.APPROVED) },
                    label = { Text("Approved ($approvedCount)") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_approved")
                )
            }
            item {
                FilterChip(
                    selected = currentFilter == CertificateStatus.REJECTED,
                    onClick = { onFilterChange(CertificateStatus.REJECTED) },
                    label = { Text("Rejected ($rejectedCount)") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_rejected")
                )
            }
        }

        // --- Trainer Cards List ---
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No trainers matching '$searchQuery'" else "No trainer records found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { it.trainerId }) { trainer ->
                    AdminTrainerCard(
                        trainer = trainer,
                        isBusy = isBusy,
                        onEdit = { trainerToEdit = trainer },
                        onOpenSchedule = { onOpenSchedule(trainer) },
                        onReviewCert = { reviewTrainer = trainer },
                        onQuickApprove = { onApproveCert(trainer.trainerId) },
                        onQuickReject = { onRejectCert(trainer.trainerId) },
                        onDelete = { trainerToDelete = trainer },
                        onOpenCertUrl = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open document: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // --- Add Trainer Dialog ---
    if (showAddDialog) {
        AddTrainerDialog(
            isBusy = isBusy,
            onDismiss = { showAddDialog = false },
            onConfirm = { uid, name, email, phone, spec ->
                onAddTrainer(uid, name, email, phone, spec) {
                    showAddDialog = false
                }
            }
        )
    }

    // --- Edit Trainer Dialog ---
    trainerToEdit?.let { target ->
        EditTrainerDialog(
            trainer = target,
            isBusy = isBusy,
            onDismiss = { trainerToEdit = null },
            onConfirm = { name, email, phone, spec ->
                onEditTrainer(target.trainerId, name, email, phone, spec) {
                    trainerToEdit = null
                }
            }
        )
    }

    // --- Delete Confirmation Dialog ---
    trainerToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { trainerToDelete = null },
            title = { Text("Delete Trainer Profile") },
            text = { Text("Are you sure you want to remove the trainer profile for ${target.name} (${target.email})? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTrainer(target.trainerId)
                        trainerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("admin_confirm_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { trainerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Review Certificate Dialog ---
    reviewTrainer?.let { target ->
        CertificateReviewDialog(
            trainer = target,
            isBusy = isBusy,
            onDismiss = { reviewTrainer = null },
            onApprove = {
                onApproveCert(target.trainerId)
                reviewTrainer = null
            },
            onReject = {
                onRejectCert(target.trainerId)
                reviewTrainer = null
            },
            onOpenDoc = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open document", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun AdminTrainerCard(
    trainer: TrainerProfileDoc,
    isBusy: Boolean,
    onEdit: () -> Unit,
    onOpenSchedule: () -> Unit,
    onReviewCert: () -> Unit,
    onQuickApprove: () -> Unit,
    onQuickReject: () -> Unit,
    onDelete: () -> Unit,
    onOpenCertUrl: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_trainer_card_${trainer.trainerId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trainer.name.ifBlank { "Trainer (${trainer.trainerId.take(6)})" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trainer.specialization.ifBlank { "Personal Trainer" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CertificateStatusBadge(status = trainer.statusEnum)
            }

            // Contact info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(
                        text = trainer.email.ifBlank { "No email" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(
                        text = trainer.phone.ifBlank { "No phone" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Certificate Details / Preview Row
            if (trainer.hasCertificate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val isPdf = trainer.certificateMimeType?.contains("pdf", ignoreCase = true) == true
                        Icon(
                            imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                            contentDescription = null,
                            tint = if (isPdf) DangerRed else BoldRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = trainer.certificateFileName ?: "certificate",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!trainer.certificateUrl.isNullOrBlank()) {
                        IconButton(
                            onClick = { onOpenCertUrl(trainer.certificateUrl) }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "View Certificate", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Text(
                    text = "No certificate uploaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action Buttons
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenSchedule,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("admin_trainer_schedule_btn_${trainer.trainerId}")
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Manage Trainer Schedule & Availability",
                            tint = BoldRed
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("admin_edit_trainer_btn")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Trainer Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("admin_delete_trainer_btn")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Trainer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trainer.hasCertificate &&
                        (trainer.statusEnum == CertificateStatus.PENDING || trainer.statusEnum == CertificateStatus.REJECTED)
                    ) {
                        Button(
                            onClick = onQuickApprove,
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_approve_cert_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (trainer.hasCertificate &&
                        (trainer.statusEnum == CertificateStatus.PENDING || trainer.statusEnum == CertificateStatus.APPROVED)
                    ) {
                        OutlinedButton(
                            onClick = onQuickReject,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_reject_cert_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onReviewCert,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Review", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddTrainerDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (userId: String, name: String, email: String, phone: String, spec: String) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Trainer Profile", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Create a trainer profile for an existing user ID in Firestore. No new auth account is created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = localError != null) {
                    Text(
                        text = localError.orEmpty(),
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it.trim(); localError = null },
                    label = { Text("Existing User ID (Auth UID)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_add_uid")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Trainer Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_add_name")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim(); localError = null },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_add_email")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }; localError = null },
                    label = { Text("Contact Phone (Digits only, 7-15)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_add_phone")
                )

                OutlinedTextField(
                    value = spec,
                    onValueChange = { spec = it; localError = null },
                    label = { Text("Specialization") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_add_spec")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val firstErr = TrainerProfileValidator.validateUserId(userId)
                        ?: TrainerProfileValidator.validateName(name)
                        ?: TrainerProfileValidator.validateEmail(email)
                        ?: TrainerProfileValidator.validatePhone(phone)
                        ?: TrainerProfileValidator.validateSpecialization(spec)
                    if (firstErr != null) {
                        localError = firstErr
                    } else {
                        onConfirm(userId, name, email, phone, spec)
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                modifier = Modifier.testTag("admin_save_new_trainer_btn")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Add Profile")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTrainerDialog(
    trainer: TrainerProfileDoc,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, phone: String, spec: String) -> Unit
) {
    var name by remember(trainer) { mutableStateOf(trainer.name) }
    var email by remember(trainer) { mutableStateOf(trainer.email) }
    var phone by remember(trainer) { mutableStateOf(trainer.phone.filter { it.isDigit() }) }
    var spec by remember(trainer) { mutableStateOf(trainer.specialization) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Trainer Profile", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "User ID: ${trainer.trainerId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(visible = localError != null) {
                    Text(
                        text = localError.orEmpty(),
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; localError = null },
                    label = { Text("Trainer Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_edit_name")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim(); localError = null },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_edit_email")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() }; localError = null },
                    label = { Text("Contact Phone (Digits only, 7-15)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_edit_phone")
                )

                OutlinedTextField(
                    value = spec,
                    onValueChange = { spec = it; localError = null },
                    label = { Text("Specialization") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_admin_edit_spec")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val firstErr = TrainerProfileValidator.validateName(name)
                        ?: TrainerProfileValidator.validateEmail(email)
                        ?: TrainerProfileValidator.validatePhone(phone)
                        ?: TrainerProfileValidator.validateSpecialization(spec)
                    if (firstErr != null) {
                        localError = firstErr
                    } else {
                        onConfirm(name, email, phone, spec)
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                modifier = Modifier.testTag("admin_save_edit_trainer_btn")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CertificateReviewDialog(
    trainer: TrainerProfileDoc,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenDoc: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BoldRed)
                Text("Review Certificate", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Trainer: ${trainer.name}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Specialization: ${trainer.specialization}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Current Status:", style = MaterialTheme.typography.bodyMedium)
                    CertificateStatusBadge(status = trainer.statusEnum)
                }

                if (trainer.hasCertificate) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Document:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(trainer.certificateFileName.orEmpty().ifBlank { "certificate" }, fontSize = 14.sp)
                            if (!trainer.certificateMimeType.isNullOrBlank()) {
                                Text("Type: ${trainer.certificateMimeType}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!trainer.certificateUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { onOpenDoc(trainer.certificateUrl) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Document")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No certificate document has been uploaded by this trainer yet. Approval and rejection are disabled until a file is uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = !isBusy && trainer.hasCertificate,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isBusy && trainer.hasCertificate,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
