package se.kidquest.app.pet

import se.kidquest.app.R

object PetImages {

    private fun eggToPetType(key: String): String? =
        when (key) {
            // Egg type IDs from backend (EGG_TO_PET_MAP)
            "blue_egg" -> "dragon"
            "green_egg" -> "cat"
            "red_egg" -> "dog"
            "yellow_egg" -> "bird"
            "purple_egg" -> "rabbit"
            "orange_egg" -> "bear"
            "brown_egg" -> "snake"
            "black_egg" -> "panda"
            "gray_egg" -> "slot"
            "teal_egg" -> "hydra"
            "pink_egg" -> "unicorn"
            "cyan_egg" -> "kapybara"

            // Fallbacks if backend ever returns petType instead of eggType
            "dragon" -> "dragon"
            "cat" -> "cat"
            "dog" -> "dog"
            "bird" -> "bird"
            "rabbit" -> "rabbit"
            "bear" -> "bear"
            "snake" -> "snake"
            "panda" -> "panda"
            "slot" -> "slot"
            "hydra" -> "hydra"
            "unicorn" -> "unicorn"
            "kapybara" -> "kapybara"
            else -> null
        }

    fun getEggDrawable(eggType: String?): Int? {
        val key = eggType?.lowercase() ?: return null
        return when (eggToPetType(key)) {
            "dragon" -> R.drawable.dragon_egg_stage1
            "cat" -> R.drawable.cat_egg_stage1
            "dog" -> R.drawable.dog_egg_stage1
            "bird" -> R.drawable.bird_egg_stage1
            "rabbit" -> R.drawable.rabbit_egg_stage1
            "bear" -> R.drawable.bear_egg_stage1
            "snake" -> R.drawable.snake_egg_stage1
            "panda" -> R.drawable.panda_egg_stage1
            "slot" -> R.drawable.slot_egg_stage1
            "hydra" -> R.drawable.hydra_egg_stage1
            "unicorn" -> R.drawable.unicorn_egg_stage1
            "kapybara" -> R.drawable.kapybara_egg_stage1
            else -> null
        }
    }

    fun getIntegratedFromEgg(eggType: String?, growthStage: Int = 1): Int? {
        val key = eggType?.lowercase() ?: return null
        val petType = eggToPetType(key) ?: return null
        return getIntegratedPetDrawable(petType, growthStage)
    }

    fun getEggStageDrawable(eggType: String?, stage: Int): Int? {
        val key = eggType?.lowercase() ?: return null
        val petType = eggToPetType(key) ?: return null
        val s = stage.coerceIn(1, 5)
        return when (petType) {
            "dragon" -> when (s) {
                1 -> R.drawable.dragon_egg_stage1
                2 -> R.drawable.dragon_egg_stage2
                3 -> R.drawable.dragon_egg_stage3
                4 -> R.drawable.dragon_egg_stage4
                else -> R.drawable.dragon_egg_stage5
            }
            "cat" -> when (s) {
                1 -> R.drawable.cat_egg_stage1
                2 -> R.drawable.cat_egg_stage2
                3 -> R.drawable.cat_egg_stage3
                4 -> R.drawable.cat_egg_stage4
                else -> R.drawable.cat_egg_stage5
            }
            "dog" -> when (s) {
                1 -> R.drawable.dog_egg_stage1
                2 -> R.drawable.dog_egg_stage2
                3 -> R.drawable.dog_egg_stage3
                4 -> R.drawable.dog_egg_stage4
                else -> R.drawable.dog_egg_stage5
            }
            "bird" -> when (s) {
                1 -> R.drawable.bird_egg_stage1
                2 -> R.drawable.bird_egg_stage2
                3 -> R.drawable.bird_egg_stage3
                4 -> R.drawable.bird_egg_stage4
                else -> R.drawable.bird_egg_stage5
            }
            "rabbit" -> when (s) {
                1 -> R.drawable.rabbit_egg_stage1
                2 -> R.drawable.rabbit_egg_stage2
                3 -> R.drawable.rabbit_egg_stage3
                4 -> R.drawable.rabbit_egg_stage4
                else -> R.drawable.rabbit_egg_stage5
            }
            "bear" -> when (s) {
                1 -> R.drawable.bear_egg_stage1
                2 -> R.drawable.bear_egg_stage2
                3 -> R.drawable.bear_egg_stage3
                4 -> R.drawable.bear_egg_stage4
                else -> R.drawable.bear_egg_stage5
            }
            "snake" -> when (s) {
                1 -> R.drawable.snake_egg_stage1
                2 -> R.drawable.snake_egg_stage2
                3 -> R.drawable.snake_egg_stage3
                4 -> R.drawable.snake_egg_stage4
                else -> R.drawable.snake_egg_stage5
            }
            "panda" -> when (s) {
                1 -> R.drawable.panda_egg_stage1
                2 -> R.drawable.panda_egg_stage2
                3 -> R.drawable.panda_egg_stage3
                4 -> R.drawable.panda_egg_stage4
                else -> R.drawable.panda_egg_stage5
            }
            "slot" -> when (s) {
                1 -> R.drawable.slot_egg_stage1
                2 -> R.drawable.slot_egg_stage2
                3 -> R.drawable.slot_egg_stage3
                4 -> R.drawable.slot_egg_stage4
                else -> R.drawable.slot_egg_stage5
            }
            "hydra" -> when (s) {
                1 -> R.drawable.hydra_egg_stage1
                2 -> R.drawable.hydra_egg_stage2
                3 -> R.drawable.hydra_egg_stage3
                4 -> R.drawable.hydra_egg_stage4
                else -> R.drawable.hydra_egg_stage5
            }
            "unicorn" -> when (s) {
                1 -> R.drawable.unicorn_egg_stage1
                2 -> R.drawable.unicorn_egg_stage2
                3 -> R.drawable.unicorn_egg_stage3
                4 -> R.drawable.unicorn_egg_stage4
                else -> R.drawable.unicorn_egg_stage5
            }
            "kapybara" -> when (s) {
                1 -> R.drawable.kapybara_egg_stage1
                2 -> R.drawable.kapybara_egg_stage2
                3 -> R.drawable.kapybara_egg_stage3
                4 -> R.drawable.kapybara_egg_stage4
                else -> R.drawable.kapybara_egg_stage5
            }
            else -> null
        }
    }

