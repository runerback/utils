#include "LogWindow.h"

#include <string>
#include <windowsx.h>

namespace {

constexpr int kDefaultWidth = 800;
constexpr int kDefaultHeight = 400;
constexpr COLORREF kBackgroundColor = RGB(30, 30, 30);
constexpr COLORREF kTextColor = RGB(220, 220, 220);

} // namespace

LogWindow::LogWindow() = default;

LogWindow::~LogWindow() {
    destroy();
}

bool LogWindow::create(HINSTANCE hInstance, const wchar_t* title) {
    hInstance_ = hInstance;

    if (!registerWindowClass()) return false;
    if (!createWindow(title)) return false;
    if (!createEditControl()) return false;

    resizeEditControl();
    return true;
}

void LogWindow::destroy() {
    if (hFont_) {
        DeleteObject(hFont_);
        hFont_ = nullptr;
    }

    if (hBackgroundBrush_) {
        DeleteObject(hBackgroundBrush_);
        hBackgroundBrush_ = nullptr;
    }

    if (hwnd_) {
        DestroyWindow(hwnd_);
        hwnd_ = nullptr;
        hwndEdit_ = nullptr;
    }

    UnregisterClassW(kWindowClassName, hInstance_);
}

void LogWindow::show() {
    if (hwnd_) {
        ShowWindow(hwnd_, SW_SHOW);
        SetForegroundWindow(hwnd_);
    }
}

void LogWindow::hide() {
    if (hwnd_) {
        ShowWindow(hwnd_, SW_HIDE);
    }
}

void LogWindow::toggleVisible() {
    if (!hwnd_) return;
    if (IsWindowVisible(hwnd_)) {
        hide();
    } else {
        show();
    }
}

bool LogWindow::isVisible() const {
    return hwnd_ && IsWindowVisible(hwnd_) != FALSE;
}

void LogWindow::append(std::string_view text) {
    if (!hwndEdit_ || text.empty()) return;

    // Convert UTF-8 to wide characters for the edit control.
    const int wideLength = MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), nullptr, 0);
    if (wideLength <= 0) return;

    std::wstring wideText;
    wideText.resize(wideLength);
    MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), wideText.data(), wideLength);

    SendMessageW(hwndEdit_, EM_SETSEL, static_cast<WPARAM>(-1), static_cast<LPARAM>(-1));
    SendMessageW(hwndEdit_, EM_REPLACESEL, FALSE, reinterpret_cast<LPARAM>(wideText.c_str()));
}

bool LogWindow::registerWindowClass() {
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = windowProc;
    wc.hInstance = hInstance_;
    wc.hbrBackground = CreateSolidBrush(kBackgroundColor);
    wc.lpszClassName = kWindowClassName;

    return RegisterClassExW(&wc) != 0;
}

bool LogWindow::createWindow(const wchar_t* title) {
    hwnd_ = CreateWindowExW(
        WS_EX_CLIENTEDGE,
        kWindowClassName,
        title,
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        kDefaultWidth,
        kDefaultHeight,
        nullptr,
        nullptr,
        hInstance_,
        this
    );

    return hwnd_ != nullptr;
}

bool LogWindow::createEditControl() {
    hwndEdit_ = CreateWindowExW(
        0,
        L"EDIT",
        L"",
        WS_CHILD | WS_VISIBLE | ES_MULTILINE | ES_READONLY |
            ES_AUTOVSCROLL | ES_AUTOHSCROLL | WS_VSCROLL | WS_HSCROLL |
            ES_NOHIDESEL,
        0,
        0,
        0,
        0,
        hwnd_,
        nullptr,
        hInstance_,
        nullptr
    );

    if (!hwndEdit_) return false;

    hFont_ = CreateFontW(
        16,
        0,
        0,
        0,
        FW_NORMAL,
        FALSE,
        FALSE,
        FALSE,
        DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,
        CLIP_DEFAULT_PRECIS,
        DEFAULT_QUALITY,
        FIXED_PITCH | FF_MODERN,
        L"Consolas"
    );

    if (hFont_) {
        SendMessageW(hwndEdit_, WM_SETFONT, reinterpret_cast<WPARAM>(hFont_), TRUE);
    }

    hBackgroundBrush_ = CreateSolidBrush(kBackgroundColor);
    return true;
}

void LogWindow::resizeEditControl() {
    if (!hwnd_ || !hwndEdit_) return;

    RECT clientRect{};
    GetClientRect(hwnd_, &clientRect);
    SetWindowPos(
        hwndEdit_,
        nullptr,
        0,
        0,
        clientRect.right - clientRect.left,
        clientRect.bottom - clientRect.top,
        SWP_NOZORDER | SWP_NOACTIVATE
    );
}

LRESULT CALLBACK LogWindow::windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_CREATE) {
        const auto* createStruct = reinterpret_cast<LPCREATESTRUCT>(lParam);
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(createStruct->lpCreateParams));
        return 0;
    }

    const auto self = reinterpret_cast<LogWindow*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    if (self) {
        return self->handleMessage(msg, wParam, lParam);
    }

    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

LRESULT LogWindow::handleMessage(UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_SIZE:
        resizeEditControl();
        return 0;

    case WM_CLOSE:
        hide();
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;

    case WM_CTLCOLOREDIT:
        SetTextColor(reinterpret_cast<HDC>(wParam), kTextColor);
        SetBkColor(reinterpret_cast<HDC>(wParam), kBackgroundColor);
        return reinterpret_cast<LRESULT>(hBackgroundBrush_);
    }

    return DefWindowProcW(hwnd_, msg, wParam, lParam);
}
