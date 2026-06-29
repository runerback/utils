#include "JsonProtocol.h"

#include <cctype>
#include <charconv>
#include <string>

namespace {

std::string_view extractStringValue(const std::string& line, const char* key) {
    const std::string keyStr = std::string("\"") + key + "\":";
    const auto pos = line.find(keyStr);
    if (pos == std::string::npos) return {};

    const auto quoteStart = line.find('"', pos + keyStr.size());
    if (quoteStart == std::string::npos) {
        // Try to read a numeric/bare token.
        const auto tokenStart = line.find_first_not_of(" \t\n\r", pos + keyStr.size());
        if (tokenStart == std::string::npos) return {};
        const auto tokenEnd = line.find_first_of(",}\n\r", tokenStart);
        if (tokenEnd == std::string::npos) {
            return std::string_view(line).substr(tokenStart);
        }
        return std::string_view(line).substr(tokenStart, tokenEnd - tokenStart);
    }

    const auto quoteEnd = line.find('"', quoteStart + 1);
    if (quoteEnd == std::string::npos) return {};
    return std::string_view(line).substr(quoteStart + 1, quoteEnd - quoteStart - 1);
}

std::optional<int> extractIntValue(const std::string& line, const char* key) {
    const std::string keyStr = std::string("\"") + key + "\":";
    const auto pos = line.find(keyStr);
    if (pos == std::string::npos) return std::nullopt;

    const auto valueStart = line.find_first_of("0123456789-", pos + keyStr.size());
    if (valueStart == std::string::npos) return std::nullopt;

    int value = 0;
    const auto result = std::from_chars(line.data() + valueStart, line.data() + line.size(), value);
    if (result.ec != std::errc()) return std::nullopt;
    return value;
}

std::optional<bool> extractBoolValue(const std::string& line, const char* key) {
    const std::string keyStr = std::string("\"") + key + "\":";
    const auto pos = line.find(keyStr);
    if (pos == std::string::npos) return std::nullopt;

    const auto valueStart = line.find_first_not_of(" \t\n\r", pos + keyStr.size());
    if (valueStart == std::string::npos) return std::nullopt;

    const auto valueEnd = line.find_first_of(",}\n\r", valueStart);
    const std::string token = (valueEnd == std::string::npos)
        ? line.substr(valueStart)
        : line.substr(valueStart, valueEnd - valueStart);

    if (token == "true") return true;
    if (token == "false") return false;
    return std::nullopt;
}

} // namespace

std::optional<KeyEvent> parseKeyEvent(const std::string& line) {
    const auto type = extractStringValue(line, "type");
    if (type != "key") return std::nullopt;

    const auto vk = extractIntValue(line, "vk");
    if (!vk.has_value()) return std::nullopt;

    const auto action = extractStringValue(line, "action");
    if (action == "down") return KeyEvent{vk.value(), true};
    if (action == "up") return KeyEvent{vk.value(), false};
    return std::nullopt;
}

std::optional<ConfigEvent> parseConfigEvent(const std::string& line) {
    const auto type = extractStringValue(line, "type");
    if (type != "config") return std::nullopt;

    const auto intercept = extractBoolValue(line, "intercept_real_keyboard");
    if (!intercept.has_value()) return std::nullopt;

    return ConfigEvent{intercept.value()};
}

std::optional<AuthEvent> parseAuthEvent(const std::string& line) {
    const auto type = extractStringValue(line, "type");
    if (type != "auth") return std::nullopt;

    const auto token = extractStringValue(line, "token");
    return AuthEvent{std::string(token)};
}

std::optional<PairEvent> parsePairEvent(const std::string& line) {
    const auto type = extractStringValue(line, "type");
    if (type != "pair") return std::nullopt;

    const auto code = extractStringValue(line, "code");
    return PairEvent{std::string(code)};
}

std::string makeAuthOkMessage() {
    return R"({"type":"auth_ok"})";
}

std::string makeAuthRequiredMessage() {
    return R"({"type":"auth_required"})";
}

std::string makeTokenMessage(const std::string& token) {
    return std::string(R"({"type":"token","token":")") + token + R"("})";
}

std::string makeAuthFailedMessage() {
    return R"({"type":"auth_failed"})";
}
