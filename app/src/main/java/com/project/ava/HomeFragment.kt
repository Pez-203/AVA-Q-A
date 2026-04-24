package com.project.ava

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project.ava.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            findNavController().navigate(R.id.action_home_to_scanner)
        } else {
            showDeniedState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            requireActivity().finish()
        }

        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }

        binding.btnScan.setOnClickListener {
            handleScanClick()
        }

        binding.btnDeny.setOnClickListener {
            binding.permissionOverlay.visibility = View.GONE
            showDeniedState()
        }

        binding.btnAllow.setOnClickListener {
            binding.permissionOverlay.visibility = View.GONE
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.permissionOverlay.setOnClickListener {
            binding.permissionOverlay.visibility = View.GONE
        }
    }

    private fun handleScanClick() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            findNavController().navigate(R.id.action_home_to_scanner)
        } else {
            showWelcomeState()
            binding.permissionOverlay.visibility = View.VISIBLE
        }
    }

    private fun showDeniedState() {
        binding.avaImage.visibility = View.GONE
        binding.welcomeBubble.visibility = View.GONE
        binding.avaSadImage.visibility = View.VISIBLE
        binding.deniedBubble.visibility = View.VISIBLE
    }

    private fun showWelcomeState() {
        binding.avaSadImage.visibility = View.GONE
        binding.deniedBubble.visibility = View.GONE
        binding.avaImage.visibility = View.VISIBLE
        binding.welcomeBubble.visibility = View.VISIBLE
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
        _binding = null
    }
}
