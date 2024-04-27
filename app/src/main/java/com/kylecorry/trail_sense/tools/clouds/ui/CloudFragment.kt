package com.kylecorry.trail_sense.tools.clouds.ui

import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.CoroutineAlerts
import com.kylecorry.andromeda.core.coroutines.BackgroundMinimumState
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentCloudsBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.io.FileSubsystem
import com.kylecorry.trail_sense.shared.io.FragmentUriPicker
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.tools.clouds.domain.classification.SoftmaxCloudClassifier
import com.kylecorry.trail_sense.tools.clouds.infrastructure.CloudDetailsService
import com.kylecorry.trail_sense.tools.clouds.infrastructure.persistence.CloudObservation
import com.kylecorry.trail_sense.tools.clouds.infrastructure.persistence.CloudRepo
import com.kylecorry.trail_sense.tools.guide.infrastructure.UserGuideUtils
import java.time.Duration
import java.time.Instant

class CloudFragment : BoundFragment<FragmentCloudsBinding>() {

    private val mapper by lazy { CloudReadingListItemMapper(requireContext(), this::handleAction) }
    private val repo by lazy { CloudRepo.getInstance(requireContext()) }
    private val cloudDetailsService by lazy { CloudDetailsService(requireContext()) }
    private val formatter by lazy { FormatService.getInstance(requireContext()) }
    private val files by lazy { FileSubsystem.getInstance(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observe(repo.getAllLive()) {
            val since = Instant.now().minus(Duration.ofHours(48))
            binding.cloudList.setItems(it.sortedByDescending { cloud -> cloud.time }
                .filter { cloud -> cloud.time >= since }, mapper)
        }

        binding.cloudListTitle.rightButton.setOnClickListener {
            UserGuideUtils.showGuide(this, R.raw.guide_tool_clouds)
        }

        binding.cloudListTitle.subtitle.text = getString(
            R.string.last_duration, formatter.formatDuration(
                Duration.ofHours(48),
                short = true
            )
        )

        binding.cloudList.emptyView = binding.cloudEmptyText
        setupCreateMenu()

        val shouldOpenScanner = requireArguments().getBoolean("open_scanner", false)
        effect("open_scanner", shouldOpenScanner) {
            if (shouldOpenScanner) {
                addFromCamera()
            }
        }
    }

    private fun setupCreateMenu() {
        binding.addMenu.setOverlay(binding.overlayMask)
        binding.addMenu.fab = binding.addBtn
        binding.addMenu.hideOnMenuOptionSelected = true
        binding.addMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_cloud_camera -> addFromCamera()
                R.id.action_cloud_file -> addFromFile()
                R.id.action_cloud_manual -> addManual()
            }
            true
        }
    }

    private fun addFromCamera() {
        requestCamera {
            if (it) {
                inBackground {
                    val uri = CustomUiUtils.takePhoto(
                        this@CloudFragment,
                        Size(SoftmaxCloudClassifier.IMAGE_SIZE, SoftmaxCloudClassifier.IMAGE_SIZE)
                    )
                    uri?.let {
                        findNavController().navigate(
                            R.id.action_cloud_to_cloud_picker,
                            bundleOf("image" to uri)
                        )
                    }
                }
            } else {
                alertNoCameraPermission()
            }
        }
    }

    private fun addFromFile() {
        inBackground(BackgroundMinimumState.Created) {
            val uri =
                FragmentUriPicker(this@CloudFragment).open(listOf("image/*"))
            val temp = uri?.let { onIO { files.copyToTemp(it) }?.toUri() }
            temp?.let {
                findNavController().navigate(
                    R.id.action_cloud_to_cloud_picker,
                    bundleOf("image" to it)
                )
            }
        }
    }

    private fun addManual() {
        findNavController().navigate(R.id.action_cloud_to_cloud_picker)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCloudsBinding {
        return FragmentCloudsBinding.inflate(layoutInflater, container, false)
    }

    private fun handleAction(action: CloudReadingAction, reading: Reading<CloudObservation>) {
        when (action) {
            CloudReadingAction.Delete -> delete(reading)
        }
    }

    private fun delete(reading: Reading<CloudObservation>) {
        inBackground {
            val cancelled = onMain {
                CoroutineAlerts.dialog(
                    requireContext(),
                    getString(R.string.delete),
                    cloudDetailsService.getCloudName(reading.value.genus)
                )
            }
            if (!cancelled) {
                repo.delete(reading)
            }
        }
    }

}