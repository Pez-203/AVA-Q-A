package com.project.ava

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.ava.data.AppDatabase
import com.project.ava.data.Question
import com.project.ava.databinding.FragmentChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isPanelExpanded = false
    private var isFirstQuestion = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val questionText = arguments?.getString("questionText") ?: return
        val answerText = arguments?.getString("answerText") ?: return
        val categoryId = arguments?.getLong("categoryId") ?: -1L

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }

        binding.panelToggle.setOnClickListener {
            togglePanel()
        }

        binding.avaCard.clipToOutline = true
        startCamera()
        loadQuestionsForPanel(categoryId)
        showInitialConversation(questionText, answerText)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            if (!isAdded) return@addListener
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun showInitialConversation(question: String, answer: String) {
        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(question, isUser = true)
        }, 300)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(answer, isUser = false)
        }, 900)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(getString(R.string.another_question_user), isUser = true)
        }, 1800)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(getString(R.string.ava_help_response), isUser = false)
            scrollToBottom()
            isFirstQuestion = false
        }, 2500)
    }

    private fun handleQuestionFromPanel(question: Question) {
        collapsePanel()
        addBubble(question.questionText, isUser = true)
        scrollToBottom()

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(question.answerText, isUser = false)
            scrollToBottom()
        }, 700)
    }

    private fun loadQuestionsForPanel(categoryId: Long) {
        if (categoryId == -1L) return
        val database = AppDatabase.getInstance(requireContext())
        scope.launch {
            val questions = withContext(Dispatchers.IO) {
                database.questionDao().getByCategoryId(categoryId)
            }
            populatePanel(questions)
        }
    }

    private fun populatePanel(questions: List<Question>) {
        val container = binding.panelQuestionsContainer
        container.removeAllViews()

        for (question in questions) {
            val itemView = layoutInflater.inflate(R.layout.item_question_row, container, false)
            itemView.findViewById<TextView>(R.id.questionText).text = question.questionText
            itemView.setOnClickListener {
                handleQuestionFromPanel(question)
            }
            container.addView(itemView)
        }
    }

    private fun togglePanel() {
        isPanelExpanded = !isPanelExpanded
        binding.panelQuestionsScroll.visibility = if (isPanelExpanded) View.VISIBLE else View.GONE

        val rotation = if (isPanelExpanded) 180f else 0f
        ObjectAnimator.ofFloat(binding.panelChevron, "rotation", rotation).apply {
            duration = 200
            start()
        }
    }

    private fun collapsePanel() {
        if (isPanelExpanded) {
            isPanelExpanded = false
            binding.panelQuestionsScroll.visibility = View.GONE
            ObjectAnimator.ofFloat(binding.panelChevron, "rotation", 0f).apply {
                duration = 200
                start()
            }
        }
    }

    private fun addBubble(text: String, isUser: Boolean) {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setPadding(32, 20, 32, 20)
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()

            if (isUser) {
                setBackgroundResource(R.drawable.bg_bubble_user)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                setBackgroundResource(R.drawable.bg_bubble_ava)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text))
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            bottomMargin = 14
        }

        binding.chatContainer.addView(bubble, params)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.chatScroll.post {
            binding.chatScroll.fullScroll(View.FOCUS_DOWN)
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
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        _binding = null
    }
}
