package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.ui.components.ProvideSafeTextToolbar
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.ui.MainViewModel
import com.example.ui.activeworkout.ActiveWorkoutScreen
import com.example.ui.activeworkout.ActiveWorkoutViewModel
import com.example.ui.calendar.CalendarScreen
import com.example.ui.customworkout.CustomWorkoutBuilderScreen
import com.example.ui.customworkout.CustomWorkoutViewModel
import com.example.ui.exercise.ExerciseDetailSheet
import com.example.ui.home.HomeScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.progress.ProgressScreen
import com.example.ui.theme.BoldRed
import com.example.ui.theme.HomeWorkoutTheme
import com.example.ui.workouts.WorkoutPlanDetailScreen
import com.example.ui.workouts.WorkoutsScreen
import kotlinx.coroutines.launch

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.data.coaching.CoachingRepository
import com.example.data.coaching.CoachingSessionPrefs
import com.example.data.coaching.CoachingUiState
import com.example.data.coaching.LinkStatus
import com.example.data.trainer.AppRole
import com.example.service.CancelledPlanNotificationWorker
import com.example.ui.auth.LoginScreen
import com.example.ui.coach.MemberCoachScreen
import com.example.ui.coach.MemberCoachingViewModel
import com.example.ui.settings.LookAndHomeSettingsScreen
import com.example.ui.trainer.TrainerMainScreen
import com.example.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException

private const val APP_CHECK_TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(APP_CHECK_TAG, "App Check debug provider configured successfully.")
            } else {
                Log.d(APP_CHECK_TAG, "No App Check debug token provided; skipping App Check installation.")
            }
        } else {
            val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(APP_CHECK_TAG, "RecaptchaAppCheckProviderFactory installed.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(APP_CHECK_TAG, "Failed to initialize Firebase App Check", e)
    }
}

