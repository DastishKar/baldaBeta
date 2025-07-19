package com.example.balda_beta.ui

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

    // Кэшируем значения для производительности
    private var cellWidth = 0
    private var cellHeight = 0
    private val gridLocation = IntArray(2)

    // Константы
    private companion object {
        const val GRID_SIZE = 5
        const val CELL_SIZE = 120
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
    }

    private fun loadDictionary() {
        repository.fetchDictionary { words ->
            viewModel.setDictionary(words)
            repository.getRandomWord(words, GRID_SIZE)?.let { startWord ->
                viewModel.placeCentralWord(startWord)
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

        // Вычисляем размеры ячеек после создания сетки
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
            }

            setOnTouchListener(createTouchListener(row, col))
            setOnClickListener { handleButtonClick(row, col) }
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
        if (viewModel.hasInsertedLetterThisTurn){
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
                selectButton(it, r, c)
                viewModel.continueSelection(r, c)
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
            val message = if (viewModel.tryAddWord(word)) {
                "Принято: $word"
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

        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Введите букву")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val letter = input.text.toString().uppercase()
                if (letter.length == 1) {
                    viewModel.placeLetter(row, col, letter)
                    showWordDialog()
                }
            }.show()
    }
    private fun showWordDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Введите слово")
            .setView(input)
            .setPositiveButton("ОК") { _, _ ->
                val word = input.text.toString().uppercase()
                if (!viewModel.tryAddWord(word)) {
                    Toast.makeText(this, "Недопустимое слово", Toast.LENGTH_SHORT).show()

                    // ❗ Возврат клетки — откат последней буквы
                    viewModel.resetLastInsertedLetter()

                    // Сброс флага — игрок может попробовать снова
                    viewModel.hasInsertedLetterThisTurn = false
                }
            }
            .show()
    }



    private fun selectButton(btn: Button, row: Int, col: Int) {
        if (!selectedButtons.contains(btn)) {
            selectedButtons.add(btn)
            btn.setBackgroundColor(getColorCompat(R.color.teal_200))
        }
    }

    private fun resetSelection() {
        selectedButtons.forEach {
            it.setBackgroundColor(getColorCompat(android.R.color.transparent))
        }
        selectedButtons.clear()
    }

    private fun setupObservers() {
        viewModel.board.observe(this) { board ->
            updateBoardUI(board)
        }

        viewModel.scores.observe(this) { (p1, p2) ->
            updateScoreUI(p1, p2)
        }

        viewModel.playerTurn.observe(this) { player ->
            updateCurrentPlayerUI(player)
        }
    }

    private fun updateBoardUI(board: Array<Array<String>>) {
        repeat(GRID_SIZE) { i ->
            repeat(GRID_SIZE) { j ->
                buttons[i][j]?.text = board[i][j]
            }
        }
    }

    private fun updateScoreUI(p1Score: Int, p2Score: Int) {
        findViewById<TextView>(R.id.tvPlayer1Score).text = "Игрок 1: $p1Score"
        findViewById<TextView>(R.id.tvPlayer2Score).text = "Игрок 2: $p2Score"
    }

    private fun updateCurrentPlayerUI(player: Int) {
        findViewById<TextView>(R.id.tvCurrentPlayer).text = "Ходит игрок $player"
    }

    // Вспомогательные методы
    private fun isValidPosition(r: Int, c: Int): Boolean =
        r in 0 until GRID_SIZE && c in 0 until GRID_SIZE

    private fun isCellOccupied(row: Int, col: Int): Boolean =
        viewModel.getBoard()[row][col].isNotEmpty()

    private fun isValidLetter(letter: String): Boolean =
        letter.length == 1 && letter.all { it.isLetter() }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getColorCompat(colorRes: Int): Int =
        ContextCompat.getColor(this, colorRes)
}