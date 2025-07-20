package com.example.balda_beta.data

import com.google.firebase.database.FirebaseDatabase
import com.example.balda_beta.viewmodel.GameViewModel

class WordRepository {

    fun fetchDictionary(callback: (Map<String, GameViewModel.WordInfo>) -> Unit) {
        val db = FirebaseDatabase.getInstance().getReference("kazakh_words")
        db.get().addOnSuccessListener { snapshot ->
            val words = mutableMapOf<String, GameViewModel.WordInfo>()

            snapshot.children.forEach { wordSnapshot ->
                val word = wordSnapshot.key?.uppercase()
                val meaningKz = wordSnapshot.child("meaning_kz").getValue(String::class.java) ?: ""
                val meaningRu = wordSnapshot.child("meaning_ru").getValue(String::class.java) ?: ""

                if (word != null && meaningKz.isNotEmpty() && meaningRu.isNotEmpty()) {
                    words[word] = GameViewModel.WordInfo(meaningKz, meaningRu)
                }
            }

            callback(words)
        }.addOnFailureListener {
            callback(emptyMap())
        }
    }

    fun getRandomWord(dictionary: Map<String, GameViewModel.WordInfo>, length: Int = 5): String? {
        return dictionary.keys.filter { it.length == length }.randomOrNull()
    }
}