#pragma once

#include <atomic>
#include <functional>
#include <set>
#include <string>

#include <winsock2.h>

class KeyboardServer {
public:
    using KeyHandler = std::function<void(int vk, bool down)>;
    using ConfigHandler = std::function<void(bool interceptRealKeyboard)>;

    explicit KeyboardServer(
        int port,
        KeyHandler handler,
        ConfigHandler configHandler = nullptr,
        const std::string& tokenFile = "approved_tokens.txt"
    );
    ~KeyboardServer();

    bool start();
    void run();
    void stop();

private:
    void handleClient(SOCKET clientSocket);
    bool sendLine(SOCKET clientSocket, const std::string& line);
    std::string generatePairingCode();
    std::string generateToken();
    void loadTokens();
    void saveTokens();

    int port_;
    KeyHandler handler_;
    ConfigHandler configHandler_;
    std::string tokenFile_;
    std::set<std::string> approvedTokens_;
    std::atomic<bool> running_{false};
    SOCKET listenSocket_ = INVALID_SOCKET;
};