sealed class Screen {
    data object Home : Screen()
    data object Workouts : Screen()
    data object Calendar : Screen()
    data object Meals : Screen()
    data object Progress : Screen()
    data class Profile(val initialTab: Int = 0) : Screen()
    data object LookAndHomeSettings : Screen()
    data class PlanDetail(val plan: WorkoutPlanEntity) : Screen()
    data class ActiveWorkout(val planId: String) : Screen()
    data object CustomBuilder : Screen()
    data class BodyPoseBmi(val initialStep: com.example.ui.posebmi.BodyPoseBmiSubScreen = com.example.ui.posebmi.BodyPoseBmiSubScreen.SCREEN_A_INTRO) : Screen()
    data object AiCalorieTracker : Screen()
    data object MuscleAnatomy : Screen()
    data class MyCoach(val initialOpenChat: Boolean = false) : Screen()
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private var pendingNotificationDestination by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        try {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.ui.calendar.reminder.CalendarEventReminderScheduler.syncAll(applicationContext)
            }
        } catch (_: Throwable) {}
        try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
            }
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank() &&
                CoachingSessionPrefs.getSavedRole(applicationContext, uid) == AppRole.MEMBER
            ) {
                CancelledPlanNotificationWorker.enqueuePeriodic(applicationContext)
                CancelledPlanNotificationWorker.enqueueImmediateCheck(applicationContext)
            }
        } catch (_: Throwable) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val destination = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_DESTINATION)
        if (!destination.isNullOrBlank()) {
            pendingNotificationDestination = destination
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshDateTick()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        pendingNotificationDestination = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_DESTINATION)
        enableEdgeToEdge()

        setContent {
            val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()
            val allPlans by mainViewModel.allPlans.collectAsStateWithLifecycle()
            val allExercises by mainViewModel.allExercises.collectAsStateWithLifecycle()
            val sessions by mainViewModel.completedSessions.collectAsStateWithLifecycle()
            val measurements by mainViewModel.measurements.collectAsStateWithLifecycle()
            val badges by mainViewModel.badges.collectAsStateWithLifecycle()
            val schedules by mainViewModel.schedules.collectAsStateWithLifecycle()
            val calendarEvents by mainViewModel.calendarEvents.collectAsStateWithLifecycle()
            val calendarExceptions by mainViewModel.calendarExceptions.collectAsStateWithLifecycle()
            val streakDays by mainViewModel.streakDays.collectAsStateWithLifecycle()
            val recommendedPlan by mainViewModel.recommendedPlan.collectAsStateWithLifecycle()

            val initialThemeMode = remember {
                com.example.data.preferences.ThemePreferences.getThemeMode(this@MainActivity)
            }
            var currentThemeMode by remember { mutableStateOf(initialThemeMode) }

            val systemDark = isSystemInDarkTheme()
            val isEffectiveDark = when (currentThemeMode) {
                "LIGHT" -> false
                "DARK" -> true
                "SYSTEM" -> systemDark
                else -> false // Default is LIGHT
            }

            fun updateAppTheme(newMode: String) {
                currentThemeMode = newMode
                com.example.data.preferences.ThemePreferences.setThemeMode(this@MainActivity, newMode)
                userProfile?.let { profile ->
                    mainViewModel.updateProfile(profile.copy(darkThemeMode = newMode))
                }
            }

            val accentHex = "#E50914"

            val currentProfile = userProfile ?: UserProfileEntity()

            fun getAuthSafely(): FirebaseAuth? {
                return try {
                    if (com.google.firebase.FirebaseApp.getApps(this@MainActivity).isEmpty()) {
                        com.google.firebase.FirebaseApp.initializeApp(this@MainActivity)
                    }
                    FirebaseAuth.getInstance()
                } catch (_: Throwable) {
                    null
                }
            }

            var currentAuthUser by remember { mutableStateOf(getAuthSafely()?.currentUser) }

            DisposableEffect(Unit) {
                val auth = getAuthSafely()
                if (auth != null) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        currentAuthUser = firebaseAuth.currentUser
                    }
                    auth.addAuthStateListener(listener)
                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                } else {
                    onDispose { }
                }
            }

            HomeWorkoutTheme(darkTheme = isEffectiveDark, accentColorHex = accentHex) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = this@MainActivity.window
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.isAppearanceLightStatusBars = !isEffectiveDark
                        insetsController.isAppearanceLightNavigationBars = !isEffectiveDark
                    }
                }
                ProvideSafeTextToolbar {
                    val coachingRepo = remember(applicationContext) { CoachingRepository(applicationContext) }
                    var resolvedRole by remember { mutableStateOf<AppRole?>(null) }
                    var isResolvingRole by remember { mutableStateOf(false) }
                    var roleResolveTrigger by remember { mutableIntStateOf(0) }

                    LaunchedEffect(currentAuthUser?.uid, roleResolveTrigger) {
                        val user = currentAuthUser
                        if (user == null) {
                            resolvedRole = null
                            isResolvingRole = false
                            return@LaunchedEffect
                        }
                        val cached = CoachingSessionPrefs.getSavedRole(applicationContext, user.uid)
                        if (cached != null) {
                            resolvedRole = cached
                        } else {
                            isResolvingRole = true
                        }
                        val fetchResult = coachingRepo.fetchUserAccount(user.uid)
                        fetchResult.onSuccess { doc ->
                            if (doc != null) {
                                resolvedRole = doc.accountType
                                mainViewModel.syncAccountIdentityIfDefault(
                                    accountName = doc.name.takeIf { it.isNotBlank() } ?: user.displayName,
                                    accountGoal = doc.fitnessGoal
                                )
                            } else {
                                // Legacy/existing Member accounts without a Firestore /users doc
                                val fallbackRole = cached ?: AppRole.MEMBER
                                val fallbackName = user.displayName?.takeIf { it.isNotBlank() }
                                    ?: currentProfile.name.takeIf { it.isNotBlank() && it != "Fitness Athlete" }
                                    ?: user.email?.substringBefore("@").orEmpty().ifBlank { "Member" }
                                mainViewModel.syncAccountIdentityIfDefault(accountName = user.displayName)
                                if (!com.example.data.coaching.CoachingSyncHealth.hasUserDocWritePermissionError(user.uid)) {
                                    coachingRepo.ensureUserAccountDoc(
                                        uid = user.uid,
                                        role = fallbackRole,
                                        name = fallbackName,
                                        email = user.email.orEmpty(),
                                        fitnessGoal = currentProfile.fitnessGoal,
                                        fitnessLevel = currentProfile.fitnessLevel
                                    )
                                }
                                resolvedRole = fallbackRole
                            }
                        }
                        isResolvingRole = false
                    }

                    LaunchedEffect(currentAuthUser?.uid, resolvedRole) {
                        val uid = currentAuthUser?.uid
                        when {
                            !uid.isNullOrBlank() && resolvedRole == AppRole.MEMBER -> {
                                CancelledPlanNotificationWorker.enqueuePeriodic(applicationContext)
                                CancelledPlanNotificationWorker.enqueueImmediateCheck(applicationContext)
                            }
                            uid.isNullOrBlank() || resolvedRole == AppRole.TRAINER || resolvedRole == AppRole.ADMIN -> {
                                CancelledPlanNotificationWorker.cancel(applicationContext)
                            }
                        }
                    }

                    val activeUser = currentAuthUser
                    if (activeUser == null) {
                        LoginScreen(
                            onLoginSuccess = { doc ->
                                if (doc != null) {
                                    resolvedRole = doc.accountType
                                    mainViewModel.syncAccountIdentityIfDefault(
                                        accountName = doc.name.takeIf { it.isNotBlank() },
                                        accountGoal = doc.fitnessGoal
                                    )
                                }
                                currentAuthUser = getAuthSafely()?.currentUser
                            }
                        )
                    } else {
                        key(activeUser.uid) {
                            when {
                                isResolvingRole && resolvedRole == null -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = BoldRed)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Loading your BodyGrind profile...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                resolvedRole == null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Offline Role Selection",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Connect to the internet to load your account type, or choose how to continue offline:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            CoachingSessionPrefs.saveUserMeta(applicationContext, activeUser.uid, AppRole.MEMBER)
                                                            resolvedRole = AppRole.MEMBER
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Member") }
                                                    OutlinedButton(
                                                        onClick = {
                                                            CoachingSessionPrefs.saveUserMeta(applicationContext, activeUser.uid, AppRole.TRAINER)
                                                            resolvedRole = AppRole.TRAINER
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Trainer") }
                                                }
                                                TextButton(onClick = { roleResolveTrigger++ }) {
                                                    Text("Retry Online Lookup")
                                                }
                                            }
                                        }
                                    }
                                }

                                resolvedRole == AppRole.TRAINER || resolvedRole == AppRole.ADMIN -> {
                                    TrainerMainScreen(
                                        onLogOut = {
                                            CancelledPlanNotificationWorker.cancel(applicationContext)
                                            try {
                                                getAuthSafely()?.signOut()
                                            } catch (_: Throwable) {}
                                            resolvedRole = null
                                            currentAuthUser = null
                                        }
                                    )
                                }

                                resolvedRole == AppRole.MEMBER -> {
                                    if (!currentProfile.onboardingCompleted) {
                                        OnboardingScreen(
                                            initialProfile = currentProfile,
                                            onCompleteOnboarding = { updated ->
                                                mainViewModel.updateProfile(updated)
                                            }
                                        )
                                    } else {
                                        MainAppContent(
                                            currentUid = activeUser.uid,
                                            mainViewModel = mainViewModel,
                                            userProfile = currentProfile,
                                            currentThemeMode = currentThemeMode,
                                            isEffectiveDark = isEffectiveDark,
                                            onThemeModeChange = { updateAppTheme(it) },
                                            allPlans = allPlans,
                                            allExercises = allExercises,
                                            sessions = sessions,
                                            measurements = measurements,
                                            badges = badges,
                                            schedules = schedules,
                                            calendarEvents = calendarEvents,
                                            calendarExceptions = calendarExceptions,
                                            streakDays = streakDays,
                                            recommendedPlan = recommendedPlan,
                                            pendingNotificationDestination = pendingNotificationDestination,
                                            onConsumeNotificationDestination = { pendingNotificationDestination = null },
                                            onSignOut = {
                                                CancelledPlanNotificationWorker.cancel(applicationContext)
                                                try {
                                                    getAuthSafely()?.signOut()
                                                } catch (_: Throwable) {}
                                                resolvedRole = null
                                                currentAuthUser = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    currentUid: String,
    mainViewModel: MainViewModel,
    userProfile: com.example.data.local.entity.UserProfileEntity,
    currentThemeMode: String,
    isEffectiveDark: Boolean,
    onThemeModeChange: (String) -> Unit,
    allPlans: List<WorkoutPlanEntity>,
    allExercises: List<ExerciseEntity>,
    sessions: List<com.example.data.local.entity.WorkoutSessionEntity>,
    measurements: List<com.example.data.local.entity.BodyMeasurementEntity>,
    badges: List<com.example.data.local.entity.BadgeEntity>,
    schedules: List<com.example.data.local.entity.WorkoutScheduleEntity>,
    calendarEvents: List<com.example.data.local.entity.CalendarEventEntity>,
    calendarExceptions: List<com.example.data.local.entity.CalendarEventExceptionEntity>,
    streakDays: Int,
    recommendedPlan: WorkoutPlanEntity?,
    pendingNotificationDestination: String? = null,
    onConsumeNotificationDestination: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val memberContext = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedExerciseForSheet by remember { mutableStateOf<ExerciseEntity?>(null) }

    val memberNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Prompt state is persisted before launch so granted or denied is never re-prompted
    }

    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !CoachingSessionPrefs.hasRequestedMemberNotificationPermission(memberContext, currentUid)
        ) {
            val hasPermission = ContextCompat.checkSelfPermission(
                memberContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            CoachingSessionPrefs.markMemberNotificationPermissionRequested(memberContext, currentUid)
            if (!hasPermission) {
                memberNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pendingNotificationDestination) {
        if (pendingNotificationDestination == NotificationHelper.DESTINATION_MY_COACH) {
            currentScreen = Screen.MyCoach(initialOpenChat = false)
            onConsumeNotificationDestination()
        }
    }
    var showChatbotDialog by remember { mutableStateOf(false) }
    var showTrainersSheet by remember { mutableStateOf(false) }
    val chatbotViewModel: com.example.ui.chatbot.ChatbotViewModel = viewModel()
    val poseBmiViewModel: com.example.ui.posebmi.BodyPoseBmiViewModel = viewModel()
    val mealsViewModel: com.example.ui.meals.SmartMealsViewModel = viewModel()
    val coachingViewModel: MemberCoachingViewModel = viewModel(key = currentUid.ifBlank { "guest" })
    val activeCoachLink by coachingViewModel.activeLink.collectAsStateWithLifecycle()
    val coachChatThread by coachingViewModel.chatThread.collectAsStateWithLifecycle()
    val trainerAvailabilityState by coachingViewModel.trainerAvailabilityState.collectAsStateWithLifecycle()
    val scheduledSessionsState by coachingViewModel.scheduledSessionsState.collectAsStateWithLifecycle()
    val trainerAvailability = (trainerAvailabilityState as? CoachingUiState.Success)?.data
    val scheduledSessions = (scheduledSessionsState as? CoachingUiState.Success)?.data.orEmpty()
    val coachUnreadCount = if (activeCoachLink?.status == LinkStatus.ACTIVE) {
        coachChatThread?.memberUnreadCount ?: 0
    } else 0
    val calendarErrorMessage by mainViewModel.calendarErrorMessage.collectAsStateWithLifecycle()
    val longestStreakDays by mainViewModel.longestStreakDays.collectAsStateWithLifecycle()
    val last30DaysStreak by mainViewModel.last30DaysStreak.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeCoachLink, userProfile, sessions, measurements, streakDays) {
        if (activeCoachLink?.status == LinkStatus.ACTIVE) {
            coachingViewModel.syncMemberDataIfLinked(
                profile = userProfile,
                sessions = sessions,
                measurements = measurements,
                streakDays = streakDays
            )
        }
    }

    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = when (currentScreen) {
            is Screen.PlanDetail -> Screen.Workouts
            is Screen.CustomBuilder -> Screen.Workouts
            is Screen.ActiveWorkout -> Screen.Home
            is Screen.LookAndHomeSettings -> Screen.Profile()
            is Screen.BodyPoseBmi -> Screen.Home
            is Screen.MuscleAnatomy -> Screen.Home
            is Screen.MyCoach -> Screen.Home
            is Screen.AiCalorieTracker -> Screen.Meals
            is Screen.Meals -> Screen.Home
            else -> Screen.Home
        }
    }

    // Plan detail exercises state
    var currentPlanExercises by remember { mutableStateOf<List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>>(emptyList()) }

    // Load exercises when entering PlanDetail
    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.PlanDetail) {
            val p = (currentScreen as Screen.PlanDetail).plan
            val pes = mainViewModel.repository.getPlanExercisesSync(p.id)
            val list = mutableListOf<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>()
            for (pe in pes) {
                val ex = mainViewModel.repository.getExerciseById(pe.exerciseId)
                if (ex != null) list.add(Pair(pe, ex))
            }
            currentPlanExercises = list
        }
    }

    val isTopLevelScreen = currentScreen is Screen.Home ||
            currentScreen is Screen.Workouts ||
            currentScreen is Screen.Calendar ||
            currentScreen is Screen.Meals ||
            currentScreen is Screen.Profile

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (isTopLevelScreen) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Members Portal",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val newMode = if (isEffectiveDark) "LIGHT" else "DARK"
                                onThemeModeChange(newMode)
                            },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (!isEffectiveDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (!isEffectiveDark) "Switch to Dark Theme" else "Switch to Light Theme",
                                tint = if (!isEffectiveDark) Color(0xFFF59E0B) else Color(0xFFFFD54F)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (isTopLevelScreen) {
                val configuration = LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isCompactWidth = screenWidthDp < 360

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    val navItems = listOf(
                        Triple(Screen.Home, "Home", Icons.Default.Home),
                        Triple(Screen.Workouts, "Workouts", Icons.Default.FitnessCenter),
                        Triple(Screen.Calendar, "Calendar", Icons.Default.CalendarMonth),
                        Triple(Screen.Meals, "Meals", Icons.Default.Restaurant),
                        Triple(Screen.Profile(), "Profile", Icons.Default.Person)
                    )

                    navItems.forEach { (screen, label, icon) ->
                        val isSelected = when (currentScreen) {
                            is Screen.Profile -> screen is Screen.Profile
                            else -> currentScreen == screen
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentScreen = if (screen is Screen.Profile) Screen.Profile(0) else screen
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (isCompactWidth) 10.sp else 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            alwaysShowLabel = !isLandscape && (!isCompactWidth || isSelected),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BoldRed,
                                selectedTextColor = BoldRed,
                                indicatorColor = BoldRed.copy(alpha = 0.18f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${label.lowercase()}")
                        )
                    }
                }
            }
        }
    )
 { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
            ) {
            when (val screen = currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        userProfile = userProfile,
                        streakDays = streakDays,
                        recommendedPlan = recommendedPlan,
                        popularPlans = allPlans.filter { !it.isChallenge },
                        challenges = allPlans.filter { it.isChallenge },
                        recentSessions = sessions.take(3),
                        badges = badges,
                        totalWorkouts = sessions.size,
                        totalCalories = sessions.sumOf { it.caloriesBurned },
                        onSelectPlan = { plan -> currentScreen = Screen.PlanDetail(plan) },
                        onStartRecommended = {
                            recommendedPlan?.let { currentScreen = Screen.ActiveWorkout(it.id) }
                        },
                        onCreateCustomPlan = { currentScreen = Screen.CustomBuilder },
                        onViewAllPlans = { currentScreen = Screen.Workouts },
                        onViewProgress = { currentScreen = Screen.Profile(initialTab = 1) },
                        onOpenTrainersDirectory = { showTrainersSheet = true },
                        onConsultTrainer = { trainerName, topic ->
                            chatbotViewModel.sendMessage(
                                query = "Can you provide $trainerName's expert advice on $topic, proper workout form, and how to train effectively in the gym?",
                                userProfile = userProfile,
                                allExercises = allExercises,
                                allPlans = allPlans,
                                recentSessions = sessions,
                                streakDays = streakDays
                            )
                            showChatbotDialog = true
                        },
                        onOpenBodyPoseBmi = {
                            poseBmiViewModel.navigateTo(com.example.ui.posebmi.BodyPoseBmiSubScreen.SCREEN_A_INTRO)
                            currentScreen = Screen.BodyPoseBmi()
                        },
                        activeCoachLink = activeCoachLink,
                        coachUnreadCount = coachUnreadCount,
                        onOpenMyCoach = { currentScreen = Screen.MyCoach(initialOpenChat = false) },
                        onOpenCoachChat = { currentScreen = Screen.MyCoach(initialOpenChat = true) },
                        longestStreakDays = longestStreakDays,
                        last30DaysStreak = last30DaysStreak,
                        onOpenProfile = { currentScreen = Screen.Profile(initialTab = 0) }
                    )
                }

                is Screen.Workouts -> {
                    WorkoutsScreen(
                        plans = allPlans,
                        exercises = allExercises,
                        onSelectPlan = { plan -> currentScreen = Screen.PlanDetail(plan) },
                        onSelectExercise = { ex -> selectedExerciseForSheet = ex },
                        onCreateCustomPlan = { currentScreen = Screen.CustomBuilder },
                        onOpenMuscleAnatomy = { currentScreen = Screen.MuscleAnatomy }
                    )
                }

                is Screen.Calendar -> {
                    CalendarScreen(
                        sessions = sessions,
                        schedules = schedules,
                        calendarEvents = calendarEvents,
                        calendarExceptions = calendarExceptions,
                        allPlans = allPlans,
                        streakDays = streakDays,
                        onToggleSchedule = { mainViewModel.toggleSchedule(it) },
                        onUpdateSchedule = { mainViewModel.updateSchedule(it) },
                        onSaveEvent = { mainViewModel.saveCalendarEvent(it) },
                        onUpdateEvent = { mainViewModel.updateCalendarEvent(it) },
                        onDeleteEvent = { mainViewModel.deleteCalendarEvent(it) },
                        onEditThisDayOnly = { eventId, date, newEvent ->
                            mainViewModel.editCalendarEventThisDayOnly(eventId, date, newEvent)
                        },
                        onDeleteThisDayOnly = { eventId, date ->
                            mainViewModel.deleteCalendarEventThisDayOnly(eventId, date)
                        },
                        calendarErrorMessage = calendarErrorMessage,
                        onClearCalendarErrorMessage = { mainViewModel.clearCalendarErrorMessage() },
                        onRestoreEvent = { mainViewModel.restoreCalendarEvent(it) },
                        onRestoreSeries = { series, exceptions -> mainViewModel.restoreCalendarSeries(series, exceptions) },
                        onUndoDeleteThisDayOnly = { eventId, date -> mainViewModel.undoDeleteThisDayOnly(eventId, date) },
                        onLoadExceptionsForEvent = { eventId -> mainViewModel.getCalendarExceptionsForEvent(eventId) },
                        onStartAssignedPlan = { plan ->
                            if (!plan.status.equals("CANCELLED", ignoreCase = true)) {
                                currentScreen = Screen.PlanDetail(plan)
                            }
                        },
                        trainerAvailability = if (activeCoachLink?.status == LinkStatus.ACTIVE) trainerAvailability else null,
                        scheduledSessions = if (activeCoachLink?.status == LinkStatus.ACTIVE) scheduledSessions else emptyList(),
                        activeCoachName = activeCoachLink?.takeIf { it.status == LinkStatus.ACTIVE }?.trainerName
                    )
                }

                is Screen.Meals -> {
                    com.example.ui.meals.SmartMealsScreen(
                        viewModel = mealsViewModel,
                        onNavigateBack = { currentScreen = Screen.Home },
                        onOpenAiCalorieTracker = { currentScreen = Screen.AiCalorieTracker }
                    )
                }

                is Screen.AiCalorieTracker -> {
                    com.example.ui.meals.aicalorie.AiCalorieTrackerScreen(
                        mealsViewModel = mealsViewModel,
                        onNavigateBack = { currentScreen = Screen.Meals },
                        onMealLogged = { currentScreen = Screen.Meals }
                    )
                }

                is Screen.Progress -> {
                    LaunchedEffect(Unit) {
                        currentScreen = Screen.Profile(initialTab = 1)
                    }
                }

                is Screen.Profile -> {
                    ProfileScreen(
                        userProfile = userProfile,
                        onUpdateProfile = { mainViewModel.updateProfileWithWeeklySync(it) },
                        onResetData = { mainViewModel.resetDatabase() },
                        onOpenLookAndHome = { currentScreen = Screen.LookAndHomeSettings },
                        onLogOut = onSignOut,
                        initialTab = screen.initialTab,
                        sessions = sessions,
                        measurements = measurements,
                        badges = badges,
                        onLogWeight = { w, h, ts -> mainViewModel.logWeight(w, h, ts) },
                        onLogMeasurement = { w, h, c, a, wst, notes, ts ->
                            mainViewModel.logMeasurement(w, h, c, a, wst, notes, ts)
                        },
                        onDeleteSession = { mainViewModel.deleteSession(it) },
                        onDeleteMeasurement = { mainViewModel.deleteMeasurement(it) },
                        onOpenBodyPoseBmi = {
                            poseBmiViewModel.navigateTo(com.example.ui.posebmi.BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD)
                            currentScreen = Screen.BodyPoseBmi(com.example.ui.posebmi.BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD)
                        },
                        activeCoachName = activeCoachLink?.takeIf { it.status == LinkStatus.ACTIVE }?.trainerName,
                        coachUnreadCount = coachUnreadCount,
                        onOpenMyCoach = { currentScreen = Screen.MyCoach(initialOpenChat = false) },
                        onUpdateUseMetric = { mainViewModel.updateUseMetric(it) },
                        onSaveWeeklyProgressRecord = { name, age, wKg, hCm, cCm, aCm, waCm, useMetric, onSaved ->
                            mainViewModel.saveWeeklyProgressRecord(
                                memberName = name,
                                memberAge = age,
                                weightKg = wKg,
                                heightCm = hCm,
                                chestCm = cCm,
                                armCm = aCm,
                                waistCm = waCm,
                                useMetric = useMetric,
                                onSaved = onSaved
                            )
                        }
                    )
                }

                is Screen.LookAndHomeSettings -> {
                    LookAndHomeSettingsScreen(
                        userProfile = userProfile,
                        currentThemeMode = currentThemeMode,
                        onThemeModeChange = onThemeModeChange,
                        onUpdateProfile = { mainViewModel.updateProfile(it) },
                        onNavigateBack = { currentScreen = Screen.Profile() }
                    )
                }

                is Screen.PlanDetail -> {
                    WorkoutPlanDetailScreen(
                        plan = screen.plan,
                        planExercises = currentPlanExercises,
                        onNavigateBack = { currentScreen = Screen.Workouts },
                        onStartWorkout = { planId -> currentScreen = Screen.ActiveWorkout(planId) },
                        onExerciseClick = { ex -> selectedExerciseForSheet = ex },
                        onDeleteCustomPlan = if (screen.plan.isCustom) {
                            {
                                mainViewModel.deleteCustomPlan(screen.plan.id)
                                currentScreen = Screen.Workouts
                            }
                        } else null
                    )
                }

                is Screen.CustomBuilder -> {
                    val customVm: CustomWorkoutViewModel = viewModel()
                    CustomWorkoutBuilderScreen(
                        viewModel = customVm,
                        onNavigateBack = { currentScreen = Screen.Workouts },
                        onPlanCreated = { planId ->
                            // Open newly created plan directly!
                            val createdPlan = allPlans.find { it.id == planId }
                            if (createdPlan != null) {
                                currentScreen = Screen.PlanDetail(createdPlan)
                            } else {
                                currentScreen = Screen.Workouts
                            }
                        }
                    )
                }

                is Screen.ActiveWorkout -> {
                    val activeVm = remember(screen.planId) {
                        ActiveWorkoutViewModel(
                            mainViewModel.getApplication(),
                            screen.planId
                        )
                    }
                    ActiveWorkoutScreen(
                        viewModel = activeVm,
                        onFinish = {
                            mainViewModel.updateStreak()
                            currentScreen = Screen.Home
                        }
                    )
                }

                is Screen.BodyPoseBmi -> {
                    LaunchedEffect(screen.initialStep) {
                        poseBmiViewModel.navigateTo(screen.initialStep)
                    }
                    com.example.ui.posebmi.BodyPoseBmiMainScreen(
                        viewModel = poseBmiViewModel,
                        onNavigateBack = { currentScreen = Screen.Home }
                    )
                }

                is Screen.MuscleAnatomy -> {
                    com.example.ui.anatomy.MuscleAnatomyScreen(
                        allExercises = allExercises,
                        initialIsFemale = userProfile.gender.equals("Female", ignoreCase = true),
                        onSelectExercise = { ex -> selectedExerciseForSheet = ex },
                        onNavigateBack = { currentScreen = Screen.Home }
                    )
                }

                is Screen.MyCoach -> {
                    MemberCoachScreen(
                        coachingViewModel = coachingViewModel,
                        userProfile = userProfile,
                        sessions = sessions,
                        measurements = measurements,
                        streakDays = streakDays,
                        localAssignedPlans = allPlans.filter { it.remoteAssignmentId != null },
                        onStartAssignedPlan = { plan ->
                            if (!plan.status.equals("CANCELLED", ignoreCase = true)) {
                                currentScreen = Screen.PlanDetail(plan)
                            }
                        },
                        onNavigateBack = { currentScreen = Screen.Home },
                        initialOpenChat = screen.initialOpenChat
                    )
                }
            }

            // Floating AI Chatbot Button
            if (userProfile.aiCoachEnabled && isTopLevelScreen && currentScreen !is Screen.ActiveWorkout && currentScreen !is Screen.LookAndHomeSettings && currentScreen !is Screen.BodyPoseBmi && currentScreen !is Screen.MuscleAnatomy) {
                com.example.ui.chatbot.FloatingAiButton(
                    onClick = { showChatbotDialog = true },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                )
            }

            // Exercise Detail Sheet
            selectedExerciseForSheet?.let { ex ->
                ExerciseDetailSheet(
                    exercise = ex,
                    isFemaleBodyDiagram = false,
                    isFemaleExerciseDrawing = false,
                    onDismiss = { selectedExerciseForSheet = null },
                    onToggleFavorite = { mainViewModel.toggleFavorite(it) }
                )
            }

            // AI Chatbot Dialog with RAG
            if (showChatbotDialog) {
                com.example.ui.chatbot.ChatbotDialog(
                    viewModel = chatbotViewModel,
                    userProfile = userProfile,
                    allExercises = allExercises,
                    allPlans = allPlans,
                    allSchedules = schedules,
                    recentSessions = sessions,
                    streakDays = streakDays,
                    onDismiss = { showChatbotDialog = false },
                    onSelectExercise = { ex ->
                        showChatbotDialog = false
                        selectedExerciseForSheet = ex
                    },
                    onSelectPlan = { plan ->
                        showChatbotDialog = false
                        currentScreen = Screen.PlanDetail(plan)
                    }
                )
            }

            // Available Gym Trainers Directory Sheet (Coach Cobie & Coach Jun)
            if (showTrainersSheet) {
                com.example.ui.trainers.TrainersDirectorySheet(
                    onDismiss = { showTrainersSheet = false },
                    onConsultWithAi = { trainerName, topic ->
                        chatbotViewModel.sendMessage(
                            query = "Can you provide $trainerName's expert advice on $topic, proper workout form, and how to train effectively in the gym?",
                            userProfile = userProfile,
                            allExercises = allExercises,
                            allPlans = allPlans,
                            allSchedules = schedules,
                            recentSessions = sessions,
                            streakDays = streakDays
                        )
                        showChatbotDialog = true
                    }
                )
            }
            }
        }
    }
}
