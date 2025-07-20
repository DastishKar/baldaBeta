package com.example.balda_beta.data

// data/WordLoader.kt

import android.content.Context
import com.google.firebase.database.FirebaseDatabase

object WordLoader {
    // Загрузка слов из Firebase
    fun loadFromFirebase(onResult: (Set<String>) -> Unit, onError: (() -> Unit)? = null) {
        val db = FirebaseDatabase.getInstance().getReference("kazakh_words")
        db.get().addOnSuccessListener { snapshot ->
            val words = snapshot.children.mapNotNull { it.key?.uppercase() }.toSet()
            onResult(words)
        }.addOnFailureListener {
            onError?.invoke()
        }
    }

    // Альтернатива: загрузка из файла assets (если Firebase недоступен)
    fun loadFromAssets(context: Context, fileName: String = "words.txt"): Set<String> {
        return context.assets.open(fileName)
            .bufferedReader()
            .readLines()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
