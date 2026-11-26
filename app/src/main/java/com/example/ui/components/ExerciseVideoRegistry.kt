package com.example.ui.components

import androidx.annotation.RawRes
import com.example.R

object ExerciseVideoRegistry {

    /**
     * Resolves the corresponding Android raw video resource ID for an exercise.
     * Matches both by exercise ID (primary) and animationType (fallback).
     */
    @RawRes
    fun getVideoResId(exerciseId: String?, animationType: String? = null): Int? {
        val normalizedId = exerciseId?.lowercase()?.trim().orEmpty()
        val normalizedAnim = animationType?.uppercase()?.trim().orEmpty()

        return when {
            // 1. Cobra Stretch
            normalizedId == "cobra_stretch" || normalizedAnim == "COBRA_STRETCH" -> R.raw.cobra_stretch

            // 2. Chair Triceps Dips
            normalizedId in listOf("triceps_dips", "chair_triceps_dips") || normalizedAnim in listOf("TRICEPS_DIPS", "CHAIR_TRICEPS_DIPS") -> R.raw.chair_triceps_dips

            // 3. Wall Sit
            normalizedId == "wall_sit" || normalizedAnim == "WALL_SIT" -> R.raw.wall_sit

            // 4. Mountain Climbers
            normalizedId == "mountain_climbers" || normalizedAnim == "MOUNTAIN_CLIMBERS" -> R.raw.mountain_climbers

            // 5. Bodyweight Squats
            normalizedId in listOf("squats", "bodyweight_squats") || normalizedAnim == "SQUAT" -> R.raw.bodyweight_squats

            // 6. Plank Hold
            normalizedId in listOf("plank", "plank_hold") || normalizedAnim == "PLANK" -> R.raw.plank_hold

            // 7. Leg Raises
            normalizedId in listOf("leg_raises", "leg_raise") || normalizedAnim == "LEG_RAISES" -> R.raw.leg_raise

            // 8. Glute Bridge
            normalizedId in listOf("butt_bridge", "glute_bridge") || normalizedAnim in listOf("GLUTE_BRIDGE", "BUTT_BRIDGE") -> R.raw.glute_bridge

            // 9. Jumping Jacks
            normalizedId == "jumping_jacks" || normalizedAnim == "JUMPING_JACKS" -> R.raw.jumping_jacks

            // 10. High Knees
            normalizedId == "high_knees" || normalizedAnim == "HIGH_KNEES" -> R.raw.high_knees

            // 11. Bicycle Crunches
            normalizedId == "bicycle_crunches" || normalizedAnim == "BICYCLE_CRUNCHES" -> R.raw.bicycle_crunches

            // 12. Arm Circles
            normalizedId in listOf("arm_circles", "arm_circle") || normalizedAnim == "ARM_CIRCLES" -> R.raw.arm_circle

            // 13. Alternating Lunges
            normalizedId in listOf("lunges", "alternating_lunges") || normalizedAnim == "LUNGE" -> R.raw.alternating_lunges

            // 14. Abdominal Crunches
            normalizedId == "abdominal_crunches" || (normalizedAnim == "CRUNCHES" && normalizedId != "butt_bridge") -> R.raw.abdominal_crunches

            // 15. Diamond Push-Ups
            normalizedId in listOf("diamond_push_ups", "diamond_push_up") || normalizedAnim in listOf("DIAMOND_PUSH_UP", "DIAMOND_PUSH_UPS") -> R.raw.diamond_push_up

            // 16. Push-Ups
            normalizedId in listOf("push_ups", "push_up") || (normalizedAnim == "PUSH_UP" && normalizedId != "diamond_push_ups") -> R.raw.push_up

            // Fallback for CRUNCHES / PUSH_UP if id is unknown
            normalizedAnim == "CRUNCHES" -> R.raw.abdominal_crunches
            normalizedAnim == "PUSH_UP" -> R.raw.push_up

            else -> null
        }
    }
}
