package com.example.ui.trainer

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.ImeAction
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

@Composable
fun TrainerProfileSection(
    profile: TrainerProfileDoc?,
    isSaving: Boolean,
    isUploading: Boolean,
    onSaveProfile: (name: String, phone: String, onSuccess: () -> Unit) -> Unit,
    onUploadCertificate: (uri: Uri, fileName: String, mimeType: String, fileSizeBytes: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }

    var nameInput by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var phoneInput by remember(profile) { mutableStateOf(profile?.phone.orEmpty()) }

    var localError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val rawMime = contentResolver.getType(uri)
            val normalizedMime = TrainerProfileValidator.normalizeMimeType(rawMime)

            var displayName = "certificate"
            var fileSize: Long? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val validationErr = TrainerProfileValidator.validateCertificateFile(normalizedMime, fileSize)
            if (validationErr != null) {
                Toast.makeText(context, validationErr, Toast.LENGTH_LONG).show()
            } else {
                onUploadCertificate(uri, displayName, normalizedMime!!, fileSize)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Banner ---
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BoldRed.copy(alpha = 0.15f))
                        .border(2.dp, BoldRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sports,
                        contentDescription = null,
                        tint = BoldRed,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.name?.ifBlank { "Personal Trainer" } ?: "Personal Trainer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile?.specialization?.ifBlank { "Trainer Profile" } ?: "Save your profile details to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Hidden: certificate self-update is a separate future task.
        if (false) {
            // --- Certification Status Card ---
            Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trainer_cert_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Professional Certification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (profile?.hasCertificate == true) {
                        CertificateStatusBadge(status = profile.statusEnum)
                    }
                }

                if (profile?.hasCertificate == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isPdf = profile.certificateMimeType?.contains("pdf", ignoreCase = true) == true
                        Icon(
                            imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                            contentDescription = null,
                            tint = if (isPdf) DangerRed else BoldRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.certificateFileName ?: "certificate",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Type: ${profile.certificateMimeType ?: "document"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!profile.certificateUrl.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(profile.certificateUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open certificate: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "View Certificate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (profile == null) {
                            "Save your trainer profile information below first before uploading a certificate (PDF, JPG, or PNG, max 5 MB)."
                        } else {
                            "No certificate uploaded yet. Upload a PDF, JPG, or PNG file (max 5 MB). Uploading or replacing a certificate sets its status to Pending Review."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                    },
                    enabled = !isUploading && profile != null,
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("trainer_upload_cert_button")
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Uploading Certificate...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (profile?.hasCertificate == true) "Replace Certificate" else "Upload Certificate",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        }

        // --- Profile Information Card ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Trainer Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isEditing) {
                        TextButton(
                            onClick = {
                                isEditing = true
                                nameInput = profile?.name.orEmpty()
                                phoneInput = profile?.phone.orEmpty()
                                localError = null
                            },
                            modifier = Modifier.testTag("trainer_edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (profile == null) "Set Up" else "Edit")
                        }
                    }
                }

                if (!isEditing) {
                    // Read-only view
                    ProfileDetailRow(label = "Full Name", value = profile?.name?.ifBlank { "Not set" } ?: "Not set", icon = Icons.Default.Person)
                    ProfileDetailRow(label = "Email Address", value = profile?.email?.ifBlank { "Not set" } ?: "Not set", icon = Icons.Default.Email)
                    ProfileDetailRow(label = "Contact Phone", value = profile?.phone?.ifBlank { "Not set" } ?: "Not set", icon = Icons.Default.Phone)
                    ProfileDetailRow(label = "Specialization", value = profile?.specialization?.ifBlank { "Not set" } ?: "Not set", icon = Icons.Default.FitnessCenter)
                } else {
                    // Edit Form
                    AnimatedVisibility(visible = localError != null) {
                        Text(
                            text = localError.orEmpty(),
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            localError = null
                        },
                        label = { Text("Full Name (1-100 chars)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_trainer_name")
                    )

                    OutlinedTextField(
                        value = profile?.email?.ifBlank { "Not set" } ?: "Not set",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Email Address (Admin-managed)") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_trainer_email")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it.filter { ch -> ch.isDigit() }
                            localError = null
                        },
                        label = { Text("Contact Phone (Digits only, 7-15)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_trainer_phone")
                    )

                    OutlinedTextField(
                        value = profile?.specialization?.ifBlank { "Not set" } ?: "Not set",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Specialization (Admin-managed)") },
                        leadingIcon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_trainer_spec")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                localError = null
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                val firstErr = TrainerProfileValidator.validateName(nameInput)
                                    ?: TrainerProfileValidator.validatePhone(phoneInput)
                                if (firstErr != null) {
                                    localError = firstErr
                                } else {
                                    onSaveProfile(nameInput, phoneInput) {
                                        isEditing = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("trainer_save_profile_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Save Profile", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CertificateStatusBadge(
    status: CertificateStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (status) {
        CertificateStatus.APPROVED -> Triple(SuccessGreen.copy(alpha = 0.18f), SuccessGreen, Icons.Default.CheckCircle)
        CertificateStatus.PENDING -> Triple(WarningAmber.copy(alpha = 0.18f), WarningAmber, Icons.Default.Schedule)
        CertificateStatus.REJECTED -> Triple(DangerRed.copy(alpha = 0.18f), DangerRed, Icons.Default.Cancel)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.testTag("cert_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = status.displayLabel,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
