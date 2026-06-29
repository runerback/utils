#include "InterceptionInput.h"
#include "KeyboardServer.h"

#include <iostream>
#include <csignal>
#include <cstring>
#include <cstdlib>

namespace {

KeyboardServer* g_server = nullptr;

void signalHandler(int) {
    if (g_server) {
        g_server->stop();
    }
}

} // namespace

int main(int argc, char* argv[]) {
    int port = 50051;
    if (argc >= 3 && std::strcmp(argv[1], "--port") == 0) {
        port = std::atoi(argv[2]);
    } else if (argc >= 2) {
        port = std::atoi(argv[1]);
    }

    if (port <= 0 || port > 65535) {
        std::cerr << "Invalid port.\n";
        return 1;
    }

    InterceptionInput input;
    if (!input.initialize()) {
        std::cerr << "Input initialization failed. Exiting." << std::endl;
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
        return 1;
    }

    server.run();
    return 0;
}
