package com.example.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.coaching.MemberType
import com.example.data.coaching.UserAccountDoc
import com.example.ui.theme.BoldRed
import com.example.ui.theme.DangerRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.controlBorder

@Composable
fun AdminMemberManagementSection(
    members: List<UserAccountDoc>,
    isBusy: Boolean,
    onUpdateMember: (
        targetUserId: String,
        memberType: String,
        isVerified: Boolean,
        discountPercent: Double,
        discountAppliesToBilling: Boolean,
        discountAppliesToPackages: Boolean,
        discountAppliesToSessionFees: Boolean,
        onDone: (Boolean, String?) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMember by remember { mutableStateOf<UserAccountDoc?>(null) }

    if (members.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No Members Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Registered athlete members will appear here for role, verification, and discount management.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_members_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Member Management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${members.size} total members registered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = BoldRed)
                }
            }
        }

        items(members, key = { it.userId }) { member ->
            AdminMemberCard(
                member = member,
                onEdit = { editingMember = member }
            )
        }
    }

    val target = editingMember
    if (target != null) {
        AdminMemberEditDialog(
            member = target,
            isBusy = isBusy,
            onDismiss = { editingMember = null },
            onSave = { type, verified, discount, b, p, s ->
                onUpdateMember(target.userId, type, verified, discount, b, p, s) { success, _ ->
                    if (success) {
                        editingMember = null
                    }
                }
            }
        )
    }
}

@Composable
private fun AdminMemberCard(
    member: UserAccountDoc,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSeniorEligible = member.isSeniorEligible()
    val isSeniorCitizen = member.memberType.equals("Senior Citizen", ignoreCase = true)
    val needsSeniorReview = isSeniorEligible && !isSeniorCitizen

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_member_card_${member.userId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.name.ifBlank { "Member" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_member_btn_${member.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Member",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Calculated Age and Membership Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calculated age displayed to Admin (raw birthdate is hidden from Admin per visibility rules)
                val ageNum = member.effectiveCalculatedAge
                val ageText = if (ageNum != null) "$ageNum yrs (calculated)" else "Age not set"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Calculated Age: $ageText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Member Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (member.memberType) {
                        "Senior Citizen" -> BoldRed.copy(alpha = 0.15f)
                        "PWD" -> Color(0xFF6B21A8).copy(alpha = 0.15f)
                        "Student" -> FlameOrange.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = member.memberType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (member.memberType) {
                            "Senior Citizen" -> BoldRed
                            "PWD" -> Color(0xFF6B21A8)
                            "Student" -> FlameOrange
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // In-Person Verification Status
            val isDiscountedType = member.isDiscountedType
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (member.isVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "In-Person Verified",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (isDiscountedType) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Pending Verification",
                        tint = FlameOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "In-Person Verification Required for ${member.memberType} discount",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlameOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Standard Member (Verification not required)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Senior Citizen Review Flag (Age 60+)
            if (needsSeniorReview) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = FlameOrange.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Senior Citizen Review Needed (Age 60+)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = FlameOrange
                            )
                            Text(
                                text = "Member is age 60+. Verify in-person at front desk to approve Senior Citizen type.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                        ) {
                            Text("Review", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Discount Settings Summary
            val discountAppliesList = listOfNotNull(
                if (member.discountAppliesToBilling) "Billing" else null,
                if (member.discountAppliesToPackages) "Packages" else null,
                if (member.discountAppliesToSessionFees) "Session Fees" else null
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Discount,
                    contentDescription = null,
                    tint = if (member.discountPercent > 0.0) BoldRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (member.discountPercent > 0.0) {
                    Text(
                        text = "Discount: ${member.discountPercent.toInt()}% off (${discountAppliesList.joinToString(", ").ifBlank { "No charges selected" }})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = BoldRed
                    )
                } else {
                    Text(
                        text = "No discount configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminMemberEditDialog(
    member: UserAccountDoc,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        memberType: String,
        isVerified: Boolean,
        discountPercent: Double,
        discountAppliesToBilling: Boolean,
        discountAppliesToPackages: Boolean,
        discountAppliesToSessionFees: Boolean
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(member.memberType) }
    var isVerified by remember { mutableStateOf(member.isVerified) }
    var discountInput by remember { mutableStateOf(if (member.discountPercent > 0.0) member.discountPercent.toInt().toString() else "0") }
    var appliesBilling by remember { mutableStateOf(member.discountAppliesToBilling) }
    var appliesPackages by remember { mutableStateOf(member.discountAppliesToPackages) }
    var appliesSessions by remember { mutableStateOf(member.discountAppliesToSessionFees) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit Member", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${member.name.ifBlank { "Member" }} (${member.email})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Calculated Age Notice (Admin sees calculated age, not birthdate)
                val ageDialogNum = member.effectiveCalculatedAge
                val ageDialogText = if (ageDialogNum != null) "$ageDialogNum years" else "Not set by member"
                Text(
                    text = "Calculated Age: $ageDialogText",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // MEMBER TYPE SELECTION
                Text(
                    text = "MEMBER TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val types = MemberType.entries.map { it.label }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { type ->
                        val isSelected = selectedType.equals(type, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = type
                                if (type == "Regular") {
                                    // Regular does not require special verification
                                    isVerified = false
                                }
                            },
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(type, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    if (type != "Regular") {
                                        Text("(Verification required)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chip_member_type_$type"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // IN-PERSON VERIFICATION
                val isDiscountedType = selectedType != "Regular"
                if (isDiscountedType) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVerified) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else FlameOrange.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "In-Person Verified",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = isVerified,
                                    onCheckedChange = { isVerified = it },
                                    modifier = Modifier.testTag("switch_in_person_verified")
                                )
                            }
                            Text(
                                text = "Admin verifies physically in-person at gym front desk. No ID upload is required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // DISCOUNT CONFIGURATION
                Text(
                    text = "MEMBER DISCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = discountInput,
                    onValueChange = {
                        discountInput = it.filter { ch -> ch.isDigit() }.take(3)
                        validationError = null
                    },
                    label = { Text("Discount Percentage (%)") },
                    trailingIcon = { Text("%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_discount_percent"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                    )
                )

                Text(
                    text = "Apply discount to the following charges:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Charges checkboxes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appliesBilling = !appliesBilling },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = appliesBilling,
                        onCheckedChange = { appliesBilling = it },
                        modifier = Modifier.testTag("checkbox_apply_billing")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Billing (Membership Dues)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appliesPackages = !appliesPackages },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = appliesPackages,
                        onCheckedChange = { appliesPackages = it },
                        modifier = Modifier.testTag("checkbox_apply_packages")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Packages (Training Sessions Bundle)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appliesSessions = !appliesSessions },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = appliesSessions,
                        onCheckedChange = { appliesSessions = it },
                        modifier = Modifier.testTag("checkbox_apply_sessions")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Session Fees (Single coaching sessions)", style = MaterialTheme.typography.bodyMedium)
                }

                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val discountVal = discountInput.toDoubleOrNull() ?: 0.0
                    if (discountVal < 0.0 || discountVal > 100.0) {
                        validationError = "Discount must be between 0% and 100%."
                        return@Button
                    }
                    onSave(
                        selectedType,
                        if (selectedType == "Regular") false else isVerified,
                        discountVal,
                        appliesBilling,
                        appliesPackages,
                        appliesSessions
                    )
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_member_changes_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Cancel")
            }
        }
    )
}
