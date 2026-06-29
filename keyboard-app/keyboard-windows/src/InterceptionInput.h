#pragma once

class InterceptionInput {
public:
    InterceptionInput();
    ~InterceptionInput();

    bool initialize();
    void shutdown();
    void sendKey(int vk, bool down);
    void setInterceptRealKeyboard(bool intercept);

    bool isReady() const;

private:
    struct Impl;
    Impl* impl_;

    void receiveLoop();
};
