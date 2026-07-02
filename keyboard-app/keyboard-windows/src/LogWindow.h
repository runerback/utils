#pragma once

#include <string_view>
#include <windows.h>

class LogWindow {
public:
    LogWindow();
    ~LogWindow();

    bool create(HINSTANCE hInstance, const wchar_t* title);
    void destroy();

    void show();
    void hide();
    void toggleVisible();
    bool isVisible() const;

    void append(std::string_view text);

private:
    static constexpr wchar_t kWindowClassName[] = L"KeyboardWindowsLogWindow";

    HINSTANCE hInstance_ = nullptr;
    HWND hwnd_ = nullptr;
    HWND hwndEdit_ = nullptr;
    HFONT hFont_ = nullptr;
    HBRUSH hBackgroundBrush_ = nullptr;

    bool registerWindowClass();
    bool createWindow(const wchar_t* title);
    bool createEditControl();
    void resizeEditControl();

    static LRESULT CALLBACK windowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam);
    LRESULT handleMessage(UINT msg, WPARAM wParam, LPARAM lParam);
};
