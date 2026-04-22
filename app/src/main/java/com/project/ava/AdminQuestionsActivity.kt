package com.project.ava

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.project.ava.data.AppDatabase
import com.project.ava.data.Question
import com.project.ava.databinding.ActivityAdminQuestionsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminQuestionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminQuestionsBinding
    private lateinit var database: AppDatabase
    private lateinit var adapter: QuestionAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val questions = mutableListOf<Question>()
    private var categoryId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminQuestionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryId = intent.getLongExtra("categoryId", 0)
        val categoryTitle = intent.getStringExtra("categoryTitle") ?: ""

        database = AppDatabase.getInstance(this)

        binding.toolbar.title = "Preguntas: $categoryTitle"
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = QuestionAdapter(
            questions,
            onEdit = { showQuestionDialog(it) },
            onDelete = { deleteQuestion(it) }
        )
        binding.questionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.questionsRecycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showQuestionDialog(null) }

        loadQuestions()
    }

    private fun loadQuestions() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                database.questionDao().getByCategoryId(categoryId)
            }
            questions.clear()
            questions.addAll(list)
            adapter.notifyDataSetChanged()

            binding.emptyText.visibility = if (questions.isEmpty()) View.VISIBLE else View.GONE
            binding.questionsRecycler.visibility =
                if (questions.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showQuestionDialog(question: Question?) {
        val view = layoutInflater.inflate(R.layout.dialog_question, null)
        val questionInput = view.findViewById<TextInputEditText>(R.id.inputQuestion)
        val answerInput = view.findViewById<TextInputEditText>(R.id.inputAnswer)

        question?.let {
            questionInput.setText(it.questionText)
            answerInput.setText(it.answerText)
        }

        AlertDialog.Builder(this)
            .setTitle(if (question == null) "Nueva Pregunta" else "Editar Pregunta")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val qText = questionInput.text.toString().trim()
                val aText = answerInput.text.toString().trim()
                if (qText.isEmpty() || aText.isEmpty()) {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (question == null) {
                            database.questionDao().insert(
                                Question(
                                    categoryId = categoryId,
                                    questionText = qText,
                                    answerText = aText
                                )
                            )
                        } else {
                            database.questionDao().update(
                                question.copy(questionText = qText, answerText = aText)
                            )
                        }
                    }
                    loadQuestions()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteQuestion(question: Question) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar esta pregunta?")
            .setPositiveButton("Eliminar") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { database.questionDao().delete(question) }
                    loadQuestions()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
