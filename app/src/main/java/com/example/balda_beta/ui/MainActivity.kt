package com.example.balda_beta.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.balda_beta.R
import com.example.balda_beta.data.WordRepository
import com.example.balda_beta.viewmodel.GameViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: GameViewModel
    private lateinit var gridLayout: GridLayout
    private val repository = WordRepository()
    private val selectedButtons = mutableListOf<Button>()
    private val buttons = Array(5) { Array<Button?>(5) { null } }

    // UI элементы
    private lateinit var timerTextView: TextView
    private lateinit var player1TextView: TextView
    private lateinit var player2TextView: TextView
    private lateinit var currentPlayerTextView: TextView

    // Кэшируем значения для производительности
    private var cellWidth = 0
    private var cellHeight = 0
    private val gridLocation = IntArray(2)

    // Константы
    private companion object {
        const val GRID_SIZE = 5
        const val CELL_SIZE = 120

        // Цветовая схема
        const val COLOR_EMPTY = Color.WHITE
        const val COLOR_CENTRAL_WORD = 0xFF4CAF50.toInt()      // Зеленый для центрального слова
        const val COLOR_PLAYER_LETTER = 0xFF2196F3.toInt()     // Синий для букв игроков
        const val COLOR_SELECTED = 0xFFFF9800.toInt()          // Оранжевый для выделенных
        const val COLOR_PLAYER_ACTIVE = 0xFFE91E63.toInt()     // Розовый для активного игрока
        const val COLOR_PLAYER_INACTIVE = 0xFF9E9E9E.toInt()   // Серый для неактивного игрока
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupObservers()
        setupBoard()
        loadDictionary()
    }

    private fun initializeViews() {
        viewModel = ViewModelProvider(this)[GameViewModel::class.java]
        gridLayout = findViewById(R.id.gridLayout)
        timerTextView = findViewById(R.id.tvTimer)
        player1TextView = findViewById(R.id.tvPlayer1Score)
        player2TextView = findViewById(R.id.tvPlayer2Score)
        currentPlayerTextView = findViewById(R.id.tvCurrentPlayer)
    }

    private fun loadDictionary() {
        repository.fetchDictionary { words ->
            viewModel.setDictionary(words)
            repository.getRandomWord(words, GRID_SIZE)?.let { startWord ->
                viewModel.placeCentralWord(startWord)
                viewModel.startTimer() // Запускаем таймер после размещения центрального слова
            }
        }
    }

    private fun setupBoard() {
        gridLayout.rowCount = GRID_SIZE
        gridLayout.columnCount = GRID_SIZE

        repeat(GRID_SIZE) { i ->
            repeat(GRID_SIZE) { j ->
                val btn = createButton(i, j)
                buttons[i][j] = btn
                gridLayout.addView(btn)
            }
        }

        gridLayout.post {
            cellWidth = gridLayout.width / GRID_SIZE
            cellHeight = gridLayout.height / GRID_SIZE
        }
    }

    private fun createButton(row: Int, col: Int): Button {
        return Button(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = CELL_SIZE
                height = CELL_SIZE
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(col)
                setMargins(2, 2, 2, 2)
            }

            setOnTouchListener(createTouchListener(row, col))
            setOnClickListener { handleButtonClick(row, col) }

            // Устанавливаем начальный цвет
            setBackgroundColor(COLOR_EMPTY)
            setTextColor(Color.BLACK)
            textSize = 16f
        }
    }

    private fun createTouchListener(row: Int, col: Int) = { v: android.view.View, event: MotionEvent ->
        val button = v as Button

        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(button, row, col)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event)
            MotionEvent.ACTION_UP -> {
                handleTouchUp()
                v.performClick()
            }
        }
        true
    }

    private fun handleTouchDown(button: Button, row: Int, col: Int) {
        resetSelection()
        if (viewModel.hasInsertedLetterThisTurn) {
            Toast.makeText(this, "Вы уже ввели букву", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCellOccupied(row, col)) {
            selectButton(button, row, col)
            viewModel.startSelection(row, col)
        }
    }

    private fun handleTouchMove(event: MotionEvent) {
        if (cellWidth == 0 || cellHeight == 0) return

        val position = getTouchPosition(event) ?: return
        val (r, c) = position

        if (isValidPosition(r, c) && isCellOccupied(r, c)) {
            buttons[r][c]?.let {
                if (!selectedButtons.contains(it)) {
                    selectButton(it, r, c)
                    viewModel.continueSelection(r, c)
                }
            }
        }
    }

    private fun getTouchPosition(event: MotionEvent): Pair<Int, Int>? {
        gridLayout.getLocationOnScreen(gridLocation)

        val x = (event.rawX.toInt() - gridLocation[0])
        val y = (event.rawY.toInt() - gridLocation[1])

        if (x < 0 || y < 0) return null

        val r = y / cellHeight
        val c = x / cellWidth

        return if (isValidPosition(r, c)) r to c else null
    }

    private fun handleTouchUp() {
        val word = viewModel.finishSelection()
        if (word.isNotEmpty()) {
            // Показываем значение слова при его принятии
            val meaning = viewModel.getWordMeaning(word)
            val message = if (viewModel.tryAddWord(word)) {
                if (meaning != null) {
                    "Принято: $word\n${meaning.meaningRu} | ${meaning.meaningKz}"
                } else {
                    "Принято: $word"
                }
            } else {
                "Недопустимое слово: $word"
            }
            showToast(message)
        }
        resetSelection()
    }

    private fun handleButtonClick(row: Int, col: Int) {
        if (!isCellOccupied(row, col)) {
            showLetterDialog(row, col)
        }
    }

    private fun showLetterDialog(row: Int, col: Int) {
        if (viewModel.isCellLocked(row, col)) {
            Toast.makeText(this, "Клетка уже занята", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Введите одну букву"
        }

        AlertDialog.Builder(this)
            .setTitle("Введите букву")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val letter = input.text.toString().uppercase()
                if (letter.length == 1) {
                    viewModel.placeLetter(row, col, letter)
                    showWordDialog()
                } else {
                    showToast("Введите одну букву")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showWordDialog() {
        val input = EditText(this).apply {
            hint = "Введите слово с использованием добавленной буквы"
        }

        AlertDialog.Builder(this)
            .setTitle("Введите слово")
            .setView(input)
            .setPositiveButton("ОК") { _, _ ->
                val word = input.text.toString().uppercase()
                val meaning = viewModel.getWordMeaning(word)
                if (!viewModel.tryAddWord(word)) {
                    showToast("Недопустимое слово")
                    viewModel.resetLastInsertedLetter()
                    viewModel.hasInsertedLetterThisTurn = false
                } else if (meaning != null) {
                    showMeaningDialog(word, meaning)
                }
            }
            .setNegativeButton("Отмена") { _, _ ->
                viewModel.resetLastInsertedLetter()
                viewModel.hasInsertedLetterThisTurn = false
            }
            .show()
    }

    private fun showMeaningDialog(word: String, meaning: GameViewModel.WordInfo) {
        AlertDialog.Builder(this)
            .setTitle("Значение слова: $word")
            .setMessage("🇷🇺 ${meaning.meaningRu}\n\n🇰🇿 ${meaning.meaningKz}")
            .setPositiveButton("ОК", null)
            .show()
    }

    private fun selectButton(btn: Button, row: Int, col: Int) {
        if (!selectedButtons.contains(btn)) {
            selectedButtons.add(btn)
            btn.setBackgroundColor(COLOR_SELECTED)
        }
    }

    private fun resetSelection() {
        selectedButtons.forEach { button ->
            // Восстанавливаем оригинальный цвет ячейки
            val position = findButtonPosition(button)
            position?.let { (row, col) ->
                updateButtonColor(button, row, col)
            }
        }
        selectedButtons.clear()
    }

    private fun findButtonPosition(targetButton: Button): Pair<Int, Int>? {
        repeat(GRID_SIZE) { i ->
            repeat(GRID_SIZE) { j ->
                if (buttons[i][j] == targetButton) {
                    return i to j
                }
            }
        }
        return null
    }

    private fun updateButtonColor(button: Button, row: Int, col: Int) {
        val cellStates = viewModel.cellStates.value ?: return
        val state = cellStates[row][col]

        val color = when (state) {
            GameViewModel.CellState.EMPTY -> COLOR_EMPTY
            GameViewModel.CellState.CENTRAL_WORD -> COLOR_CENTRAL_WORD
            GameViewModel.CellState.PLAYER_LETTER -> COLOR_PLAYER_LETTER
            GameViewModel.CellState.SELECTED -> COLOR_SELECTED
        }

        button.setBackgroundColor(color)
    }

    private fun setupObservers() {
        viewModel.board.observe(this) { board ->
            updateBoardUI(board)
        }

        viewModel.cellStates.observe(this) { states ->
            updateCellColors(states)
        }

        viewModel.selectedPositions.observe(this) { positions ->
            updateSelectedPositions(positions)
        }

        viewModel.scores.observe(this) { (p1, p2) ->
            updateScoreUI(p1, p2)
        }

        viewModel.playerTurn.observe(this) { player ->
            updateCurrentPlayerUI(player)
        }

        viewModel.timeLeft.observe(this) { time ->
            updateTimerUI(time)
        }

        viewModel.gameMessage.observe(this) { message ->
            showToast(message)
        }
    }

    private fun updateBoardUI(board: Array<Array<String>>) {
        repeat(GRID_SIZE) { i ->
            repeat(GRID_SIZE) { j ->
                buttons[i][j]?.text = board[i][j]
            }
        }
    }

    private fun updateCellColors(states: Array<Array<GameViewModel.CellState>>) {
        repeat(GRID_SIZE) { i ->
            repeat(GRID_SIZE) { j ->
                buttons[i][j]?.let { button ->
                    if (!selectedButtons.contains(button)) {
                        updateButtonColor(button, i, j)
                    }
                }
            }
        }
    }

    private fun updateSelectedPositions(positions: List<Pair<Int, Int>>) {
        // Сбрасываем предыдущие выделения
        selectedButtons.forEach { button ->
            val position = findButtonPosition(button)
            position?.let { (row, col) ->
                updateButtonColor(button, row, col)
            }
        }
        selectedButtons.clear()

        // Применяем новые выделения
        positions.forEach { (row, col) ->
            buttons[row][col]?.let { button ->
                selectedButtons.add(button)
                button.setBackgroundColor(COLOR_SELECTED)
            }
        }
    }

    private fun updateScoreUI(p1Score: Int, p2Score: Int) {
        player1TextView.text = "Игрок 1: $p1Score"
        player2TextView.text = "Игрок 2: $p2Score"
    }

    private fun updateCurrentPlayerUI(player: Int) {
        currentPlayerTextView.text = "Ходит игрок $player"

        // Подсвечиваем активного игрока
        if (player == 1) {
            player1TextView.setTextColor(COLOR_PLAYER_ACTIVE)
            player2TextView.setTextColor(COLOR_PLAYER_INACTIVE)
        } else {
            player1TextView.setTextColor(COLOR_PLAYER_INACTIVE)
            player2TextView.setTextColor(COLOR_PLAYER_ACTIVE)
        }
    }

    private fun updateTimerUI(timeLeft: Int) {
        timerTextView.text = "⏰ $timeLeft сек"

        // Меняем цвет таймера при критическом времени
        val color = when {
            timeLeft <= 10 -> Color.RED
            timeLeft <= 20 -> 0xFFFF9800.toInt() // Оранжевый
            else -> Color.BLACK
        }
        timerTextView.setTextColor(color)
    }

    // Вспомогательные методы
    private fun isValidPosition(r: Int, c: Int): Boolean =
        r in 0 until GRID_SIZE && c in 0 until GRID_SIZE

    private fun isCellOccupied(row: Int, col: Int): Boolean =
        viewModel.getBoard()[row][col].isNotEmpty()

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}