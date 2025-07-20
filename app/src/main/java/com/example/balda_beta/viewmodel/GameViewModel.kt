package com.example.balda_beta.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _board = MutableLiveData(Array(5) { Array(5) { "" } })
    val board: LiveData<Array<Array<String>>> = _board

    private val _playerTurn = MutableLiveData(1)
    val playerTurn: LiveData<Int> = _playerTurn

    private val _scores = MutableLiveData(Pair(0, 0))
    val scores: LiveData<Pair<Int, Int>> = _scores

    private val _gameMessage = MutableLiveData<String>()
    val gameMessage: LiveData<String> = _gameMessage

    private val _timeLeft = MutableLiveData(50)
    val timeLeft: LiveData<Int> = _timeLeft

    private val _isTimeUp = MutableLiveData(false)
    val isTimeUp: LiveData<Boolean> = _isTimeUp

    // Цветовые состояния ячеек
    private val _cellStates = MutableLiveData(Array(5) { Array(5) { CellState.EMPTY } })
    val cellStates: LiveData<Array<Array<CellState>>> = _cellStates

    // Выбранные позиции для подсветки
    private val _selectedPositions = MutableLiveData<List<Pair<Int, Int>>>(emptyList())
    val selectedPositions: LiveData<List<Pair<Int, Int>>> = _selectedPositions

    private val usedWords = mutableSetOf<String>()
    private lateinit var dictionary: Map<String, WordInfo>
    private val selectedPath = mutableListOf<Pair<Int, Int>>()
    private var timerJob: Job? = null

    var hasInsertedLetterThisTurn = false
    private var lastInsertedPosition: Pair<Int, Int>? = null

    companion object {
        const val GRID_SIZE = 5
        const val MIN_WORD_LENGTH = 3
        const val TURN_TIME_SECONDS = 50
    }

    enum class CellState {
        EMPTY,           // Пустая ячейка
        CENTRAL_WORD,    // Буквы центрального слова
        PLAYER_LETTER,   // Буквы, добавленные игроками
        SELECTED         // Выделенные ячейки при составлении слова
    }

    data class WordInfo(
        val meaningKz: String,
        val meaningRu: String
    )

    fun setDictionary(words: Map<String, WordInfo>) {
        dictionary = words
    }

    fun placeLetter(row: Int, col: Int, letter: String): Boolean {
        if (hasInsertedLetterThisTurn) return false

        val current = _board.value ?: return false
        val currentStates = _cellStates.value ?: return false

        if (current[row][col].isNotEmpty()) return false

        current[row][col] = letter
        currentStates[row][col] = CellState.PLAYER_LETTER
        lastInsertedPosition = row to col
        hasInsertedLetterThisTurn = true

        _board.value = current
        _cellStates.value = currentStates
        return true
    }

    fun resetLastInsertedLetter() {
        lastInsertedPosition?.let { (row, col) ->
            val current = _board.value ?: return
            val currentStates = _cellStates.value ?: return

            current[row][col] = ""
            currentStates[row][col] = CellState.EMPTY

            _board.value = current
            _cellStates.value = currentStates
            lastInsertedPosition = null
        }
    }

    fun tryAddWord(word: String): Boolean {
        val upperWord = word.uppercase()
        if (upperWord.length < MIN_WORD_LENGTH || upperWord !in dictionary.keys || upperWord in usedWords) {
            _gameMessage.value = "Недопустимое слово: $word"
            return false
        }

        usedWords.add(upperWord)
        val (p1, p2) = _scores.value ?: Pair(0, 0)
        val wordScore = if (_isTimeUp.value == true) 0 else word.length
        val newScores = if (_playerTurn.value == 1) Pair(p1 + wordScore, p2) else Pair(p1, p2 + wordScore)
        _scores.value = newScores

        val meaning = dictionary[upperWord]
        val messageText = if (wordScore == 0) {
            "Время вышло! Слово: $word (0 очков)"
        } else {
            "Принято: $word (+$wordScore очков)"
        }
        _gameMessage.value = messageText

        nextPlayer()
        return true
    }

    fun placeCentralWord(word: String) {
        val mid = 2
        val start = (GRID_SIZE - word.length) / 2
        val current = _board.value ?: return
        val currentStates = _cellStates.value ?: return

        for (i in word.indices) {
            current[mid][start + i] = word[i].toString()
            currentStates[mid][start + i] = CellState.CENTRAL_WORD
        }
        _board.value = current
        _cellStates.value = currentStates
    }

    fun startSelection(row: Int, col: Int): Boolean {
        selectedPath.clear()
        return if (isCellOccupied(row, col)) {
            selectedPath.add(row to col)
            updateSelectedPositions()
            true
        } else false
    }

    fun continueSelection(row: Int, col: Int): Boolean {
        val position = row to col

        if (position in selectedPath) return false

        return if (canSelectPosition(position)) {
            selectedPath.add(position)
            updateSelectedPositions()
            true
        } else false
    }

    fun finishSelection(): String {
        val word = buildSelectedWord()
        selectedPath.clear()
        updateSelectedPositions()
        return word
    }

    fun resetSelection() {
        selectedPath.clear()
        updateSelectedPositions()
    }

    private fun updateSelectedPositions() {
        _selectedPositions.value = selectedPath.toList()
    }

    fun getWordMeaning(word: String): WordInfo? {
        return dictionary[word.uppercase()]
    }

    fun isCellLocked(row: Int, col: Int): Boolean {
        return isCellOccupied(row, col)
    }

    fun isCellOccupied(row: Int, col: Int): Boolean {
        val board = _board.value ?: return false
        return isValidPosition(row, col) && board[row][col].isNotEmpty()
    }

    fun isValidPosition(row: Int, col: Int): Boolean =
        row in 0 until GRID_SIZE && col in 0 until GRID_SIZE

    private fun canSelectPosition(position: Pair<Int, Int>): Boolean {
        val (row, col) = position
        return position !in selectedPath &&
                isCellOccupied(row, col) &&
                (selectedPath.isEmpty() || isAdjacent(selectedPath.last(), position))
    }

    private fun buildSelectedWord(): String {
        val board = _board.value ?: return ""
        return selectedPath.joinToString("") { (row, col) ->
            board[row][col]
        }
    }

    private fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        val dx = kotlin.math.abs(a.first - b.first)
        val dy = kotlin.math.abs(a.second - b.second)
        return (dx + dy == 1) && (dx <= 1 && dy <= 1)
    }

    fun startTimer() {
        stopTimer()
        _timeLeft.value = TURN_TIME_SECONDS
        _isTimeUp.value = false

        timerJob = viewModelScope.launch {
            for (i in TURN_TIME_SECONDS downTo 1) {
                _timeLeft.value = i
                delay(1000)
            }
            _timeLeft.value = 0
            _isTimeUp.value = true
            _gameMessage.value = "Время вышло!"
            // Автоматически передаем ход следующему игроку через 2 секунды
            delay(2000)
            nextPlayer()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun nextPlayer() {
        _playerTurn.value = if (_playerTurn.value == 1) 2 else 1
        hasInsertedLetterThisTurn = false
        lastInsertedPosition = null
        startTimer()
    }

    fun getBoard(): Array<Array<String>> {
        return board.value ?: Array(GRID_SIZE) { Array(GRID_SIZE) { "" } }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}