#include <winsock2.h>
#include <windows.h>
#include <shellapi.h>

#include "ConsoleStreambuf.h"
#include "InterceptionInput.h"
#include "KeyboardServer.h"
#include "LogWindow.h"
#include "TrayIcon.h"

#include <csignal>
#include <cstdlib>
#include <iostream>
#include <locale>
#include <string>
#include <thread>

namespace {

KeyboardServer* g_server = nullptr;

void signalHandler(int) {
    if (g_server) {
        g_server->stop();
    }
}

int parsePort(int argc, wchar_t** argv) {
    int port = 50051;
    if (argc >= 3 && std::wcscmp(argv[1], L"--port") == 0) {
        port = std::wcstol(argv[2], nullptr, 10);
    } else if (argc >= 2) {
        port = std::wcstol(argv[1], nullptr, 10);
    }
    return port;
}

} // namespace

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE, LPSTR, int) {
    // Use classic C locale for consistent numeric formatting in logs.
    std::locale::global(std::locale("C"));

    int argc = 0;
    wchar_t** argv = CommandLineToArgvW(GetCommandLineW(), &argc);

    int port = parsePort(argc, argv);
    LocalFree(argv);

    if (port <= 0 || port > 65535) {
        MessageBoxW(nullptr, L"Invalid port.", L"Keyboard Windows Receiver", MB_OK | MB_ICONERROR);
        return 1;
    }

    LogWindow logWindow;
    if (!logWindow.create(hInstance, L"Keyboard Windows Receiver - Logs")) {
        MessageBoxW(nullptr, L"Failed to create log window.", L"Keyboard Windows Receiver", MB_OK | MB_ICONERROR);
        return 1;
    }

    ConsoleStreambuf coutBuf(&logWindow);
    ConsoleStreambuf cerrBuf(&logWindow);
    std::streambuf* originalCoutBuf = std::cout.rdbuf(&coutBuf);
    std::streambuf* originalCerrBuf = std::cerr.rdbuf(&cerrBuf);

    InterceptionInput input;
    if (!input.initialize()) {
        std::cerr << "Failed to initialize input. Exiting.\n";
        std::cout.rdbuf(originalCoutBuf);
        std::cerr.rdbuf(originalCerrBuf);
        return 1;
    }

    KeyboardServer server(
        port,
        [&input](int vk, bool down) {
            input.sendKey(vk, down);
        },
        [&input](bool intercept) {
            input.setInterceptRealKeyboard(intercept);
        }
    );
    g_server = &server;

    std::signal(SIGINT, signalHandler);
    std::signal(SIGTERM, signalHandler);

    if (!server.start()) {
        std::cerr << "Failed to start server. Exiting.\n";
        std::cout.rdbuf(originalCoutBuf);
        std::cerr.rdbuf(originalCerrBuf);
        return 1;
    }

    std::thread serverThread([&server]() {
        server.run();
    });

    TrayIcon trayIcon;
    if (!trayIcon.create(
            hInstance,
            L"Keyboard Windows Receiver",
            [&]() {
                logWindow.toggleVisible();
            },
            [&]() {
                server.stop();
                trayIcon.destroy();
            })) {
        std::cerr << "Failed to create tray icon. Exiting.\n";
        server.stop();
        serverThread.join();
        std::cout.rdbuf(originalCoutBuf);
        std::cerr.rdbuf(originalCerrBuf);
        return 1;
    }

    trayIcon.runMessageLoop();

    if (serverThread.joinable()) {
        serverThread.join();
    }

    std::cout.rdbuf(originalCoutBuf);
    std::cerr.rdbuf(originalCerrBuf);

    return 0;
}
