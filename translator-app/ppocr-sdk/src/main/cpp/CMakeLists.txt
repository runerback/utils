cmake_minimum_required(VERSION 3.22.1)
project(ocr_native_shim C)

find_library(liblog log)
find_library(libdl dl)

add_library(sfp_exceptions SHARED sfp_exceptions.c)
target_link_libraries(sfp_exceptions PRIVATE ${liblog} ${libdl})
# DF_1_GLOBAL puts the shim in the namespace's global group at map time;
# RTLD_GLOBAL at dlopen time is ignored for app classloader namespaces.
target_link_options(sfp_exceptions PRIVATE "-Wl,-z,global")

add_library(opencv_loader SHARED opencv_loader.c)
target_link_libraries(opencv_loader PRIVATE ${liblog} ${libdl})

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # libopencv_java4 (built with an old NDK) needs three symbols that modern
    # libc++_shared.so no longer exports: __sfp_handle_exceptions (implemented
    # in sfp_exceptions.c), __lttf2 (compiler-rt builtins) and _Unwind_Resume
    # (libunwind). Pull the real implementations from the NDK archives.
    # The NDK marks __lttf2/_Unwind_Resume GLOBAL HIDDEN, and lld won't export
    # hidden symbols from a shared lib, so globalize them at configure time.
    file(GLOB CLANG_PREBUILT_DIRS "${ANDROID_NDK}/toolchains/llvm/prebuilt/*")
    list(GET CLANG_PREBUILT_DIRS 0 CLANG_DIR)
    file(GLOB CLANG_RES_DIRS "${CLANG_DIR}/lib/clang/*")
    list(GET CLANG_RES_DIRS 0 CLANG_RES_DIR)
    set(CLANG_LINUX_LIB_DIR "${CLANG_RES_DIR}/lib/linux")
    set(BUILTINS_ARCHIVE "${CLANG_LINUX_LIB_DIR}/libclang_rt.builtins-aarch64-android.a")
    set(UNWIND_ARCHIVE "${CLANG_LINUX_LIB_DIR}/aarch64/libunwind.a")

    find_program(LLVM_NM_TOOL NAMES llvm-nm PATHS "${CLANG_DIR}/bin" NO_DEFAULT_PATH REQUIRED)
    find_program(LLVM_AR_TOOL NAMES llvm-ar PATHS "${CLANG_DIR}/bin" NO_DEFAULT_PATH REQUIRED)
    find_program(LLVM_OBJCOPY_TOOL NAMES llvm-objcopy PATHS "${CLANG_DIR}/bin" NO_DEFAULT_PATH REQUIRED)

    set(EXTRACT_DIR "${CMAKE_CURRENT_BINARY_DIR}/ndk_objs")
    file(MAKE_DIRECTORY "${EXTRACT_DIR}")

    # Extract every libunwind member (small archive), rename the hidden
    # _Unwind_Resume definition; sfp_exceptions.c exports a thunk under the
    # original name that tail-calls it.
    set(UNWIND_DIR "${EXTRACT_DIR}/unwind")
    file(MAKE_DIRECTORY "${UNWIND_DIR}")
    execute_process(COMMAND "${LLVM_AR_TOOL}" x "${UNWIND_ARCHIVE}" WORKING_DIRECTORY "${UNWIND_DIR}")
    execute_process(COMMAND "${LLVM_NM_TOOL}" -A --defined-only "${UNWIND_ARCHIVE}" OUTPUT_VARIABLE UNWIND_NM_OUTPUT)
    string(REGEX MATCH ":([A-Za-z0-9_./-]+\\.o): [0-9a-fA-F]+ T _Unwind_Resume" _resume_match "${UNWIND_NM_OUTPUT}")
    if(NOT _resume_match)
        message(FATAL_ERROR "Could not find _Unwind_Resume member in ${UNWIND_ARCHIVE}")
    endif()
    execute_process(COMMAND "${LLVM_OBJCOPY_TOOL}"
        --redefine-sym=_Unwind_Resume=_Unwind_Resume_hidden
        "${UNWIND_DIR}/${CMAKE_MATCH_1}")
    file(GLOB UNWIND_OBJS "${UNWIND_DIR}/*.o")
    set_source_files_properties(${UNWIND_OBJS} PROPERTIES EXTERNAL_OBJECT TRUE)
    target_sources(sfp_exceptions PRIVATE ${UNWIND_OBJS})

    # Extract only the builtins member defining __lttf2 and rename it; the
    # exported thunk lives in sfp_exceptions.c.
    execute_process(COMMAND "${LLVM_NM_TOOL}" -A --defined-only "${BUILTINS_ARCHIVE}" OUTPUT_VARIABLE BUILTINS_NM_OUTPUT)
    string(REGEX MATCH ":([A-Za-z0-9_./-]+\\.o): [0-9a-fA-F]+ T __lttf2" _lttf2_match "${BUILTINS_NM_OUTPUT}")
    if(NOT _lttf2_match)
        message(FATAL_ERROR "Could not find __lttf2 member in ${BUILTINS_ARCHIVE}")
    endif()
    set(LTTF2_MEMBER "${CMAKE_MATCH_1}")
    execute_process(COMMAND "${LLVM_AR_TOOL}" x "${BUILTINS_ARCHIVE}" "${LTTF2_MEMBER}" WORKING_DIRECTORY "${EXTRACT_DIR}")
    set(LTTF2_OBJ "${EXTRACT_DIR}/${LTTF2_MEMBER}")
    if(NOT EXISTS "${LTTF2_OBJ}")
        message(FATAL_ERROR "Failed to extract ${LTTF2_MEMBER} from ${BUILTINS_ARCHIVE}")
    endif()
    execute_process(COMMAND "${LLVM_OBJCOPY_TOOL}"
        --redefine-sym=__lttf2=__lttf2_hidden
        "${LTTF2_OBJ}")
    set_source_files_properties("${LTTF2_OBJ}" PROPERTIES EXTERNAL_OBJECT TRUE)
    target_sources(sfp_exceptions PRIVATE "${LTTF2_OBJ}")
endif()
