package com.example.ui.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.coaching.CoachingRepository
import com.example.data.coaching.UserAccountDoc
import com.example.data.trainer.AppRole
import com.example.ui.theme.DangerRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.controlBorder
import com.example.utils.ManilaDateTime
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(
    onLoginSuccess: (UserAccountDoc?) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val coachingRepo = remember(context) { CoachingRepository(context) }

    var isSignUpMode by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(AppRole.MEMBER) }
    var fullName by remember { mutableStateOf("") }
    var trainerTitle by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    fun checkNetwork(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun attemptSubmit() {
        focusManager.clearFocus()
        errorMessage = null

        val trimmedEmail = email.trim()
        val trimmedPassword = password
        val trimmedName = fullName.trim()

        if (isSignUpMode && trimmedName.isEmpty()) {
            errorMessage = "Please enter your full name."
            return
        }

        if (isSignUpMode && selectedRole == AppRole.MEMBER) {
            if (birthdate.isBlank()) {
                errorMessage = "Birthdate is required for member sign-up."
                return
            }
            if (!ManilaDateTime.isValidBirthdate(birthdate.trim())) {
                errorMessage = "Please select a valid birthdate (must be at least 10 years old)."
                return
            }
        }

        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            errorMessage = "Please enter your email and password."
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            errorMessage = "Please enter a valid email address."
            return
        }

        if (isSignUpMode && trimmedPassword.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }

        if (!checkNetwork()) {
            errorMessage = "No internet connection. Check your connection and try again."
            return
        }

        isLoading = true

        coroutineScope.launch {
            try {
                val auth = try {
                    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                        com.google.firebase.FirebaseApp.initializeApp(context)
                    }
                    FirebaseAuth.getInstance()
                } catch (_: Throwable) {
                    null
                }

                if (auth == null) {
                    errorMessage = "Firebase is not configured yet. Please check google-services.json."
                    return@launch
                }

                val authResult = if (isSignUpMode) {
                    auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                } else {
                    auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                }

                val uid = authResult.user?.uid ?: auth.currentUser?.uid
                if (uid == null) {
                    errorMessage = "Authentication succeeded, but user ID is unavailable."
                    return@launch
                }

                val fallbackDisplayName = trimmedName.ifBlank { trimmedEmail.substringBefore("@").ifBlank { "Athlete" } }
                val existingDoc = coachingRepo.fetchUserAccount(uid).getOrNull()
                val memberBirthdate = if (selectedRole == AppRole.MEMBER && birthdate.isNotBlank()) birthdate.trim() else null
                val resolvedAccount = if (existingDoc != null) {
                    coachingRepo.ensureUserAccountDoc(
                        uid = uid,
                        role = existingDoc.accountType,
                        name = existingDoc.name.ifBlank { fallbackDisplayName },
                        email = existingDoc.email.ifBlank { trimmedEmail },
                        birthdate = if (existingDoc.accountType == AppRole.MEMBER && existingDoc.birthdate == null) memberBirthdate else existingDoc.birthdate
                    ).getOrElse { existingDoc }
                } else {
                    com.example.data.coaching.CoachingSessionPrefs.saveUserMeta(
                        context,
                        uid,
                        selectedRole,
                        fallbackDisplayName
                    )
                    coachingRepo.ensureUserAccountDoc(
                        uid = uid,
                        role = selectedRole,
                        name = fallbackDisplayName,
                        email = trimmedEmail,
                        trainerTitle = trainerTitle.trim().takeIf { it.isNotBlank() },
                        birthdate = memberBirthdate
                    ).getOrElse {
                        com.example.data.coaching.UserAccountDoc(
                            userId = uid,
                            name = fallbackDisplayName,
                            email = trimmedEmail,
                            accountType = selectedRole,
                            birthdate = memberBirthdate,
                            calculatedAge = memberBirthdate?.let { ManilaDateTime.calculateAge(it) }
                        )
                    }
                }

                onLoginSuccess(resolvedAccount)
            } catch (e: FirebaseAuthWeakPasswordException) {
                errorMessage = "Password is too weak. Use at least 6 characters."
            } catch (e: FirebaseAuthUserCollisionException) {
                errorMessage = "An account with this email already exists. Try signing in."
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                errorMessage = if (e.errorCode == "ERROR_INVALID_EMAIL") {
                    "Please enter a valid email address."
                } else {
                    "Incorrect email or password."
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                errorMessage = "Incorrect email or password."
            } catch (e: FirebaseAuthException) {
                errorMessage = when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                    "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is not enabled in Firebase Authentication."
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try signing in."
                    "ERROR_INVALID_CREDENTIAL",
                    "ERROR_WRONG_PASSWORD",
                    "ERROR_USER_NOT_FOUND" -> "Incorrect email or password."
                    else -> {
                        val msg = e.message?.lowercase().orEmpty()
                        if (msg.contains("supplied auth credential") || msg.contains("malformed or has expired")) {
                            "Incorrect email or password."
                        } else {
                            "Authentication failed (${e.errorCode}). Please try again."
                        }
                    }
                }
            } catch (e: FirebaseTooManyRequestsException) {
                errorMessage = "Too many unsuccessful attempts. Please try again later."
            } catch (e: FirebaseNetworkException) {
                errorMessage = "No internet connection. Check your connection and try again."
            } catch (e: Exception) {
                val msg = e.message?.lowercase().orEmpty()
                errorMessage = when {
                    msg.contains("network") || msg.contains("connection") || msg.contains("timeout") || msg.contains("offline") ->
                        "No internet connection. Check your connection and try again."
                    msg.contains("invalid-email") || msg.contains("badly formatted") ->
                        "Please enter a valid email address."
                    msg.contains("email-already-in-use") ->
                        "An account with this email already exists. Try signing in."
                    msg.contains("supplied auth credential") ||
                        msg.contains("malformed or has expired") ||
                        msg.contains("invalid-credential") ||
                        msg.contains("invalid_login_credentials") ||
                        msg.contains("user-not-found") ||
                        msg.contains("wrong-password") ->
                        "Incorrect email or password."
                    msg.contains("too-many-requests") ->
                        "Too many unsuccessful attempts. Please try again later."
                    else ->
                        "Something went wrong. Please try again."
                }
            } finally {
                isLoading = false
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (screenWidthDp < 360) 16.dp else 24.dp
    val verticalPadding = if (isLandscape) 16.dp else 28.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
            }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

            Image(
                painter = painterResource(R.drawable.bodygrind_logo),
                contentDescription = "BodyGrind logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 16.dp))

            Text(
                text = "BodyGrind",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Member & Trainer Portal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = FlameOrange
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 18.dp else 24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isSignUpMode) {
                        Text(
                            text = "Account Role",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = selectedRole == AppRole.MEMBER,
                                onClick = { selectedRole = AppRole.MEMBER },
                                label = {
                                    Text(
                                        text = "Member",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_role_member")
                            )
                            FilterChip(
                                selected = selectedRole == AppRole.TRAINER,
                                onClick = { selectedRole = AppRole.TRAINER },
                                label = {
                                    Text(
                                        text = "Trainer",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Sports, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_role_trainer")
                            )
                        }

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = {
                                fullName = it
                                errorMessage = null
                            },
                            label = { Text("Full Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("full_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (selectedRole == AppRole.MEMBER) {
                            var showPicker by remember { mutableStateOf(false) }

                            if (showPicker) {
                                val cal = java.util.Calendar.getInstance(ManilaDateTime.MANILA_ZONE_ID)
                                val initialYear = cal.get(java.util.Calendar.YEAR) - 25
                                val initialMonth = 0
                                val initialDay = 1

                                val dateDialog = remember(context) {
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            birthdate = String.format("%04d-%02d-%02d", y, m + 1, d)
                                            errorMessage = null
                                            showPicker = false
                                        },
                                        initialYear,
                                        initialMonth,
                                        initialDay
                                    ).apply {
                                        setOnCancelListener { showPicker = false }
                                        setOnDismissListener { showPicker = false }
                                    }
                                }

                                androidx.compose.runtime.DisposableEffect(showPicker) {
                                    dateDialog.show()
                                    onDispose {
                                        dateDialog.dismiss()
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPicker = true }
                            ) {
                                OutlinedTextField(
                                    value = if (birthdate.isNotBlank()) ManilaDateTime.formatBirthdate(birthdate) else "",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    label = { Text("Birthdate *") },
                                    placeholder = { Text("Select birthdate (Required)") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.controlBorder,
                                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("birthdate_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    supportingText = {
                                        if (birthdate.isNotBlank()) {
                                            val age = ManilaDateTime.calculateAge(birthdate)
                                            Text("Age: $age yrs (calculated from birthdate)", color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Text("Required for personalized calorie target & workout plans", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                )
                            }
                        }

                        if (selectedRole == AppRole.TRAINER) {
                            OutlinedTextField(
                                value = trainerTitle,
                                onValueChange = { trainerTitle = it },
                                label = { Text("Coaching Title (optional)") },
                                placeholder = { Text("e.g. Strength & Conditioning Coach") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trainer_title_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { attemptSubmit() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DangerRed.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = DangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DangerRed,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("error_message")
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { attemptSubmit() },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSignUpMode) Icons.Default.PersonAdd else Icons.Default.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSignUpMode) "Create Account" else "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign in with your gym-issued account or create a Member / Trainer account to connect and sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
