package com.example.balda_beta.data

import com.google.firebase.database.FirebaseDatabase

// data/WordRepository.kt
class WordRepository {

    fun fetchDictionary(callback: (Set<String>) -> Unit) {
        val db = FirebaseDatabase.getInstance().getReference("kazakh_words")
        db.get().addOnSuccessListener { snapshot ->
            val words = snapshot.children.mapNotNull { it.key?.uppercase() }.toSet()
            callback(words)
        }.addOnFailureListener {
            callback(emptySet())
        }
    }

    fun getRandomWord(dictionary: Set<String>, length: Int = 5): String? {
        return dictionary.filter { it.length == length }.randomOrNull()
    }
}
