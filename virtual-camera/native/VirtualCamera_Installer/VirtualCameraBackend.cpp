#include "pch.h"

#include "VirtualCameraBackend.h"

#include "../VirtualCameraMediaSource/VirtualCameraMediaSource.h"

#include <mfvirtualcamera.h>

namespace
{
constexpr DWORD kWindows11BuildNumber = 22000;
constexpr wchar_t kWindows11BackendName[] = L"Windows 11 Media Foundation virtual camera";
constexpr wchar_t kWindows10BackendName[] = L"Windows 10 virtual camera backend (not yet implemented)";

using RtlGetVersionFn = LONG (WINAPI*)(OSVERSIONINFOW*);
using MFCreateVirtualCameraFn = HRESULT (WINAPI*)(
    MFVirtualCameraType type,
    MFVirtualCameraLifetime lifetime,
    MFVirtualCameraAccess access,
    LPCWSTR friendlyName,
    LPCWSTR sourceId,
    const GUID* categories,
    ULONG categoryCount,
    IMFVirtualCamera** virtualCamera);

HRESULT GetCurrentWindowsBuildNumber(DWORD& buildNumber)
{
    buildNumber = 0;

    wil::unique_hmodule module(LoadLibraryW(L"ntdll.dll"));
    RETURN_LAST_ERROR_IF_NULL(module.get());

    const auto rtlGetVersion = reinterpret_cast<RtlGetVersionFn>(GetProcAddress(module.get(), "RtlGetVersion"));
    RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_PROC_NOT_FOUND), rtlGetVersion == nullptr);

    OSVERSIONINFOW versionInfo{};
    versionInfo.dwOSVersionInfoSize = sizeof(versionInfo);

    const LONG status = rtlGetVersion(&versionInfo);
    RETURN_HR_IF(HRESULT_FROM_WIN32(status), status != 0);

    buildNumber = versionInfo.dwBuildNumber;
    return S_OK;
}

std::wstring BuildUnsupportedBackendMessage(DWORD buildNumber)
{
    if (buildNumber == 0)
    {
        return L"Unable to determine the current Windows build. The Windows 11 Media Foundation backend requires build 22000 or newer, and the in-repo Windows 10 backend is not implemented yet.";
    }

    return std::wstring(L"Windows build ") + std::to_wstring(buildNumber)
        + L" is below 22000. The Windows 11 Media Foundation backend is unavailable, and the in-repo Windows 10 backend is not implemented yet.";
}

class UnsupportedVirtualCameraBackend final : public IVirtualCameraBackend
{
public:
    explicit UnsupportedVirtualCameraBackend(DWORD buildNumber) :
        m_message(BuildUnsupportedBackendMessage(buildNumber))
    {
    }

    const wchar_t* GetDisplayName() const noexcept override
    {
        return kWindows10BackendName;
    }

    HRESULT CreateVirtualCamera(const std::wstring&, const std::wstring&) override
    {
        std::wcerr << m_message << L"\n";
        return HRESULT_FROM_WIN32(ERROR_NOT_SUPPORTED);
    }

    HRESULT RemoveVirtualCamera(const std::wstring&) override
    {
        std::wcerr << m_message << L"\n";
        return HRESULT_FROM_WIN32(ERROR_NOT_SUPPORTED);
    }

private:
    std::wstring m_message;
};

class Windows11MediaFoundationBackend final : public IVirtualCameraBackend
{
public:
    const wchar_t* GetDisplayName() const noexcept override
    {
        return kWindows11BackendName;
    }

    HRESULT CreateVirtualCamera(const std::wstring& friendlyName, const std::wstring& imagePath) override
    {
        wil::com_ptr_nothrow<IMFVirtualCamera> virtualCamera;
        std::wcout << L"Creating virtual camera object...\n";
        HRESULT hr = OpenVirtualCamera(friendlyName, virtualCamera);
        if (FAILED(hr))
        {
            std::wcerr << L"OpenVirtualCamera failed: 0x" << std::hex << static_cast<unsigned long>(hr) << L"\n";
            return hr;
        }

        std::wcout << L"Setting synthetic camera kind...\n";
        hr = virtualCamera->SetUINT32(VCAM_KIND, static_cast<UINT32>(VirtualCameraKind::Synthetic));
        if (FAILED(hr))
        {
            std::wcerr << L"SetUINT32(VCAM_KIND) failed: 0x" << std::hex << static_cast<unsigned long>(hr) << L"\n";
            return hr;
        }

        if (!imagePath.empty())
        {
            std::wcout << L"Setting image path attribute...\n";
            hr = virtualCamera->SetString(VCAM_IMAGE_PATH, imagePath.c_str());
            if (FAILED(hr))
            {
                std::wcerr << L"SetString(VCAM_IMAGE_PATH) failed: 0x" << std::hex << static_cast<unsigned long>(hr) << L"\n";
                return hr;
            }
        }

        std::wcout << L"Starting virtual camera registration...\n";
        hr = virtualCamera->Start(nullptr);
        if (FAILED(hr))
        {
            std::wcerr << L"IMFVirtualCamera::Start failed: 0x" << std::hex << static_cast<unsigned long>(hr) << L"\n";
            return hr;
        }

        std::wcout << L"Created virtual camera: " << friendlyName << L"\n";
        if (!imagePath.empty())
        {
            std::wcout << L"Image source: " << imagePath << L"\n";
        }

        return S_OK;
    }

    HRESULT RemoveVirtualCamera(const std::wstring& friendlyName) override
    {
        wil::com_ptr_nothrow<IMFVirtualCamera> virtualCamera;
        RETURN_IF_FAILED(OpenVirtualCamera(friendlyName, virtualCamera));
        RETURN_IF_FAILED(virtualCamera->Remove());
        RETURN_IF_FAILED(virtualCamera->Shutdown());

        std::wcout << L"Removed virtual camera: " << friendlyName << L"\n";
        return S_OK;
    }

private:
    static HRESULT LoadCreateVirtualCamera(MFCreateVirtualCameraFn& createVirtualCamera)
    {
        static HMODULE module = LoadLibraryW(L"mfsensorgroup.dll");
        RETURN_LAST_ERROR_IF_NULL(module);

        createVirtualCamera = reinterpret_cast<MFCreateVirtualCameraFn>(GetProcAddress(module, "MFCreateVirtualCamera"));
        RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_PROC_NOT_FOUND), createVirtualCamera == nullptr);

        return S_OK;
    }

    static HRESULT OpenVirtualCamera(const std::wstring& friendlyName, wil::com_ptr_nothrow<IMFVirtualCamera>& virtualCamera)
    {
        MFCreateVirtualCameraFn createVirtualCamera = nullptr;
        RETURN_IF_FAILED(LoadCreateVirtualCamera(createVirtualCamera));

        RETURN_IF_FAILED(createVirtualCamera(
            MFVirtualCameraType_SoftwareCameraSource,
            MFVirtualCameraLifetime_System,
            MFVirtualCameraAccess_CurrentUser,
            friendlyName.c_str(),
            VIRTUALCAMERAMEDIASOURCE_CLSID,
            nullptr,
            0,
            virtualCamera.put()));

        return S_OK;
    }
};
}

std::unique_ptr<IVirtualCameraBackend> CreateVirtualCameraBackend()
{
    DWORD buildNumber = 0;
    if (SUCCEEDED(GetCurrentWindowsBuildNumber(buildNumber)) && (buildNumber >= kWindows11BuildNumber))
    {
        return std::make_unique<Windows11MediaFoundationBackend>();
    }

    return std::make_unique<UnsupportedVirtualCameraBackend>(buildNumber);
}
