package com.anantva.tether.data.model

data class AvatarOption(
    val id: String,
    val emoji: String,
    val label: String,
    val bgGradient: List<Long>
)

object AvatarCatalog {
    val defaultAvatars = listOf(
        AvatarOption("chill_cat", "😼", "Chill Cat", listOf(0xFFE53935, 0xFFB71C1C)),
        AvatarOption("cool_dog", "🐶", "Cool Dog", listOf(0xFF1976D2, 0xFF0D47A1)),
        AvatarOption("fire_fox", "🦊", "Fire Fox", listOf(0xFFE65100, 0xFFBF360C)),
        AvatarOption("space_owl", "🦉", "Space Owl", listOf(0xFF6A1B9A, 0xFF4A148C)),
        AvatarOption("zen_panda", "🐼", "Zen Panda", listOf(0xFF2E7D32, 0xFF1B5E20)),
        AvatarOption("lucky_rabbit", "🐰", "Lucky Rabbit", listOf(0xFFAD1457, 0xFF880E4F)),
        AvatarOption("storm_wolf", "🐺", "Storm Wolf", listOf(0xFF455A64, 0xFF263238)),
        AvatarOption("king_lion", "🦁", "King Lion", listOf(0xFFF9A825, 0xFFF57F17)),
        AvatarOption("ghost_panda", "👻", "Ghost", listOf(0xFF78909C, 0xFF546E7A)),
        AvatarOption("alien_cat", "👽", "Alien", listOf(0xFF00BFA5, 0xFF00897B)),
        AvatarOption("robot", "🤖", "Robot", listOf(0xFF607D8B, 0xFF37474F)),
        AvatarOption("skull", "💀", "Skull", listOf(0xFF424242, 0xFF212121)),
        AvatarOption("money_face", "🤑", "Money Face", listOf(0xFF689F38, 0xFF33691E)),
        AvatarOption("sunglasses", "😎", "Cool", listOf(0xFF0288D1, 0xFF01579B)),
        AvatarOption("devil", "😈", "Devil", listOf(0xFF7B1FA2, 0xFF4A148C)),
        AvatarOption("rocket", "🚀", "Rocket", listOf(0xFFEF5350, 0xFFC62828))
    )

    fun getAvatarById(id: String): AvatarOption {
        return defaultAvatars.find { it.id == id } ?: defaultAvatars.first()
    }
}
