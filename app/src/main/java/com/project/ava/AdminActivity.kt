package com.project.ava

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.project.ava.data.AppDatabase
import com.project.ava.data.Category
import com.project.ava.databinding.ActivityAdminBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private lateinit var database: AppDatabase
    private lateinit var adapter: CategoryAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val categories = mutableListOf<Category>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = CategoryAdapter(
            categories,
            onEdit = { showCategoryDialog(it) },
            onDelete = { deleteCategory(it) },
            onSelect = { openQuestions(it) }
        )
        binding.categoriesRecycler.layoutManager = LinearLayoutManager(this)
        binding.categoriesRecycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showCategoryDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    private fun loadCategories() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { database.categoryDao().getAll() }
            categories.clear()
            categories.addAll(list)
            adapter.notifyDataSetChanged()

            binding.emptyText.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
            binding.categoriesRecycler.visibility =
                if (categories.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showCategoryDialog(category: Category?) {
        val view = layoutInflater.inflate(R.layout.dialog_category, null)
        val titleInput = view.findViewById<TextInputEditText>(R.id.inputTitle)
        val qrCodeInput = view.findViewById<TextInputEditText>(R.id.inputQrCode)

        category?.let {
            titleInput.setText(it.title)
            qrCodeInput.setText(it.qrCode)
        }

        AlertDialog.Builder(this)
            .setTitle(if (category == null) "Nueva Categoría" else "Editar Categoría")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val title = titleInput.text.toString().trim()
                val qrCode = qrCodeInput.text.toString().trim()
                if (title.isEmpty() || qrCode.isEmpty()) {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (category == null) {
                                database.categoryDao()
                                    .insert(Category(title = title, qrCode = qrCode))
                            } else {
                                database.categoryDao()
                                    .update(category.copy(title = title, qrCode = qrCode))
                            }
                        }
                        loadCategories()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@AdminActivity,
                            "Error: el código QR ya existe",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteCategory(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar '${category.title}' y todas sus preguntas?")
            .setPositiveButton("Eliminar") { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { database.categoryDao().delete(category) }
                    loadCategories()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openQuestions(category: Category) {
        val intent = Intent(this, AdminQuestionsActivity::class.java)
        intent.putExtra("categoryId", category.id)
        intent.putExtra("categoryTitle", category.title)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
