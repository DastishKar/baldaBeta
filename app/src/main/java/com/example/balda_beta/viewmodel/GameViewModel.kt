package com.example.balda_beta.viewmodel

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
    }

    fun placeLetter(row: Int, col: Int, letter: String) {
        if (hasInsertedLetterThisTurn || isCellLocked(row, col)) return
        _board.value!![row][col] = letter
        lastInsertedCell = row to col
        hasInsertedLetterThisTurn = true
        _board.value = _board.value
    }

    fun isCellLocked(row: Int, col: Int): Boolean {
        return (row to col) in lockedCells
    }

    fun tryAddWord(word: String): Boolean {
        val upperWord = word.uppercase()

        if (upperWord.length < 3) return false  // запрещаем короткие
        if (usedWords.contains(upperWord)) return false
        if (!dictionary.contains(upperWord)) return false
        if (centralWord.contains(upperWord) && !upperWord.startsWith(centralWord)) return false

        usedWords.add(upperWord)

        val current = _scores.value!!
        val updated = if (_playerTurn.value == 1)
            current.copy(first = current.first + word.length)
        else current.copy(second = current.second + word.length)
        _scores.value = updated

        // Окончание хода
        lastInsertedCell?.let { lockedCells.add(it) }
        lastInsertedCell = null
        hasInsertedLetterThisTurn = false
        _playerTurn.value = if (_playerTurn.value == 1) 2 else 1

        return true
    }

    fun resetLastInsertedLetter() {
        lastInsertedCell?.let { (row, col) ->
            _board.value!![row][col] = ""
            lastInsertedCell = null
            hasInsertedLetterThisTurn = false
            _board.value = _board.value
        }
    }

    fun placeCentralWord(word: String) {
        val row = 2
        val startCol = (5 - word.length) / 2
        centralWord = word.uppercase()

        word.uppercase().forEachIndexed { index, c ->
            _board.value!![row][startCol + index] = c.toString()
            lockedCells.add(row to (startCol + index))
        }
        _board.value = _board.value
    }

    fun startSelection(row: Int, col: Int) {
        selectedPath.clear()
        selectedPath.add(row to col)
    }

    fun continueSelection(row: Int, col: Int) {
        val last = selectedPath.lastOrNull()
        if (last != null && isAdjacent(last, row to col) && !selectedPath.contains(row to col)) {
            selectedPath.add(row to col)
        }
    }

    fun finishSelection(): String {
        val board = _board.value ?: return ""
        val word = selectedPath.map { (i, j) -> board[i][j] }.joinToString("")
        selectedPath.clear()
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

    // Не используется, можно удалить если не нужно
    fun isValidLetter(letter: String): Boolean {
        return letter.length == 1 && letter[0].isLetter()
    }
}
