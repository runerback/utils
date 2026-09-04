// Shim for legacy OpenCV arm64 builds: they reference __sfp_handle_exceptions,
// __lttf2 and _Unwind_Resume expecting libc++_shared.so to provide them (old
// NDKs re-exported compiler-rt builtins/unwinder; current NDKs do not).
// __lttf2 and _Unwind_Resume are pulled from the NDK's own compiler-rt /
// libunwind archives (see CMakeLists.txt); this file adds the one symbol the
// NDK no longer ships at all. Loaded via dlopen(RTLD_GLOBAL) from opencv_loader
// so these symbols resolve when libopencv_java4.so is loaded afterwards.
#include <stdint.h>

#if defined(__aarch64__)

// Keep the hidden implementations linked in (see CMakeLists.txt) even though
// nothing here calls them directly; without a reference --gc-sections would
// collect them.
extern char __lttf2_hidden[];
extern char _Unwind_Resume_hidden[];
__attribute__((used, visibility("default")))
void *const shim_keepalive[] = {
    (void *)&__lttf2_hidden[0],
    (void *)&_Unwind_Resume_hidden[0],
};

// Exported thunks forwarding to the renamed hidden implementations. A tail
// branch preserves whatever register ABI the __fp128/unwinder calling
// conventions use, so this works without native tf-type support in C.
__asm__(
    ".globl __lttf2\n"
    ".type __lttf2, %function\n"
    "__lttf2:\n"
    "    b __lttf2_hidden\n"
    ".globl _Unwind_Resume\n"
    ".type _Unwind_Resume, %function\n"
    "_Unwind_Resume:\n"
    "    b _Unwind_Resume_hidden\n"
);

// flags use the compiler-rt FP_EX_* values, which map 1:1 onto the FPSR
// cumulative exception bits: IOC(0) DZC(1) OFC(2) UFC(3) IXC(4).
void __sfp_handle_exceptions(int flags) {
    uint64_t fpsr;
    __asm__ volatile("mrs %0, fpsr" : "=r"(fpsr));
    fpsr |= (uint64_t)(flags & 0x1f);
    __asm__ volatile("msr fpsr, %0" ::"r"(fpsr));
}

#endif
