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

package com.google.mediapipe.examples.handlandmarker

import androidx.lifecycle.ViewModel

/**
 *  This ViewModel is used to store hand landmarker helper settings
 */
class MainViewModel : ViewModel() {

    private var _gpu: Int = 0
    private var _detect: Float = 0.4f
    private var _track: Float = 0.4f
    private var _presence: Float = 0.4f
    private var _hands: Int = 1

    val gpu:      Int   get() = _gpu
    val detect:   Float get() = _detect
    val track:    Float get() = _track
    val presence: Float get() = _presence
    val hands:    Int   get() = _hands

    fun setGpu(t: Int)        { _gpu      = t }
    fun setDetect(s: Float)   { _detect   = s }
    fun setTrack(s: Float)    { _track    = s }
    fun setPresence(s: Float) { _presence = s }
    fun setHands(h: Int)      { _hands    = h }
}
