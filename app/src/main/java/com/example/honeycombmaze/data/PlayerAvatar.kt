package com.example.honeycombmaze.data

import androidx.compose.ui.graphics.Color

data class PlayerAvatar(
    val id: String,
    val name: String,
    val emoji: String,
    val cost: Int,
    val coreColor: Color = Color(0xFF5B8DEF)
)

object AvatarRegistry {
    val AVATARS = listOf(
        PlayerAvatar("default", "DEFAULT", "🔵", 0, Color(0xFF5B8DEF)),
        PlayerAvatar("zombie", "Zombie", "🧟", 5),
        PlayerAvatar("frog", "Frog", "🐸", 10),
        PlayerAvatar("robot", "Robot", "🤖", 15),
        PlayerAvatar("clown", "Clown", "🤡", 20),
        PlayerAvatar("thinking", "Thinking", "🤔", 25),
        PlayerAvatar("imp", "Devil Imp", "😈", 30),
        PlayerAvatar("unicorn", "Unicorn", "🦄", 35),
        PlayerAvatar("pig", "Pig", "🐷", 40),
        PlayerAvatar("alien", "Alien", "👽", 45),
        PlayerAvatar("moon", "Full Moon", "🌝", 50),
        PlayerAvatar("ghost", "Ghost", "👻", 55),
        PlayerAvatar("santa", "Santa", "🎅", 60),
        PlayerAvatar("invader", "Invader", "👾", 65),
        PlayerAvatar("happy", "Happy", "😌", 70),
        PlayerAvatar("soccer", "Soccer", "⚽", 75),
        PlayerAvatar("capman", "Man Cap", "👲", 80),
        PlayerAvatar("rocket", "Rocket", "🚀", 85),
        PlayerAvatar("man", "Man", "👨🏽", 90),
        PlayerAvatar("demon", "Demon", "👹", 95),
        PlayerAvatar("cat", "Cat", "🐱", 100),
        PlayerAvatar("dog", "Dog", "🐶", 110),
        PlayerAvatar("heart", "Heart", "❤️", 120),
        PlayerAvatar("poop", "Poop", "💩", 130),
        PlayerAvatar("cool", "Cool", "😎", 150)
    )

    fun getAvatar(id: String): PlayerAvatar {
        return AVATARS.find { it.id == id } ?: AVATARS[0]
    }
}
