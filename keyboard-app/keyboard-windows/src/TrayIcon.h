#pragma once

#include <functional>
#include <string>
#include <windows.h>

class TrayIcon {
public:
    using ExitCallback = std::function<void()>;
    using ShowLogsCallback = std::function<void()>;

    TrayIcon();
    ~TrayIcon();

    bool create(
        HINSTANCE hInstance,
        const std::wstring& tooltip,
        ShowLogsCallback onShowLogs,
        ExitCallback onExit);
    void destroy();
    void runMessageLoop();
    void requestExit();
    void requestShowLogs();

private:
    static constexpr UINT kTrayMessageId = WM_APP + 1;
    static constexpr UINT kShowLogsMenuId = 1001;
    static constexpr UINT kExitMenuId = 1002;

    HINSTANCE hInstance_ = nullptr;
    HWND hwnd_ = nullptr;
    HMENU menu_ = nullptr;
    bool running_ = false;
    ShowLogsCallback onShowLogs_;
    ExitCallback onExit_;

    bool registerWindowClass();
    bool createWindow();
    bool addIcon(const std::wstring& tooltip);
    void removeIcon();
    void showContextMenu();

    static LRESULT CALLBACK windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT handleMessage(UINT msg, WPARAM wParam, LPARAM lParam);
};
