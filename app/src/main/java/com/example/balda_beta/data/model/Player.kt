package com.example.balda_beta.data.model

// data/model/Player.kt
data class Player(
    val name: String,
    var score: Int = 0,
    val words: MutableSet<String> = mutableSetOf()
)

