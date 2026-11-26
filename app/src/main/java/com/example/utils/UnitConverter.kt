package com.example.utils

import java.util.Locale

object UnitConverter {

    private const val KG_TO_LB = 2.20462f
    private const val CM_TO_INCH = 0.393701f

    fun kgToLb(kg: Float): Float = kg * KG_TO_LB

    fun lbToKg(lb: Float): Float = lb / KG_TO_LB

    fun cmToInches(cm: Float): Float = cm * CM_TO_INCH

    fun inchesToCm(inches: Float): Float = inches / CM_TO_INCH

    fun formatWeight(kg: Float, useMetric: Boolean): String {
        return if (useMetric) {
            String.format(Locale.US, "%.1f kg", kg)
        } else {
            val lb = kgToLb(kg)
            String.format(Locale.US, "%.1f lb", lb)
        }
    }

    fun formatHeight(cm: Float, useMetric: Boolean): String {
        return if (useMetric) {
            String.format(Locale.US, "%.0f cm", cm)
        } else {
            val totalInches = cmToInches(cm)
            val feet = (totalInches / 12).toInt()
            val inches = (totalInches % 12).toInt()
            "${feet}ft ${inches}in"
        }
    }

    fun formatWaist(cm: Float?, useMetric: Boolean): String {
        if (cm == null || cm <= 0f) return "--"
        return if (useMetric) {
            String.format(Locale.US, "%.1f cm", cm)
        } else {
            val inches = cmToInches(cm)
            String.format(Locale.US, "%.1f in", inches)
        }
    }
}
