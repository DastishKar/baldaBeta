package com.example.balda_beta.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GameViewModel : ViewModel() {

    private val selectedPath = mutableListOf<Pair<Int, Int>>()
    private val _board = MutableLiveData(Array(5) { Array(5) { "" } })
    val board: LiveData<Array<Array<String>>> get() = _board

    private val _scores = MutableLiveData(Pair(0, 0))
    val scores: LiveData<Pair<Int, Int>> get() = _scores

    private val _playerTurn = MutableLiveData(1)
    val playerTurn: LiveData<Int> get() = _playerTurn

    private var dictionary: Set<String> = emptySet()
    private val lockedCells = mutableSetOf<Pair<Int, Int>>()
    private val usedWords = mutableSetOf<String>()
    private var centralWord: String = ""

    var hasInsertedLetterThisTurn = false
    private var lastInsertedCell: Pair<Int, Int>? = null

    fun setDictionary(words: Set<String>) {
        dictionary = words.map { it.uppercase() }.toSet()
        Log.d("GameViewModel", "Dictionary loaded: ${dictionary.size} words")
        Log.d("GameViewModel", "Sample words: ${dictionary.take(10)}")
    }

    fun placeLetter(row: Int, col: Int, letter: String) {
        if (hasInsertedLetterThisTurn || isCellLocked(row, col)) return
        _board.value!![row][col] = letter.uppercase()
        lastInsertedCell = row to col
        hasInsertedLetterThisTurn = true
        _board.value = _board.value
    }

    fun isCellLocked(row: Int, col: Int): Boolean {
        return (row to col) in lockedCells
    }

    fun tryAddWord(word: String): Boolean {
        val upperWord = word.uppercase()

        Log.d("GameViewModel", "=== Checking word: '$upperWord' ===")
        Log.d("GameViewModel", "Word length: ${upperWord.length}")
        Log.d("GameViewModel", "Selected path: $selectedPath")
        Log.d("GameViewModel", "Has inserted letter: $hasInsertedLetterThisTurn")
        Log.d("GameViewModel", "Last inserted cell: $lastInsertedCell")

        // Проверка длины слова
        if (upperWord.length < 2) {
            Log.d("GameViewModel", "REJECTED: Word too short (< 3 letters)")
            resetLastInsertedLetter()
            return false
        }

        // Проверка на повторное использование слова
        if (usedWords.contains(upperWord)) {
            Log.d("GameViewModel", "REJECTED: Word already used")
            Log.d("GameViewModel", "Used words: $usedWords")
            resetLastInsertedLetter()
            return false
        }

        // Проверка наличия в словаре
        if (!dictionary.contains(upperWord)) {
            Log.d("GameViewModel", "REJECTED: Word not in dictionary")
            Log.d("GameViewModel", "Dictionary size: ${dictionary.size}")
            // Попробуем найти похожие слова для отладки
            val similarWords = dictionary.filter { it.contains(upperWord) || upperWord.contains(it) }
            Log.d("GameViewModel", "Similar words in dictionary: $similarWords")
            resetLastInsertedLetter()
            return false
        }

        // Проверяем, что слово не является центральным словом
        if (upperWord == centralWord) {
            Log.d("GameViewModel", "REJECTED: Word is central word ('$centralWord')")
            resetLastInsertedLetter()
            return false
        }

//         Проверяем, что слово не является подстрокой центрального слова
        if (centralWord.contains(upperWord) && !isWordExtension(upperWord)) {
            Log.d("GameViewModel", "REJECTED: Word is substring of central word ('$centralWord')")
            resetLastInsertedLetter()
            return false
        }

        // Проверяем, что слово использует добавленную букву (если она была добавлена)
        if (hasInsertedLetterThisTurn && !wordUsesInsertedLetter()) {
            Log.d("GameViewModel", "REJECTED: Word doesn't use inserted letter")
            resetLastInsertedLetter()
            return false
        }

        // Слово прошло все проверки - принимаем его
        Log.d("GameViewModel", "ACCEPTED: Word '$upperWord' is valid!")
        usedWords.add(upperWord)

        val current = _scores.value!!
        val updated = if (_playerTurn.value == 1)
            current.copy(first = current.first + upperWord.length)
        else current.copy(second = current.second + upperWord.length)
        _scores.value = updated

        // Завершение хода - закрепляем добавленную букву
        lastInsertedCell?.let { lockedCells.add(it) }
        lastInsertedCell = null
        hasInsertedLetterThisTurn = false
        _playerTurn.value = if (_playerTurn.value == 1) 2 else 1

        selectedPath.clear()
        return true
    }

    private fun isWordExtension(word: String): Boolean {
        return word.length > centralWord.length &&
                (word.startsWith(centralWord) || word.endsWith(centralWord) || word.contains(centralWord))
    }

    private fun wordUsesInsertedLetter(): Boolean {
        if (lastInsertedCell == null) return true
        val (row, col) = lastInsertedCell!!
        val usesInserted = selectedPath.contains(row to col)
        Log.d("GameViewModel", "Checking if word uses inserted letter at ($row, $col): $usesInserted")
        return usesInserted
    }

    fun resetLastInsertedLetter() {
        lastInsertedCell?.let { (row, col) ->
            _board.value!![row][col] = ""
            _board.value = _board.value
            Log.d("GameViewModel", "Reset inserted letter at ($row, $col)")
        }
        lastInsertedCell = null
        hasInsertedLetterThisTurn = false
        selectedPath.clear()
    }

    fun placeCentralWord(word: String) {
        val row = 2
        val startCol = (5 - word.length) / 2
        centralWord = word.uppercase()

        Log.d("GameViewModel", "Placing central word: '$centralWord'")

        word.uppercase().forEachIndexed { index, c ->
            _board.value!![row][startCol + index] = c.toString()
            lockedCells.add(row to (startCol + index))
        }
        _board.value = _board.value
    }

    fun startSelection(row: Int, col: Int) {
        selectedPath.clear()
        selectedPath.add(row to col)
        Log.d("GameViewModel", "Started selection at ($row, $col)")
    }

    fun continueSelection(row: Int, col: Int) {
        val last = selectedPath.lastOrNull()
        if (last != null && isAdjacent(last, row to col) && !selectedPath.contains(row to col)) {
            selectedPath.add(row to col)
            Log.d("GameViewModel", "Continued selection to ($row, $col), path: $selectedPath")
        }
    }

    fun finishSelection(): String {
        val board = _board.value ?: return ""
        val word = selectedPath.map { (i, j) -> board[i][j] }.joinToString("")
        Log.d("GameViewModel", "Finished selection, word: '$word', path: $selectedPath")
        return word
    }

    private fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        val dx = kotlin.math.abs(a.first - b.first)
        val dy = kotlin.math.abs(a.second - b.second)
        return (dx + dy == 1)
    }

    fun getBoard(): Array<Array<String>> {
        return board.value ?: Array(5) { Array(5) { "" } }
    }

    fun isValidLetter(letter: String): Boolean {
        return letter.length == 1 && letter[0].isLetter()
    }

    // Дополнительные методы для отладки
    fun getSelectedPath(): List<Pair<Int, Int>> = selectedPath.toList()
    fun getDictionarySize(): Int = dictionary.size
    fun getCentralWord(): String = centralWord
}