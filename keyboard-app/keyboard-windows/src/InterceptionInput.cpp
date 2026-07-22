#include "InterceptionInput.h"

#include "interception.h"

#include <windows.h>

#include <atomic>
#include <iostream>
#include <thread>

struct InterceptionInput::Impl {
    InterceptionContext context = nullptr;
    InterceptionDevice keyboardDevice = 0;
    bool ready = false;
    HKL usLayout = nullptr;

    std::thread receiveThread;
    std::atomic<bool> running{false};
    std::atomic<bool> interceptRealKeyboard{false};
};

InterceptionInput::InterceptionInput() : impl_(new Impl{}) {}

InterceptionInput::~InterceptionInput() {
    shutdown();
    delete impl_;
}

bool InterceptionInput::initialize() {
    if (impl_->ready) return true;

    impl_->context = interception_create_context();
    if (!impl_->context) {
        std::cerr << "Failed to create Interception context. Is the driver installed?\n";
        return false;
    }

    // Use the first virtual keyboard slot for sending input.
    impl_->keyboardDevice = INTERCEPTION_KEYBOARD(0);

    // Start with no receive filter so real keyboards are not affected.
    interception_set_filter(
        impl_->context,
        interception_is_keyboard,
        INTERCEPTION_FILTER_KEY_NONE
    );

    impl_->ready = true;
    impl_->running = true;

    // Load the US-English layout so VK-to-scan-code mapping is independent of
    // the active Windows keyboard layout on the receiving PC. This keeps the
    // on-screen QWERTY labels consistent with the physical scan codes sent.
    impl_->usLayout = LoadKeyboardLayoutW(L"00000409", KLF_NOTELLSHELL);
    if (!impl_->usLayout) {
        std::cerr << "Failed to load US-English keyboard layout; falling back to active layout mapping.\n";
    }

    impl_->receiveThread = std::thread(&InterceptionInput::receiveLoop, this);

    std::cout << "Interception input initialized.\n";
    return true;
}

void InterceptionInput::shutdown() {
    impl_->running = false;
    if (impl_->receiveThread.joinable()) {
        impl_->receiveThread.join();
    }
    if (impl_->context) {
        interception_destroy_context(impl_->context);
        impl_->context = nullptr;
    }
    if (impl_->usLayout) {
        UnloadKeyboardLayout(impl_->usLayout);
        impl_->usLayout = nullptr;
    }
    impl_->ready = false;
}

void InterceptionInput::sendKey(int vk, bool down) {
    if (!impl_->ready) return;

    const UINT scanCodeEx = impl_->usLayout
        ? MapVirtualKeyExW(static_cast<UINT>(vk), MAPVK_VK_TO_VSC_EX, impl_->usLayout)
        : MapVirtualKeyW(static_cast<UINT>(vk), MAPVK_VK_TO_VSC_EX);
    if (scanCodeEx == 0) {
        std::cerr << "Cannot map VK " << vk << " to scan code\n";
        return;
    }

    const bool isExtended = (scanCodeEx & 0xFF00) == 0xE000;
    const unsigned short scanCode = static_cast<unsigned short>(scanCodeEx & 0xFF);

    InterceptionKeyStroke stroke{};
    stroke.code = scanCode;
    stroke.state = (down ? INTERCEPTION_KEY_DOWN : INTERCEPTION_KEY_UP)
                   | (isExtended ? INTERCEPTION_KEY_E0 : 0);
    stroke.information = 0;

    interception_send(
        impl_->context,
        impl_->keyboardDevice,
        reinterpret_cast<InterceptionStroke*>(&stroke),
        1
    );

    // Give Windows time to update its internal key state before the next
    // injected event arrives. Without this pause, modifier combos such as
    // Win+R are often seen as just the Windows key, so only the Start menu
    // opens.
    Sleep(10);
}

void InterceptionInput::setInterceptRealKeyboard(bool intercept) {
    if (!impl_->ready) return;

    const bool previous = impl_->interceptRealKeyboard.exchange(intercept);
    if (previous == intercept) return;

    if (intercept) {
        interception_set_filter(
            impl_->context,
            interception_is_keyboard,
            INTERCEPTION_FILTER_KEY_ALL
        );
        std::cout << "Real keyboard input interception enabled.\n";
    } else {
        interception_set_filter(
            impl_->context,
            interception_is_keyboard,
            INTERCEPTION_FILTER_KEY_NONE
        );
        std::cout << "Real keyboard input interception disabled.\n";
    }
}

bool InterceptionInput::isReady() const {
    return impl_->ready;
}

void InterceptionInput::receiveLoop() {
    while (impl_->running) {
        InterceptionDevice device = interception_wait_with_timeout(impl_->context, 100);
        if (device == 0) continue;
        if (!interception_is_keyboard(device)) continue;

        InterceptionKeyStroke stroke{};
        const int received = interception_receive(
            impl_->context,
            device,
            reinterpret_cast<InterceptionStroke*>(&stroke),
            1
        );
        if (received <= 0) continue;

        // Only drop events when interception is explicitly enabled.
        // If the filter was active but interception is now off, pass them through.
        if (!impl_->interceptRealKeyboard) {
            interception_send(
                impl_->context,
                device,
                reinterpret_cast<InterceptionStroke*>(&stroke),
                1
            );
        }
        // Otherwise drop the event, blocking the real keyboard.
    }
}
