#include "TrayIcon.h"

#include <shellapi.h>

#pragma comment(lib, "shell32.lib")

namespace {

constexpr wchar_t kWindowClassName[] = L"KeyboardWindowsTrayIcon";

} // namespace

TrayIcon::TrayIcon() = default;

TrayIcon::~TrayIcon() {
    destroy();
}

bool TrayIcon::create(
    HINSTANCE hInstance,
    const std::wstring& tooltip,
    ShowLogsCallback onShowLogs,
    ExitCallback onExit) {
    hInstance_ = hInstance;
    onShowLogs_ = std::move(onShowLogs);
    onExit_ = std::move(onExit);

    if (!registerWindowClass()) return false;
    if (!createWindow()) return false;
    if (!addIcon(tooltip)) return false;

    menu_ = CreatePopupMenu();
    if (!menu_) return false;

    AppendMenuW(menu_, MF_STRING, kShowLogsMenuId, L"Show / hide logs");
    AppendMenuW(menu_, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(menu_, MF_STRING, kExitMenuId, L"Exit");

    running_ = true;
    return true;
}

void TrayIcon::destroy() {
    removeIcon();

    if (menu_) {
        DestroyMenu(menu_);
        menu_ = nullptr;
    }

    if (hwnd_) {
        DestroyWindow(hwnd_);
        hwnd_ = nullptr;
    }

    UnregisterClassW(kWindowClassName, hInstance_);
    running_ = false;
}

void TrayIcon::runMessageLoop() {
    MSG msg{};
    while (running_ && GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
}

void TrayIcon::requestExit() {
    if (onExit_) {
        onExit_();
    }
}

void TrayIcon::requestShowLogs() {
    if (onShowLogs_) {
        onShowLogs_();
    }
}

bool TrayIcon::registerWindowClass() {
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = windowProc;
    wc.hInstance = hInstance_;
    wc.lpszClassName = kWindowClassName;

    return RegisterClassExW(&wc) != 0;
}

bool TrayIcon::createWindow() {
    hwnd_ = CreateWindowExW(
        0,
        kWindowClassName,
        L"Keyboard Windows Receiver",
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT,
        CW_USEDEFAULT,
        0,
        0,
        nullptr,
        nullptr,
        hInstance_,
        this
    );

    return hwnd_ != nullptr;
}

bool TrayIcon::addIcon(const std::wstring& tooltip) {
    NOTIFYICONDATAW nid{};
    nid.cbSize = sizeof(nid);
    nid.hWnd = hwnd_;
    nid.uID = 1;
    nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
    nid.uCallbackMessage = kTrayMessageId;
    nid.hIcon = LoadIconW(nullptr, reinterpret_cast<LPCWSTR>(IDI_APPLICATION));
    wcsncpy_s(nid.szTip, tooltip.c_str(), _TRUNCATE);

    return Shell_NotifyIconW(NIM_ADD, &nid) == TRUE;
}

void TrayIcon::removeIcon() {
    if (!hwnd_) return;

    NOTIFYICONDATAW nid{};
    nid.cbSize = sizeof(nid);
    nid.hWnd = hwnd_;
    nid.uID = 1;
    Shell_NotifyIconW(NIM_DELETE, &nid);
}

void TrayIcon::showContextMenu() {
    POINT pt{};
    GetCursorPos(&pt);

    SetForegroundWindow(hwnd_);
    TrackPopupMenu(
        menu_,
        TPM_RIGHTALIGN | TPM_BOTTOMALIGN | TPM_RIGHTBUTTON,
        pt.x,
        pt.y,
        0,
        hwnd_,
        nullptr
    );
    PostMessageW(hwnd_, WM_NULL, 0, 0);
}

LRESULT CALLBACK TrayIcon::windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_CREATE) {
        const auto* createStruct = reinterpret_cast<LPCREATESTRUCT>(lParam);
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(createStruct->lpCreateParams));
        return 0;
    }

    const auto self = reinterpret_cast<TrayIcon*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    if (self) {
        return self->handleMessage(msg, wParam, lParam);
    }

    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

LRESULT TrayIcon::handleMessage(UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case kTrayMessageId:
        if (lParam == WM_RBUTTONUP || lParam == WM_LBUTTONUP) {
            showContextMenu();
        } else if (lParam == WM_LBUTTONDBLCLK) {
            requestShowLogs();
        }
        return 0;

    case WM_COMMAND:
        if (LOWORD(wParam) == kShowLogsMenuId) {
            requestShowLogs();
        } else if (LOWORD(wParam) == kExitMenuId) {
            requestExit();
        }
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }

    return DefWindowProcW(hwnd_, msg, wParam, lParam);
}
