package com.example.ui.posebmi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BmiRecordEntity
import com.example.data.local.entity.BodyScanEntity
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class BodyPoseBmiSubScreen {
    SCREEN_A_INTRO,
    SCREEN_D_BMI_RESULTS,
    SCREEN_E_HISTORY_DASHBOARD
}

class BodyPoseBmiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = WorkoutRepository(database)

    // Current active sub-screen
    private val _currentSubScreen = MutableStateFlow(BodyPoseBmiSubScreen.SCREEN_A_INTRO)
    val currentSubScreen: StateFlow<BodyPoseBmiSubScreen> = _currentSubScreen.asStateFlow()

    // Measurement unit mode (true = kg / cm, false = lb / ft)
    private val _useMetric = MutableStateFlow(true)
    val useMetric: StateFlow<Boolean> = _useMetric.asStateFlow()

    // User measurements inputs in metric (pre-filled from profile)
    val heightCmInput = MutableStateFlow("175")
    val weightKgInput = MutableStateFlow("70.0")

    // User measurements inputs in imperial (lb / ft + in)
    val heightFeetInput = MutableStateFlow("5")
    val heightInchesInput = MutableStateFlow("8.9")
    val weightLbInput = MutableStateFlow("154.3")

    val scanNotesInput = MutableStateFlow("")

    // BMI Calculation result & validation error message
    val calculatedBmiResult = MutableStateFlow<BmiAnalysisResult?>(null)
    val inputErrorMessage = MutableStateFlow<String?>(null)

    // Historical records from database
    val bmiHistory: StateFlow<List<BmiRecordEntity>> = repository.getAllBmiRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestBmiRecord: StateFlow<BmiRecordEntity?> = repository.getLatestBmiRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bodyScansHistory: StateFlow<List<BodyScanEntity>> = repository.getAllBodyScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var canonicalWeightKg: Float = 70.0f
    private var canonicalHeightCm: Float = 175.0f
    private var weightDirty: Boolean = false
    private var heightDirty: Boolean = false
    private var currentProfile: com.example.data.local.entity.UserProfileEntity? = null

    init {
        viewModelScope.launch {
            var initializedInputs = false
            var lastObservedWeightKg: Float? = null
            var lastObservedHeightCm: Float? = null
            var lastObservedProfileMetric: Boolean? = null

            repository.getUserProfile().collect { profile ->
                currentProfile = profile
                if (profile != null) {
                    val hCm = if (profile.heightCm > 0f) profile.heightCm else 175f
                    val wKg = if (profile.weightKg > 0f) profile.weightKg else 70.0f
                    val biometricsChanged = !initializedInputs ||
                        lastObservedWeightKg == null ||
                        lastObservedHeightCm == null ||
                        kotlin.math.abs(wKg - lastObservedWeightKg!!) > 0.001f ||
                        kotlin.math.abs(hCm - lastObservedHeightCm!!) > 0.001f

                    if (biometricsChanged) {
                        canonicalWeightKg = wKg
                        canonicalHeightCm = hCm
                        weightDirty = false
                        heightDirty = false
                        lastObservedWeightKg = wKg
                        lastObservedHeightCm = hCm

                        heightCmInput.value = BmiCalculator.formatCmInput(hCm.toDouble())
                        weightKgInput.value = com.example.data.progress.BiometricValidator.formatWeightInput(wKg, true)

                        val (ft, inches) = BmiCalculator.cmToFeetAndInchesStrings(hCm)
                        heightFeetInput.value = ft
                        heightInchesInput.value = inches
                        weightLbInput.value = BmiCalculator.kgToLbString(wKg)

                        if (initializedInputs && calculatedBmiResult.value != null) {
                            val liveBmi = BmiCalculator.calculateBmi(
                                heightCmInput.value,
                                weightKgInput.value
                            )
                            calculatedBmiResult.value = BmiCalculator.classifyAdultBmi(liveBmi, hCm, profile.calculateAge())
                        }
                    }

                    if (!initializedInputs) {
                        _useMetric.value = profile.useMetric
                        lastObservedProfileMetric = profile.useMetric
                        initializedInputs = true
                    } else if (lastObservedProfileMetric != profile.useMetric) {
                        lastObservedProfileMetric = profile.useMetric
                        applyUseMetric(profile.useMetric, persist = false)
                    }
                }
            }
        }
    }

    fun onMetricHeightChanged(value: String) {
        heightCmInput.value = value
        heightDirty = true
        if (inputErrorMessage.value != null) inputErrorMessage.value = null
    }

    fun onMetricWeightChanged(value: String) {
        weightKgInput.value = value
        weightDirty = true
        if (inputErrorMessage.value != null) inputErrorMessage.value = null
    }

    fun onImperialHeightFeetChanged(value: String) {
        heightFeetInput.value = value
        heightDirty = true
        if (inputErrorMessage.value != null) inputErrorMessage.value = null
    }

    fun onImperialHeightInchesChanged(value: String) {
        heightInchesInput.value = value
        heightDirty = true
        if (inputErrorMessage.value != null) inputErrorMessage.value = null
    }

    fun onImperialWeightLbChanged(value: String) {
        weightLbInput.value = value
        weightDirty = true
        if (inputErrorMessage.value != null) inputErrorMessage.value = null
    }

    fun navigateTo(subScreen: BodyPoseBmiSubScreen) {
        _currentSubScreen.value = subScreen
    }

    fun setUseMetric(targetMetric: Boolean) {
        applyUseMetric(targetMetric, persist = true)
    }

    private fun applyUseMetric(targetMetric: Boolean, persist: Boolean) {
        if (_useMetric.value == targetMetric) return
        inputErrorMessage.value = null

        if (targetMetric) {
            // Switching from lb/ft to kg/cm: only recompute canonical metric if user edited imperial fields
            if (heightDirty) {
                val feetTrim = heightFeetInput.value.trim()
                val inchesTrim = heightInchesInput.value.trim()
                if (feetTrim.isNotEmpty() || inchesTrim.isNotEmpty()) {
                    try {
                        val cmBd = BmiCalculator.feetAndInchesToCm(feetTrim, inchesTrim)
                        if (cmBd > BigDecimal.ZERO) {
                            canonicalHeightCm = cmBd.toFloat()
                        }
                    } catch (_: NumberFormatException) { }
                }
                heightDirty = false
            }
            heightCmInput.value = BmiCalculator.formatCmInput(canonicalHeightCm.toDouble())

            if (weightDirty) {
                val lb = weightLbInput.value.trim().toFloatOrNull()
                if (lb != null && lb > 0f) {
                    canonicalWeightKg = com.example.utils.UnitConverter.lbToKg(lb)
                }
                weightDirty = false
            }
            weightKgInput.value = com.example.data.progress.BiometricValidator.formatWeightInput(canonicalWeightKg, true)
        } else {
            // Switching from kg/cm to lb/ft: only recompute canonical metric if user edited metric fields
            if (heightDirty) {
                val cm = heightCmInput.value.trim().toFloatOrNull()
                if (cm != null && cm > 0f) {
                    canonicalHeightCm = cm
                }
                heightDirty = false
            }
            val (ft, inches) = BmiCalculator.cmToFeetAndInchesStrings(canonicalHeightCm)
            heightFeetInput.value = ft
            heightInchesInput.value = inches

            if (weightDirty) {
                val kg = weightKgInput.value.trim().toFloatOrNull()
                if (kg != null && kg > 0f) {
                    canonicalWeightKg = kg
                }
                weightDirty = false
            }
            weightLbInput.value = BmiCalculator.kgToLbString(canonicalWeightKg)
        }

        _useMetric.value = targetMetric
        if (persist) {
            viewModelScope.launch {
                repository.updateUseMetric(targetMetric)
            }
        }
    }

    /**
     * Executes BMI validation, calculation with BigDecimal HALF_UP rounding to 1 decimal place,
     * classification into standard categories with healthy weight range,
     * and persists the record in metric (cm / kg) with sourceType fixed as MANUAL.
     */
    fun saveAssessment(onSaved: () -> Unit = {}) {
        inputErrorMessage.value = null

        val bmiBd: BigDecimal
        val h: Float
        val w: Float

        if (_useMetric.value) {
            val hStr = heightCmInput.value
            val wStr = weightKgInput.value

            val validation = BmiCalculator.validateRawInputs(hStr, wStr)
            if (!validation.isValid) {
                inputErrorMessage.value = validation.errorMessage
                return // STOP: do not calculate or save on validation failure
            }

            bmiBd = BmiCalculator.calculateBmi(hStr, wStr)
            h = BigDecimal(hStr.trim()).toFloat()
            w = BigDecimal(wStr.trim()).toFloat()
        } else {
            val fStr = heightFeetInput.value
            val iStr = heightInchesInput.value
            val wLbStr = weightLbInput.value

            val validation = BmiCalculator.validateRawImperialInputs(fStr, iStr, wLbStr)
            if (!validation.isValid) {
                inputErrorMessage.value = validation.errorMessage
                return // STOP: do not calculate or save on validation failure
            }

            bmiBd = BmiCalculator.calculateBmiFromImperial(fStr, iStr, wLbStr)
            h = BmiCalculator.feetAndInchesToCm(fStr, iStr).toFloat()
            w = BmiCalculator.lbToKg(wLbStr).toFloat()

            // Keep metric fields synchronized with converted values
            heightCmInput.value = BmiCalculator.formatCmInput(h.toDouble())
            weightKgInput.value = String.format(java.util.Locale.US, "%.1f", w)
        }

        val age = currentProfile?.calculateAge()
        val analysis = BmiCalculator.classifyAdultBmi(bmiBd, h, age)
        calculatedBmiResult.value = analysis

        viewModelScope.launch {
            val scanId = "bmi_calc_${System.currentTimeMillis()}"
            val scanEntity = BodyScanEntity(
                scanId = scanId,
                userId = 1,
                scanDate = System.currentTimeMillis(),
                cameraFacing = "MANUAL",
                poseDetectionStatus = "MANUAL",
                landmarkConfidence = 1f,
                scanNotes = scanNotesInput.value,
                bodyVisibilityStatus = "MANUAL"
            )

            val bmiRecord = BmiRecordEntity(
                bmiRecordId = "bmi_${System.currentTimeMillis()}",
                userId = 1,
                heightCm = h,
                weightKg = w,
                bmiValue = analysis.bmi,
                bmiCategory = analysis.category,
                assessmentDate = System.currentTimeMillis(),
                sourceType = "MANUAL",
                linkedScanId = scanId
            )

            repository.saveFullScanAndBmi(scanEntity, emptyList(), bmiRecord)
            onSaved()
        }
    }

    fun deleteBmiRecord(record: BmiRecordEntity) {
        viewModelScope.launch {
            repository.deleteBmiRecord(record)
        }
    }
}
