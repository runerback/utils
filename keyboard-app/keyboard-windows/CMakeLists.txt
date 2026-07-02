cmake_minimum_required(VERSION 3.20)
project(KeyboardWindows LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(interception STATIC
    third_party/interception/interception.c
)
target_include_directories(interception PUBLIC third_party/interception)
target_compile_definitions(interception PRIVATE INTERCEPTION_STATIC)

add_executable(keyboard-windows WIN32
    src/main.cpp
    src/KeyboardServer.cpp
    src/InterceptionInput.cpp
    src/JsonProtocol.cpp
    src/TrayIcon.cpp
    src/LogWindow.cpp
    src/ConsoleStreambuf.cpp
)

target_link_libraries(keyboard-windows PRIVATE interception ws2_32 shell32 gdi32)

target_compile_definitions(keyboard-windows PRIVATE INTERCEPTION_STATIC)
