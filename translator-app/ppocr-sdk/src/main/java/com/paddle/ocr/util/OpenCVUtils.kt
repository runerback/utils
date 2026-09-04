// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.util

import android.content.Context
import android.util.Log

object OpenCVUtils {

    private var loaderLoaded = false

    init {
        try {
            System.loadLibrary("opencv_loader")
            loaderLoaded = true
            Log.i("OpenCVUtils", "opencv_loader loaded")
        } catch (e: Throwable) {
            Log.e("OpenCVUtils", "opencv_loader load failed", e)
        }
    }

    private external fun nativeLoadOpenCvShim(): Boolean

    private var initialized = false

    fun init(context: Context): Boolean {
        if (initialized) return true
        try {
            // Publish the legacy-builtin shim's symbols (RTLD_GLOBAL) so that
            // libopencv_java4.so, loaded locally via System.loadLibrary, can
            // resolve __sfp_handle_exceptions / __lttf2 / _Unwind_Resume.
            if (loaderLoaded) {
                val shimLoaded = try {
                    nativeLoadOpenCvShim()
                } catch (t: Throwable) {
                    Log.e("OpenCVUtils", "nativeLoadOpenCvShim threw", t)
                    false
                }
                Log.i("OpenCVUtils", "shim loaded globally: $shimLoaded")
            } else {
                Log.e("OpenCVUtils", "loader missing; shim will not be global")
            }
            // Even when the native side already dlopened libopencv_java4.so,
            // still load through System.loadLibrary: ART only searches
            // classloader-registered libraries for standard-mangled JNI names
            // (Java_org_opencv_core_Mat_n_1Mat ...). The lib is already mapped,
            // so this just registers the handle and fires JNI_OnLoad.
            System.loadLibrary("opencv_java4")
            initialized = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenCVUtils", "Failed to initialize OpenCV: ${e.message}")
        }
        return initialized
    }
}
