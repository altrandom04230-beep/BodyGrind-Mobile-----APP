package com.example.ui.posebmi

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BmiCalculatorTest {

    @Test
    fun testBmiCalculation_standardExample() {
        // Example from specification:
        // Height: 1.70 meters (170 cm), Weight: 70 kg -> BMI: 24.2
        val heightCm = 170f
        val weightKg = 70f
        val bmi = BmiCalculator.calculateBmi(heightCm, weightKg)
        assertEquals(24.2f, bmi, 0.05f)
    }

    @Test
    fun testExactBmiRounding_160cm_80kg() {
        // 80 * 10000 / (160 * 160) = 800000 / 25600 = 31.25 -> 31.3 (Obesity)
        val bmiBd = BmiCalculator.calculateBmi("160", "80")
        assertEquals("31.3", bmiBd.toPlainString())
        val analysis = BmiCalculator.classifyAdultBmi(bmiBd, 160f)
        assertEquals(31.3f, analysis.bmi, 0.001f)
        assertEquals("Obesity", analysis.category)
    }

    @Test
    fun testExactBmiRounding_175cm_70kg() {
        // 70 * 10000 / (175 * 175) = 700000 / 30625 = 22.857... -> 22.9 (Normal weight)
        val bmiBd = BmiCalculator.calculateBmi("175", "70")
        assertEquals("22.9", bmiBd.toPlainString())
        val analysis = BmiCalculator.classifyAdultBmi(bmiBd, 175f)
        assertEquals(22.9f, analysis.bmi, 0.001f)
        assertEquals("Normal weight", analysis.category)
    }

    @Test
    fun testExactBmiRounding_200cm_99_8kg() {
        // 99.8 * 10000 / (200 * 200) = 998000 / 40000 = 24.95 -> 25.0 (Overweight)
        val bmiBd = BmiCalculator.calculateBmi("200", "99.8")
        assertEquals("25.0", bmiBd.toPlainString())
        val analysis = BmiCalculator.classifyAdultBmi(bmiBd, 200f)
        assertEquals(25.0f, analysis.bmi, 0.001f)
        assertEquals("Overweight", analysis.category)
    }

    @Test
    fun testRawInputValidation_emptyAndBlank() {
        val emptyH = BmiCalculator.validateRawInputs("", "70")
        assertFalse(emptyH.isValid)
        assertEquals("Please enter your height.", emptyH.errorMessage)

        val spacesH = BmiCalculator.validateRawInputs("   ", "70")
        assertFalse(spacesH.isValid)
        assertEquals("Please enter your height.", spacesH.errorMessage)

        val emptyW = BmiCalculator.validateRawInputs("175", "")
        assertFalse(emptyW.isValid)
        assertEquals("Please enter your weight.", emptyW.errorMessage)

        val spacesW = BmiCalculator.validateRawInputs("175", "   ")
        assertFalse(spacesW.isValid)
        assertEquals("Please enter your weight.", spacesW.errorMessage)
    }

    @Test
    fun testRawInputValidation_nonNumeric() {
        val invalidH = BmiCalculator.validateRawInputs("abc", "70")
        assertFalse(invalidH.isValid)
        assertEquals("Please enter a valid height.", invalidH.errorMessage)

        val invalidW = BmiCalculator.validateRawInputs("175", "xyz")
        assertFalse(invalidW.isValid)
        assertEquals("Please enter a valid weight.", invalidW.errorMessage)
    }

    @Test
    fun testAdultBmiClassification_underweight() {
        val heightCm = 180f
        val weightKg = 55f // BMI = 55 / 3.24 = 16.97
        val bmi = BmiCalculator.calculateBmi(heightCm, weightKg)
        val analysis = BmiCalculator.classifyAdultBmi(bmi, heightCm)

        assertEquals("Underweight", analysis.category)
        assertTrue(bmi < 18.5f)
        assertTrue(analysis.recommendations.isNotEmpty())
    }

    @Test
    fun testAdultBmiClassification_normalWeight() {
        val heightCm = 175f
        val weightKg = 68f // BMI = 68 / 3.0625 = 22.2
        val bmi = BmiCalculator.calculateBmi(heightCm, weightKg)
        val analysis = BmiCalculator.classifyAdultBmi(bmi, heightCm)

        assertEquals("Normal weight", analysis.category)
        assertTrue(bmi < 25.0f && bmi >= 18.5f)
    }

    @Test
    fun testAdultBmiClassification_overweight() {
        val heightCm = 175f
        val weightKg = 82f // BMI = 82 / 3.0625 = 26.8
        val bmi = BmiCalculator.calculateBmi(heightCm, weightKg)
        val analysis = BmiCalculator.classifyAdultBmi(bmi, heightCm)

        assertEquals("Overweight", analysis.category)
        assertTrue(bmi < 30.0f && bmi >= 25.0f)
    }

    @Test
    fun testAdultBmiClassification_obesity() {
        val heightCm = 170f
        val weightKg = 95f // BMI = 95 / 2.89 = 32.87
        val bmi = BmiCalculator.calculateBmi(heightCm, weightKg)
        val analysis = BmiCalculator.classifyAdultBmi(bmi, heightCm)

        assertEquals("Obesity", analysis.category)
        assertTrue(bmi >= 30.0f)
    }

    @Test
    fun testValidation_validInputs() {
        val result = BmiCalculator.validateInputs(175f, 70f)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun testValidation_zeroAndNegativeValues() {
        val zeroHeight = BmiCalculator.validateInputs(0f, 70f)
        assertFalse(zeroHeight.isValid)
        assertTrue(zeroHeight.errorMessage!!.contains("greater than zero"))

        val negWeight = BmiCalculator.validateInputs(175f, -5f)
        assertFalse(negWeight.isValid)
        assertTrue(negWeight.errorMessage!!.contains("greater than zero"))
    }

    @Test
    fun testValidation_implausibleHeight() {
        val tooShort = BmiCalculator.validateInputs(40f, 70f)
        assertFalse(tooShort.isValid)
        assertTrue(tooShort.errorMessage!!.contains("50 cm and 260 cm"))

        val tooTall = BmiCalculator.validateInputs(280f, 70f)
        assertFalse(tooTall.isValid)
        assertTrue(tooTall.errorMessage!!.contains("50 cm and 260 cm"))
    }

    @Test
    fun testValidation_implausibleWeight() {
        val tooLight = BmiCalculator.validateInputs(175f, 15f)
        assertFalse(tooLight.isValid)
        assertTrue(tooLight.errorMessage!!.contains("20 kg and 350 kg"))

        val tooHeavy = BmiCalculator.validateInputs(175f, 400f)
        assertFalse(tooHeavy.isValid)
        assertTrue(tooHeavy.errorMessage!!.contains("20 kg and 350 kg"))
    }

    @Test
    fun testUnitConversion_cmToFeetAndInches_andBack() {
        val (ft, inches) = BmiCalculator.cmToFeetAndInchesStrings(175f)
        assertEquals("5", ft)
        assertEquals("8.9", inches)

        val cmBack = BmiCalculator.feetAndInchesToCm("5", "8.9").toFloat()
        assertEquals(175f, cmBack, 0.1f)

        val (ftExact, inExact) = BmiCalculator.cmToFeetAndInchesStrings(182.88f)
        assertEquals("6", ftExact)
        assertEquals("0", inExact)
    }

    @Test
    fun testUnitConversion_kgToLb_andBack() {
        val lbStr = BmiCalculator.kgToLbString(70.0f)
        assertEquals("154.3", lbStr)

        val kgStr = BmiCalculator.lbToKgString(154.3f)
        assertEquals("70.0", kgStr)
    }

    @Test
    fun testImperialValidation_andCalculation() {
        val emptyH = BmiCalculator.validateRawImperialInputs("", "", "154")
        assertFalse(emptyH.isValid)
        assertEquals("Please enter your height.", emptyH.errorMessage)

        val badW = BmiCalculator.validateRawImperialInputs("5", "9", "abc")
        assertFalse(badW.isValid)
        assertEquals("Please enter a valid weight.", badW.errorMessage)

        val badInches = BmiCalculator.validateRawImperialInputs("5", "13", "154")
        assertFalse(badInches.isValid)
        assertEquals("Inches must be less than 12.", badInches.errorMessage)

        val valid = BmiCalculator.validateRawImperialInputs("5", "9", "154.3")
        assertTrue(valid.isValid)

        // 5 ft 9 in (175.26 cm), 154.3 lb (69.989 kg) -> BMI = 22.8
        val bmiBd = BmiCalculator.calculateBmiFromImperial("5", "9", "154.3")
        assertEquals("22.8", bmiBd.toPlainString())
    }
}
