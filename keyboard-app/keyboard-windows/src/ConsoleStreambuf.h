#pragma once

#include "LogWindow.h"

#include <mutex>
#include <sstream>
#include <streambuf>

class ConsoleStreambuf : public std::streambuf {
public:
    explicit ConsoleStreambuf(LogWindow* logWindow);

protected:
    int_type overflow(int_type ch) override;
    int sync() override;

private:
    LogWindow* logWindow_;
    std::mutex mutex_;
    std::string buffer_;

    void flushBuffer();
};
