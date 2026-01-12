/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gnii.handlandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.gnii.handlandmarker.HandLandmarkerHelper
import com.gnii.handlandmarker.MainActivity
import com.gnii.handlandmarker.MainViewModel
import com.gnii.handlandmarker.R
import com.gnii.handlandmarker.databinding.FragmentCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener {
    companion object {
        private const val TAG = "trace CameraFragment"
    }
    private var _binding: FragmentCameraBinding? = null

    private val binding get() = _binding!!

    private lateinit var helper: HandLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var analyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var facing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the HandLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (helper.isClose()) {
                helper.setup()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        if (this::helper.isInitialized) {         /// keep vars into viewModel (for persistence)
            viewModel.setGpu(helper.gpu)
            viewModel.setHands(helper.hands)
            viewModel.setAlpha(helper.alpha)
            viewModel.setScale(helper.scale)

            // Close the HandLandmarkerHelper and release resources
            backgroundExecutor.execute { helper.clear() }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding =
            FragmentCameraBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView")
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated")
        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        binding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the HandLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            helper = HandLandmarkerHelper(
                context = requireContext(),
                lsnr    = this
            )
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val future =
            ProcessCameraProvider.getInstance(requireContext())
        future.addListener(
            {
                // CameraProvider
                provider = future.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        // CameraProvider and Selector
        val pv = provider
            ?: throw IllegalStateException("Camera initialization failed.")
        val sel =
            CameraSelector.Builder().requireLensFacing(facing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder()
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        analyzer =
            ImageAnalysis.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        pv.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = pv.bindToLifecycle(
                this, sel, preview, analyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectHand(imageProxy: ImageProxy) {
        helper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = facing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(config: Configuration) {
        super.onConfigurationChanged(config)
        Log.d(TAG, "onConfigChanged")
        analyzer?.targetRotation = binding.viewFinder.display.rotation

    }

    // Update UI after hand have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onHelperCreated(opt : String) {
        Log.d(TAG, "onHelperBuilt")
        activity?.runOnUiThread {
            binding.bottomSheetLayout.inferenceTimeLabel.text = opt
        }
    }

    override fun onResults(
        resultBundle: HandLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_binding == null) return@runOnUiThread

            binding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("[%dx%d] %4dms",
                    resultBundle.height, resultBundle.width,
                    resultBundle.time)

            // Pass necessary information to OverlayView for drawing on the canvas
            binding.overlay.setResults(
                resultBundle.results.first(),
                resultBundle.height,
                resultBundle.width
            )

            // Force a redraw
            binding.overlay.invalidate()

            // Update controllers colors
            var colors = helper.calcCtrlColors(resultBundle.results.first())
            (activity as MainActivity).updateBackground(colors[0], colors[1])
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
