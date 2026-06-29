#include "KeyboardServer.h"

#include "JsonProtocol.h"

#include <ctime>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <random>
#include <sstream>
#include <ws2tcpip.h>

#pragma comment(lib, "ws2_32.lib")

namespace {

std::string generateRandomHex(int length) {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 15);

    std::stringstream ss;
    for (int i = 0; i < length; ++i) {
        ss << std::hex << dis(gen);
    }
    return ss.str();
}

} // namespace

KeyboardServer::KeyboardServer(
    int port,
    KeyHandler handler,
    ConfigHandler configHandler,
    const std::string& tokenFile)
    : port_(port)
    , handler_(std::move(handler))
    , configHandler_(std::move(configHandler))
    , tokenFile_(tokenFile) {
    loadTokens();
}

KeyboardServer::~KeyboardServer() {
    stop();
}

bool KeyboardServer::start() {
    WSADATA wsaData{};
    const int startupResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (startupResult != 0) {
        std::cerr << "WSAStartup failed: " << startupResult << "\n";
        return false;
    }

    listenSocket_ = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket_ == INVALID_SOCKET) {
        std::cerr << "socket failed: " << WSAGetLastError() << "\n";
        WSACleanup();
        return false;
    }

    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(static_cast<u_short>(port_));

    if (bind(listenSocket_, reinterpret_cast<sockaddr*>(&serverAddr), sizeof(serverAddr)) == SOCKET_ERROR) {
        std::cerr << "bind failed: " << WSAGetLastError() << "\n";
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        WSACleanup();
        return false;
    }

    if (listen(listenSocket_, SOMAXCONN) == SOCKET_ERROR) {
        std::cerr << "listen failed: " << WSAGetLastError() << "\n";
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
        WSACleanup();
        return false;
    }

    std::cout << "Keyboard server listening on 0.0.0.0:" << port_ << "\n";
    return true;
}

void KeyboardServer::run() {
    running_ = true;
    while (running_) {
        sockaddr_in clientAddr{};
        int clientAddrLen = sizeof(clientAddr);
        SOCKET clientSocket = accept(
            listenSocket_,
            reinterpret_cast<sockaddr*>(&clientAddr),
            &clientAddrLen
        );
        if (clientSocket == INVALID_SOCKET) {
            if (running_) {
                std::cerr << "accept failed: " << WSAGetLastError() << "\n";
            }
            break;
        }

        char clientIp[INET_ADDRSTRLEN]{};
        InetNtopA(
            AF_INET,
            &clientAddr.sin_addr,
            clientIp,
            INET_ADDRSTRLEN
        );
        std::cout << "Client connected: " << clientIp << "\n";

        handleClient(clientSocket);

        closesocket(clientSocket);
        std::cout << "Client disconnected: " << clientIp << "\n";
    }
}

void KeyboardServer::stop() {
    running_ = false;
    if (listenSocket_ != INVALID_SOCKET) {
        closesocket(listenSocket_);
        listenSocket_ = INVALID_SOCKET;
    }
    WSACleanup();
}

void KeyboardServer::handleClient(SOCKET clientSocket) {
    std::string buffer;
    char recvBuffer[1024]{};
    bool authenticated = false;
    std::string pairingCode;

    while (running_) {
        const int received = recv(clientSocket, recvBuffer, sizeof(recvBuffer) - 1, 0);
        if (received <= 0) break;

        recvBuffer[received] = '\0';
        buffer.append(recvBuffer, received);

        std::size_t newlinePos;
        while ((newlinePos = buffer.find('\n')) != std::string::npos) {
            std::string line = buffer.substr(0, newlinePos);
            if (!line.empty() && line.back() == '\r') {
                line.pop_back();
            }
            buffer.erase(0, newlinePos + 1);

            if (auto event = parseKeyEvent(line)) {
                if (!authenticated) {
                    if (pairingCode.empty()) {
                        pairingCode = generatePairingCode();
                        std::cout << "Pairing code: " << pairingCode << "\n";
                    }
                    if (!sendLine(clientSocket, makeAuthRequiredMessage())) {
                        return;
                    }
                    continue;
                }
                handler_(event->vk, event->down);
            } else if (auto config = parseConfigEvent(line)) {
                if (!authenticated) {
                    if (pairingCode.empty()) {
                        pairingCode = generatePairingCode();
                        std::cout << "Pairing code: " << pairingCode << "\n";
                    }
                    if (!sendLine(clientSocket, makeAuthRequiredMessage())) {
                        return;
                    }
                    continue;
                }
                if (configHandler_) {
                    configHandler_(config->interceptRealKeyboard);
                }
            } else if (auto auth = parseAuthEvent(line)) {
                if (approvedTokens_.count(auth->token) > 0) {
                    authenticated = true;
                    std::cout << "Client authenticated with existing token.\n";
                    if (!sendLine(clientSocket, makeAuthOkMessage())) {
                        return;
                    }
                } else {
                    if (pairingCode.empty()) {
                        pairingCode = generatePairingCode();
                        std::cout << "Pairing code: " << pairingCode << "\n";
                    }
                    if (!sendLine(clientSocket, makeAuthRequiredMessage())) {
                        return;
                    }
                }
            } else if (auto pair = parsePairEvent(line)) {
                if (pair->code == pairingCode && !pairingCode.empty()) {
                    const std::string token = generateToken();
                    approvedTokens_.insert(token);
                    saveTokens();
                    authenticated = true;
                    std::cout << "Client paired. Token saved.\n";
                    if (!sendLine(clientSocket, makeTokenMessage(token))) {
                        return;
                    }
                    if (!sendLine(clientSocket, makeAuthOkMessage())) {
                        return;
                    }
                } else {
                    if (!sendLine(clientSocket, makeAuthFailedMessage())) {
                        return;
                    }
                }
            }
        }
    }
}

bool KeyboardServer::sendLine(SOCKET clientSocket, const std::string& line) {
    const std::string payload = line + "\n";
    const int result = send(clientSocket, payload.c_str(), static_cast<int>(payload.size()), 0);
    return result != SOCKET_ERROR && result == static_cast<int>(payload.size());
}

std::string KeyboardServer::generatePairingCode() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(100000, 999999);
    return std::to_string(dis(gen));
}

std::string KeyboardServer::generateToken() {
    return generateRandomHex(32);
}

void KeyboardServer::loadTokens() {
    std::ifstream file(tokenFile_);
    if (!file.is_open()) return;

    std::string token;
    while (std::getline(file, token)) {
        if (!token.empty()) {
            approvedTokens_.insert(token);
        }
    }
}

void KeyboardServer::saveTokens() {
    std::ofstream file(tokenFile_);
    if (!file.is_open()) {
        std::cerr << "Failed to save tokens to " << tokenFile_ << "\n";
        return;
    }
    for (const auto& token : approvedTokens_) {
        file << token << "\n";
    }
}
