package at.hannibal2.skyhanni.features.dungeon

enum class DungeonFloor(val bossName: String) {
    E("The Watcher"),
    F1("Bonzo"),
    F2("Scarf"),
    F3("The Professor"),
    F4("Thorn"),
    F5("Livid"),
    F6("Sadan"),
    F7("Necron");

    companion object {

        fun byBossName(bossName: String): DungeonFloor? {
            val clean = bossName.trim().lowercase()
                .removePrefix("the ")
                .removeSuffix(" collection")
                .trim()
            return entries.firstOrNull { floor ->
                val floorBoss = floor.bossName.lowercase().removePrefix("the ").trim()
                floor.name.lowercase() == clean ||
                    floor.name.lowercase() == clean.replace("m", "f") ||
                    floor.bossName.lowercase() == clean ||
                    floorBoss == clean
            }
        }
    }
}
