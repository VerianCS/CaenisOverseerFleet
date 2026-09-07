package com.enderstorage.overseer.detector

object MinecraftMechanicsRegistry {

    // Hardness constants
    private val BLOCK_HARDNESS = mapOf(
        "OBSIDIAN" to 50.0f,
        "CRYING_OBSIDIAN" to 50.0f,
        "ANCIENT_DEBRIS" to 30.0f,
        "REINFORCED_DEEPSLATE" to 55.0f,
        "ENDER_CHEST" to 22.5f,
        "ANVIL" to 5.0f,
        "DEEPSLATE" to 3.0f,
        "DEEPSLATE_DIAMOND_ORE" to 4.5f,
        "DEEPSLATE_GOLD_ORE" to 4.5f,
        "DEEPSLATE_IRON_ORE" to 4.5f,
        "DIAMOND_ORE" to 3.0f,
        "GOLD_ORE" to 3.0f,
        "IRON_ORE" to 3.0f,
        "STONE" to 1.5f,
        "NETHERRACK" to 0.4f
    )

    private val TOOL_MULTIPLIERS = mapOf(
        "NETHERITE_PICKAXE" to 9.0f,
        "DIAMOND_PICKAXE" to 8.0f,
        "IRON_PICKAXE" to 6.0f,
        "GOLDEN_PICKAXE" to 12.0f,
        "STONE_PICKAXE" to 4.0f,
        "WOODEN_PICKAXE" to 2.0f
    )

    // Unknown or custom blocks are skipped rather than assigned a speculative threshold.
    fun getHardness(blockType: String): Float = BLOCK_HARDNESS[blockType] ?: 0f

    fun getToolMultiplier(tool: String?): Float {
        if (tool == null) return 1.0f
        return TOOL_MULTIPLIERS[tool] ?: 1.0f
    }
}
