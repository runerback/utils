#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

// System.loadLibrary() loads libraries RTLD_LOCAL, so symbols loaded that way
// are invisible to libopencv_java4.so's symbol resolution. Loading the shim
// with RTLD_GLOBAL publishes its symbols (__sfp_handle_exceptions, __lttf2,
// _Unwind_Resume) into this classloader's linker namespace before OpenCV.
//
// Fallback: load libopencv_java4.so from here too (same dlopen path, after
// the shim) and invoke its JNI_OnLoad manually, since System.loadLibrary is
// skipped when this succeeds and the runtime would otherwise never fire it.
JNIEXPORT jboolean JNICALL
Java_com_paddle_ocr_util_OpenCVUtils_nativeLoadOpenCvShim(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "OpenCVUtils", "nativeLoadOpenCvShim: entering");
    void *shim = dlopen("libsfp_exceptions.so", RTLD_NOW | RTLD_GLOBAL);
    if (!shim) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVUtils",
                            "shim dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }
    __android_log_print(ANDROID_LOG_INFO, "OpenCVUtils",
                        "shim dlopen ok, handle=%p", shim);
    void *sfp = dlsym(shim, "__sfp_handle_exceptions");
    void *lttf2 = dlsym(shim, "__lttf2");
    void *resume = dlsym(shim, "_Unwind_Resume");
    __android_log_print(ANDROID_LOG_INFO, "OpenCVUtils",
                        "shim symbols: __sfp_handle_exceptions=%p __lttf2=%p _Unwind_Resume=%p",
                        sfp, lttf2, resume);

    void *opencv = dlopen("libopencv_java4.so", RTLD_NOW);
    if (!opencv) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVUtils",
                            "opencv dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }
    __android_log_print(ANDROID_LOG_INFO, "OpenCVUtils",
                        "opencv dlopen ok, handle=%p", opencv);

    jint (*on_load)(JavaVM *, void *) = (jint (*)(JavaVM *, void *)) dlsym(opencv, "JNI_OnLoad");
    if (!on_load) {
        __android_log_print(ANDROID_LOG_WARN, "OpenCVUtils",
                            "opencv has no JNI_OnLoad (symbol: %s)", dlerror());
        return JNI_TRUE;
    }
    JavaVM *vm = NULL;
    if ((*env)->GetJavaVM(env, &vm) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVUtils", "GetJavaVM failed");
        return JNI_FALSE;
    }
    jint ver = on_load(vm, NULL);
    __android_log_print(ANDROID_LOG_INFO, "OpenCVUtils",
                        "opencv JNI_OnLoad returned 0x%x", ver);
    if (ver < JNI_VERSION_1_2) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVUtils",
                            "opencv JNI_OnLoad rejected (0x%x)", ver);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
