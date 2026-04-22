package com.project.ava

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
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

    private fun listAssetImages(): List<String> {
        val images = mutableListOf<String>()
        try {
            val qrFiles = assets.list("qr") ?: emptyArray()
            for (f in qrFiles) {
                if (f.endsWith(".png", ignoreCase = true) || f.endsWith(".jpg", ignoreCase = true)) {
                    images.add(f)
                }
            }
        } catch (_: Exception) {}
        return images
    }

    private fun loadAssetImage(imageView: ImageView, imageName: String): Boolean {
        return try {
            val stream = assets.open("qr/$imageName")
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                true
            } else {
                imageView.visibility = View.GONE
                false
            }
        } catch (_: Exception) {
            imageView.visibility = View.GONE
            false
        }
    }

    private fun showQuestionDialog(question: Question?) {
        val view = layoutInflater.inflate(R.layout.dialog_question, null)
        val questionInput = view.findViewById<TextInputEditText>(R.id.inputQuestion)
        val answerInput = view.findViewById<TextInputEditText>(R.id.inputAnswer)
        val imageNameInput = view.findViewById<TextInputEditText>(R.id.inputImageName)
        val labelAvailable = view.findViewById<TextView>(R.id.labelAvailableImages)
        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val seekBarX = view.findViewById<SeekBar>(R.id.seekBarX)
        val seekBarY = view.findViewById<SeekBar>(R.id.seekBarY)
        val labelX = view.findViewById<TextView>(R.id.labelOffsetX)
        val labelY = view.findViewById<TextView>(R.id.labelOffsetY)

        val availableImages = listAssetImages()
        if (availableImages.isNotEmpty()) {
            labelAvailable.text = "Disponibles: ${availableImages.joinToString(", ")}"
        } else {
            labelAvailable.text = "No hay imágenes en assets/qr/"
        }

        question?.let {
            questionInput.setText(it.questionText)
            answerInput.setText(it.answerText)
            it.imageName?.let { name ->
                imageNameInput.setText(name)
                loadAssetImage(imagePreview, name)
            }
            seekBarX.progress = it.imageOffsetX + 200
            seekBarY.progress = it.imageOffsetY + 200
            labelX.text = "Posición X: ${it.imageOffsetX} dp"
            labelY.text = "Posición Y: ${it.imageOffsetY} dp"
        }

        seekBarX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                labelX.text = "Posición X: ${progress - 200} dp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBarY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                labelY.text = "Posición Y: ${progress - 200} dp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        imageNameInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = imageNameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    loadAssetImage(imagePreview, name)
                } else {
                    imagePreview.visibility = View.GONE
                }
            }
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
                val imgName = imageNameInput.text.toString().trim().ifEmpty { null }
                val offX = seekBarX.progress - 200
                val offY = seekBarY.progress - 200

                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (question == null) {
                            database.questionDao().insert(
                                Question(
                                    categoryId = categoryId,
                                    questionText = qText,
                                    answerText = aText,
                                    imageName = imgName,
                                    imageOffsetX = offX,
                                    imageOffsetY = offY
                                )
                            )
                        } else {
                            database.questionDao().update(
                                question.copy(
                                    questionText = qText,
                                    answerText = aText,
                                    imageName = imgName,
                                    imageOffsetX = offX,
                                    imageOffsetY = offY
                                )
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
