package com.project.ava

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.ava.data.AppDatabase
import com.project.ava.data.Question
import com.project.ava.databinding.FragmentQuestionsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestionsFragment : Fragment() {
    private var _binding: FragmentQuestionsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var categoryId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryId = arguments?.getLong("categoryId") ?: return

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }

        binding.btnOtherQuestions.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        loadQuestions(categoryId)
    }

    private fun loadQuestions(categoryId: Long) {
        val database = AppDatabase.getInstance(requireContext())
        scope.launch {
            val questions = withContext(Dispatchers.IO) {
                database.questionDao().getByCategoryId(categoryId)
            }
            displayQuestions(questions)
        }
    }

    private fun displayQuestions(questions: List<Question>) {
        val container = binding.questionsContainer
        container.removeAllViews()

        for (question in questions) {
            val itemView = layoutInflater.inflate(R.layout.item_question_row, container, false)
            itemView.findViewById<TextView>(R.id.questionText).text = question.questionText

            itemView.setOnClickListener {
                val bundle = bundleOf(
                    "questionText" to question.questionText,
                    "answerText" to question.answerText,
                    "categoryId" to categoryId
                )
                findNavController().navigate(R.id.action_questions_to_chat, bundle)
            }

            container.addView(itemView)
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_message))
            .setPositiveButton(getString(R.string.btn_accept), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}
