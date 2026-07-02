#include "ConsoleStreambuf.h"

ConsoleStreambuf::ConsoleStreambuf(LogWindow* logWindow)
    : logWindow_(logWindow) {
    setp(nullptr, nullptr);
}

ConsoleStreambuf::int_type ConsoleStreambuf::overflow(int_type ch) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (ch == traits_type::eof()) {
        flushBuffer();
        return traits_type::not_eof(ch);
    }

    buffer_.push_back(static_cast<char>(ch));

    if (ch == '\n') {
        flushBuffer();
    }

    return traits_type::not_eof(ch);
}

int ConsoleStreambuf::sync() {
    std::lock_guard<std::mutex> lock(mutex_);
    flushBuffer();
    return 0;
}

void ConsoleStreambuf::flushBuffer() {
    if (buffer_.empty() || !logWindow_) return;

    logWindow_->append(buffer_);
    buffer_.clear();
}
