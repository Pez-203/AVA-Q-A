package com.project.ava

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.ava.data.Question
import com.project.ava.databinding.ItemQuestionBinding

class QuestionAdapter(
    private val questions: List<Question>,
    private val onEdit: (Question) -> Unit,
    private val onDelete: (Question) -> Unit
) : RecyclerView.Adapter<QuestionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemQuestionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = questions[position]
        holder.binding.questionText.text = question.questionText
        holder.binding.answerText.text = question.answerText
        if (question.imageName != null) {
            holder.binding.imageInfoText.text =
                "IMG: ${question.imageName} (X:${question.imageOffsetX}, Y:${question.imageOffsetY})"
            holder.binding.imageInfoText.visibility = View.VISIBLE
        } else {
            holder.binding.imageInfoText.visibility = View.GONE
        }
        holder.binding.btnEdit.setOnClickListener { onEdit(question) }
        holder.binding.btnDelete.setOnClickListener { onDelete(question) }
    }

    override fun getItemCount() = questions.size
}
