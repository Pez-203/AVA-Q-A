package com.project.ava

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.ava.data.AppDatabase
import com.project.ava.databinding.FragmentLoadingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoadingFragment : Fragment() {
    private var _binding: FragmentLoadingBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val qrCode = arguments?.getString("qrCode") ?: return

        val funFacts = resources.getStringArray(R.array.fun_facts)
        binding.funFactText.text = funFacts.random()

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        animateSteps(qrCode)
    }

    private fun animateSteps(qrCode: String) {
        val green = ContextCompat.getColor(requireContext(), R.color.green_primary)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            binding.step1Circle.setBackgroundResource(R.drawable.bg_step_circle_done)
            binding.line1.setBackgroundColor(green)
        }, 600)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            binding.step2Circle.setBackgroundResource(R.drawable.bg_step_circle_done)
            binding.line2.setBackgroundColor(green)
        }, 1200)

        handler.postDelayed({
            if (!isAdded) return@postDelayed
            binding.step3Circle.setBackgroundResource(R.drawable.bg_step_circle_done)
            loadAndNavigate(qrCode)
        }, 1800)
    }

    private fun loadAndNavigate(qrCode: String) {
        val database = AppDatabase.getInstance(requireContext())
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                database.categoryDao().getWithQuestionsByQrCode(qrCode)
            }
            if (result != null && isAdded) {
                val bundle = bundleOf(
                    "categoryId" to result.category.id,
                    "categoryTitle" to result.category.title
                )
                findNavController().navigate(R.id.action_loading_to_questions, bundle)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        _binding = null
    }
}