    fun getIntegratedPetDrawable(petType: String?, growthStage: Int): Int? {
        val stage = growthStage.coerceIn(1, 5)
        return when (petType?.lowercase()) {
            "dragon" -> when (stage) {
                1 -> R.drawable.dragon_integrated_stage1
                2 -> R.drawable.dragon_integrated_stage2
                3 -> R.drawable.dragon_integrated_stage3
                4 -> R.drawable.dragon_integrated_stage4
                else -> R.drawable.dragon_integrated_stage5
            }
            "cat" -> when (stage) {
                1 -> R.drawable.cat_integrated_stage1
                2 -> R.drawable.cat_integrated_stage2
                3 -> R.drawable.cat_integrated_stage3
                4 -> R.drawable.cat_integrated_stage4
                else -> R.drawable.cat_integrated_stage5
            }
            "dog" -> when (stage) {
                1 -> R.drawable.dog_integrated_stage1
                2 -> R.drawable.dog_integrated_stage2
                3 -> R.drawable.dog_integrated_stage3
                4 -> R.drawable.dog_integrated_stage4
                else -> R.drawable.dog_integrated_stage5
            }
            "bird" -> when (stage) {
                1 -> R.drawable.bird_integrated_stage1
                2 -> R.drawable.bird_integrated_stage2
                3 -> R.drawable.bird_integrated_stage3
                4 -> R.drawable.bird_integrated_stage4
                else -> R.drawable.bird_integrated_stage5
            }
            "rabbit" -> when (stage) {
                1 -> R.drawable.rabbit_integrated_stage1
                2 -> R.drawable.rabbit_integrated_stage2
                3 -> R.drawable.rabbit_integrated_stage3
                4 -> R.drawable.rabbit_integrated_stage4
                else -> R.drawable.rabbit_integrated_stage5
            }
            "bear" -> when (stage) {
                1 -> R.drawable.bear_integrated_stage1
                2 -> R.drawable.bear_integrated_stage2
                3 -> R.drawable.bear_integrated_stage3
                4 -> R.drawable.bear_integrated_stage4
                else -> R.drawable.bear_integrated_stage5
            }
            "snake" -> when (stage) {
                1 -> R.drawable.snake_integrated_stage1
                2 -> R.drawable.snake_integrated_stage2
                3 -> R.drawable.snake_integrated_stage3
                4 -> R.drawable.snake_integrated_stage4
                else -> R.drawable.snake_integrated_stage5
            }
            "panda" -> when (stage) {
                1 -> R.drawable.panda_integrated_stage1
                2 -> R.drawable.panda_integrated_stage2
                3 -> R.drawable.panda_integrated_stage3
                4 -> R.drawable.panda_integrated_stage4
                else -> R.drawable.panda_integrated_stage5
            }
            "slot" -> when (stage) {
                1 -> R.drawable.slot_integrated_stage1
                2 -> R.drawable.slot_integrated_stage2
                3 -> R.drawable.slot_integrated_stage3
                4 -> R.drawable.slot_integrated_stage4
                else -> R.drawable.slot_integrated_stage5
            }
            "hydra" -> when (stage) {
                1 -> R.drawable.hydra_integrated_stage1
                2 -> R.drawable.hydra_integrated_stage2
                3 -> R.drawable.hydra_integrated_stage3
                4 -> R.drawable.hydra_integrated_stage4
                else -> R.drawable.hydra_integrated_stage5
            }
            "unicorn" -> when (stage) {
                1 -> R.drawable.unicorn_integrated_stage1
                2 -> R.drawable.unicorn_integrated_stage2
                3 -> R.drawable.unicorn_integrated_stage3
                4 -> R.drawable.unicorn_integrated_stage4
                else -> R.drawable.unicorn_integrated_stage5
            }
            "kapybara" -> when (stage) {
                1 -> R.drawable.kapybara_integrated_stage1
                2 -> R.drawable.kapybara_integrated_stage2
                3 -> R.drawable.kapybara_integrated_stage3
                4 -> R.drawable.kapybara_integrated_stage4
                else -> R.drawable.kapybara_integrated_stage5
            }
            else -> null
        }
    }
}

