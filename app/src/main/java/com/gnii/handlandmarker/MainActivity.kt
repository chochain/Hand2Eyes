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
package com.gnii.handlandmarker

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.activity.OnBackPressedCallback
import com.gnii.handlandmarker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var main: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        main = ActivityMainBinding.inflate(layoutInflater)
        setContentView(main.root)

        val nav = supportFragmentManager.findFragmentById(
            R.id.fragment_container
        ) as NavHostFragment
        main.navigation.setupWithNavController(nav.navController)

        val callback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                // Your code that used to be in onBackPressed() goes here
                // Example:
                // if (someCondition) {
                //     // Do something specific
                // } else {
                //     // If you still want the default back behavior (e.g., closing the app),
                //     // you can call this, but often you'll handle it all here.
                //     // super.handleOnBackPressed() // Or simply finish() or navigate back
                // }
                // For simple activity closing:
                finish() 
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    fun updateBackground(lcolor: Int, rcolor: Int) {
        main.viewLeft.setBackgroundColor(lcolor)
        main.viewRight.setBackgroundColor(rcolor)
    }
}
