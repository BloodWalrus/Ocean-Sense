package com.kylecorry.trail_sense.tools.mirror.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.torch.ScreenTorch
import com.kylecorry.trail_sense.databinding.FragmentToolMirrorCameraBinding
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera

class ToolMirrorCameraFragment : BoundFragment<FragmentToolMirrorCameraBinding>() {
    private val flashlight by lazy { ScreenTorch(requireActivity().window) }
    private var isCameraEnabled = true

    override fun generateBinding(
        layoutInflater: LayoutInflater, container: ViewGroup?
    ): FragmentToolMirrorCameraBinding {
        return FragmentToolMirrorCameraBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.camera.setScaleType(PreviewView.ScaleType.FIT_CENTER)
        binding.camera.setShowTorch(false)
        binding.camera.setPreviewBackgroundColor(Color.WHITE)
    }

    override fun onResume() {
        super.onResume()
        startCamera()
        flashlight.on()
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
        flashlight.off()
    }

    private fun startCamera() {
        if (!isCameraEnabled){
            return
        }
        requestCamera {
            if (it) {
                binding.camera.start(
                    readFrames = false,
                    preferBackCamera = false,
                    shouldStabilizePreview = false
                )
            } else {
                isCameraEnabled = false
                alertNoCameraPermission()
            }
        }
    }

    private fun stopCamera() {
        binding.camera.stop()
    }
}