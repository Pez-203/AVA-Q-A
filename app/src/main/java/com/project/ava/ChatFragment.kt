package com.project.ava

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.ava.databinding.FragmentChatBinding

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

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

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnQuestions.setOnClickListener {
            findNavController().popBackStack()
        }

        showChatBubbles(questionText, answerText)
    }

    private fun showChatBubbles(question: String, answer: String) {
        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(question, isUser = true)
        }, 300)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(answer, isUser = false)
        }, 800)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(getString(R.string.ava_another_question), isUser = false)
        }, 1500)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            addBubble(getString(R.string.ava_help_response), isUser = false)
            binding.chatScroll.post {
                binding.chatScroll.fullScroll(View.FOCUS_DOWN)
            }
        }, 2200)
    }

    private fun addBubble(text: String, isUser: Boolean) {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setPadding(32, 24, 32, 24)
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()

            if (isUser) {
                setBackgroundResource(R.drawable.bg_bubble_user)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.green_primary))
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
            bottomMargin = 16
        }

        binding.chatContainer.addView(bubble, params)
        binding.chatScroll.post {
            binding.chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
