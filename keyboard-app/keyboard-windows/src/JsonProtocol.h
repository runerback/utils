#pragma once

#include <optional>
#include <string>

struct KeyEvent {
    int vk;
    bool down;
};

struct ConfigEvent {
    bool interceptRealKeyboard;
};

struct AuthEvent {
    std::string token;
};

struct PairEvent {
    std::string code;
};

std::optional<KeyEvent> parseKeyEvent(const std::string& line);
std::optional<ConfigEvent> parseConfigEvent(const std::string& line);
std::optional<AuthEvent> parseAuthEvent(const std::string& line);
std::optional<PairEvent> parsePairEvent(const std::string& line);

std::string makeAuthOkMessage();
std::string makeAuthRequiredMessage();
std::string makeTokenMessage(const std::string& token);
std::string makeAuthFailedMessage();
