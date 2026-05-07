//
// Copyright (C) Microsoft Corporation. All rights reserved.
//

#include "pch.h"

#include "../VirtualCameraMediaSource/VirtualCameraMediaSource.h"

#include <array>

using namespace winrt::Windows::Devices::Enumeration;
using namespace winrt::Windows::Media::Devices;

namespace
{
constexpr wchar_t kDefaultFriendlyName[] = L"Static Image Camera";
constexpr MFVirtualCameraLifetime kDefaultLifetime = MFVirtualCameraLifetime_System;
constexpr MFVirtualCameraAccess kDefaultAccess = MFVirtualCameraAccess_CurrentUser;

enum class Action
{
    Help,
    Create,
    Remove,
    List,
    RegisterSource,
    UnregisterSource,
    Install,
    Uninstall,
};

struct Options
{
    Action action = Action::Help;
    std::wstring friendlyName = kDefaultFriendlyName;
    std::wstring imagePath;
    std::wstring dllPath;
};

void PrintUsage()
{
    std::wcout
        << L"VirtualCameraControl\n"
        << L"Usage:\n"
        << L"  VirtualCameraControl install [--name <friendly-name>] [--image <path-to-image>] [--dll <media-source-dll>]\n"
        << L"  VirtualCameraControl uninstall [--name <friendly-name>]\n"
        << L"  VirtualCameraControl register-source [--dll <media-source-dll>]\n"
        << L"  VirtualCameraControl unregister-source\n"
        << L"  VirtualCameraControl create [--name <friendly-name>] [--image <path-to-image>]\n"
        << L"  VirtualCameraControl remove [--name <friendly-name>]\n"
        << L"  VirtualCameraControl list\n";
}

std::wstring GetMediaSourceRegistryKey()
{
    return std::wstring(L"Software\\Classes\\CLSID\\") + VIRTUALCAMERAMEDIASOURCE_CLSID;
}

std::wstring GetDefaultMediaSourceDllPath()
{
    std::array<wchar_t, MAX_PATH> modulePath{};
    const DWORD pathLength = GetModuleFileNameW(nullptr, modulePath.data(), static_cast<DWORD>(modulePath.size()));
    THROW_LAST_ERROR_IF(pathLength == 0);
    THROW_HR_IF(HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER), pathLength >= modulePath.size());

    std::wstring dllPath = modulePath.data();
    const size_t separator = dllPath.find_last_of(L"\\/");
    THROW_HR_IF(E_UNEXPECTED, separator == std::wstring::npos);
    dllPath.resize(separator + 1);
    dllPath += L"VirtualCameraMediaSource.dll";
    return dllPath;
}

HRESULT ValidateExistingFile(const std::wstring& path)
{
    const DWORD attributes = GetFileAttributesW(path.c_str());
    RETURN_LAST_ERROR_IF(attributes == INVALID_FILE_ATTRIBUTES);
    RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_DIRECTORY), (attributes & FILE_ATTRIBUTE_DIRECTORY) != 0);

    return S_OK;
}

HRESULT SetRegistryStringValue(HKEY key, _In_opt_z_ LPCWSTR valueName, const std::wstring& value)
{
    const DWORD byteCount = static_cast<DWORD>((value.size() + 1) * sizeof(wchar_t));
    RETURN_IF_WIN32_ERROR(RegSetValueExW(
        key,
        valueName,
        0,
        REG_SZ,
        reinterpret_cast<const BYTE*>(value.c_str()),
        byteCount));

    return S_OK;
}

HRESULT RegisterMediaSource(const std::wstring& dllPath)
{
    RETURN_IF_FAILED(ValidateExistingFile(dllPath));

    wil::unique_hkey clsidKey;
    wil::unique_hkey inprocServerKey;
    const std::wstring clsidRegistryKey = GetMediaSourceRegistryKey();
    const std::wstring inprocServerRegistryKey = clsidRegistryKey + L"\\InprocServer32";

    RETURN_IF_WIN32_ERROR(RegCreateKeyExW(
        HKEY_LOCAL_MACHINE,
        clsidRegistryKey.c_str(),
        0,
        nullptr,
        0,
        KEY_SET_VALUE | KEY_CREATE_SUB_KEY,
        nullptr,
        clsidKey.addressof(),
        nullptr));
    RETURN_IF_FAILED(SetRegistryStringValue(clsidKey.get(), nullptr, VIRTUALCAMERAMEDIASOURCE_FRIENDLYNAME));

    RETURN_IF_WIN32_ERROR(RegCreateKeyExW(
        HKEY_LOCAL_MACHINE,
        inprocServerRegistryKey.c_str(),
        0,
        nullptr,
        0,
        KEY_SET_VALUE,
        nullptr,
        inprocServerKey.addressof(),
        nullptr));
    RETURN_IF_FAILED(SetRegistryStringValue(inprocServerKey.get(), nullptr, dllPath));
    RETURN_IF_FAILED(SetRegistryStringValue(inprocServerKey.get(), L"ThreadingModel", L"Both"));

    std::wcout << L"Registered media source DLL: " << dllPath << L"\n";
    return S_OK;
}

HRESULT UnregisterMediaSource()
{
    const std::wstring clsidRegistryKey = GetMediaSourceRegistryKey();
    const LSTATUS status = RegDeleteTreeW(HKEY_LOCAL_MACHINE, clsidRegistryKey.c_str());
    if ((status != ERROR_SUCCESS) && (status != ERROR_FILE_NOT_FOUND))
    {
        RETURN_IF_WIN32_ERROR(status);
    }

    std::wcout << L"Unregistered media source CLSID: " << VIRTUALCAMERAMEDIASOURCE_CLSID << L"\n";
    return S_OK;
}

HRESULT ParseArguments(int argc, wchar_t* argv[], Options& options)
{
    if (argc <= 1)
    {
        options.action = Action::Help;
        return S_OK;
    }

    const std::wstring_view command = argv[1];
    if (command == L"create")
    {
        options.action = Action::Create;
    }
    else if (command == L"remove")
    {
        options.action = Action::Remove;
    }
    else if (command == L"list")
    {
        options.action = Action::List;
    }
    else if (command == L"register-source")
    {
        options.action = Action::RegisterSource;
    }
    else if (command == L"unregister-source")
    {
        options.action = Action::UnregisterSource;
    }
    else if (command == L"install")
    {
        options.action = Action::Install;
    }
    else if (command == L"uninstall")
    {
        options.action = Action::Uninstall;
    }
    else if ((command == L"-h") || (command == L"--help"))
    {
        options.action = Action::Help;
        return S_OK;
    }
    else
    {
        std::wcerr << L"Unknown command: " << command << L"\n";
        return HRESULT_FROM_WIN32(ERROR_BAD_ARGUMENTS);
    }

    for (int index = 2; index < argc; ++index)
    {
        const std::wstring_view argument = argv[index];
        if (argument == L"--name")
        {
            RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_BAD_ARGUMENTS), index + 1 >= argc);
            options.friendlyName = argv[++index];
        }
        else if (argument == L"--image")
        {
            RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_BAD_ARGUMENTS), index + 1 >= argc);
            options.imagePath = argv[++index];
        }
        else if (argument == L"--dll")
        {
            RETURN_HR_IF(HRESULT_FROM_WIN32(ERROR_BAD_ARGUMENTS), index + 1 >= argc);
            options.dllPath = argv[++index];
        }
        else if ((argument == L"-h") || (argument == L"--help"))
        {
            options.action = Action::Help;
            return S_OK;
        }
        else
        {
            std::wcerr << L"Unknown option: " << argument << L"\n";
            return HRESULT_FROM_WIN32(ERROR_BAD_ARGUMENTS);
        }
    }

    return S_OK;
}

HRESULT OpenVirtualCamera(const std::wstring& friendlyName, wil::com_ptr_nothrow<IMFVirtualCamera>& virtualCamera)
{
    RETURN_IF_FAILED(MFCreateVirtualCamera(
        MFVirtualCameraType_SoftwareCameraSource,
        kDefaultLifetime,
        kDefaultAccess,
        friendlyName.c_str(),
        VIRTUALCAMERAMEDIASOURCE_CLSID,
        nullptr,
        0,
        virtualCamera.put()));

    return S_OK;
}

HRESULT CreateVirtualCamera(const std::wstring& friendlyName, const std::wstring& imagePath)
{
    wil::com_ptr_nothrow<IMFVirtualCamera> virtualCamera;
    std::wcout << L"Creating virtual camera object...\n";
    HRESULT hr = OpenVirtualCamera(friendlyName, virtualCamera);
    if (FAILED(hr))
    {
        std::wcerr << L"MFCreateVirtualCamera failed: 0x" << std::hex << static_cast<unsigned long>(hr) << L"\n";
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

HRESULT RemoveVirtualCamera(const std::wstring& friendlyName)
{
    wil::com_ptr_nothrow<IMFVirtualCamera> virtualCamera;
    RETURN_IF_FAILED(OpenVirtualCamera(friendlyName, virtualCamera));
    RETURN_IF_FAILED(virtualCamera->Remove());
    RETURN_IF_FAILED(virtualCamera->Shutdown());

    std::wcout << L"Removed virtual camera: " << friendlyName << L"\n";
    return S_OK;
}

HRESULT ListVideoCaptureDevices()
{
    auto devices = DeviceInformation::FindAllAsync(MediaDevice::GetVideoCaptureSelector()).get();
    if (devices.Size() == 0)
    {
        std::wcout << L"No video capture devices found.\n";
        return S_OK;
    }

    for (auto const& device : devices)
    {
        std::wcout << L"- " << device.Name().c_str() << L"\n"
                   << L"  " << device.Id().c_str() << L"\n";
    }

    return S_OK;
}

HRESULT InstallVirtualCamera(const Options& options)
{
    const std::wstring dllPath = options.dllPath.empty() ? GetDefaultMediaSourceDllPath() : options.dllPath;
    RETURN_IF_FAILED(RegisterMediaSource(dllPath));
    auto unregisterOnFailure = wil::scope_exit([&]() noexcept
    {
        (void)UnregisterMediaSource();
    });
    RETURN_IF_FAILED(CreateVirtualCamera(options.friendlyName, options.imagePath));
    unregisterOnFailure.release();

    return S_OK;
}

HRESULT UninstallVirtualCamera(const Options& options)
{
    RETURN_IF_FAILED(RemoveVirtualCamera(options.friendlyName));
    RETURN_IF_FAILED(UnregisterMediaSource());

    return S_OK;
}

HRESULT Run(const Options& options)
{
    switch (options.action)
    {
    case Action::Install:
        return InstallVirtualCamera(options);
    case Action::Uninstall:
        return UninstallVirtualCamera(options);
    case Action::RegisterSource:
    {
        const std::wstring dllPath = options.dllPath.empty() ? GetDefaultMediaSourceDllPath() : options.dllPath;
        return RegisterMediaSource(dllPath);
    }
    case Action::UnregisterSource:
        return UnregisterMediaSource();
    case Action::Create:
        return CreateVirtualCamera(options.friendlyName, options.imagePath);
    case Action::Remove:
        return RemoveVirtualCamera(options.friendlyName);
    case Action::List:
        return ListVideoCaptureDevices();
    case Action::Help:
    default:
        PrintUsage();
        return S_OK;
    }
}
}

int __cdecl wmain(int argc, wchar_t* argv[])
try
{
    winrt::init_apartment(winrt::apartment_type::multi_threaded);
    RETURN_IF_FAILED(MFStartup(MF_VERSION));
    auto shutdownMediaFoundation = wil::scope_exit([]() noexcept { MFShutdown(); });

    Options options;
    RETURN_IF_FAILED(ParseArguments(argc, argv, options));
    const HRESULT hr = Run(options);
    if (FAILED(hr))
    {
        THROW_IF_FAILED(hr);
    }

    return 0;
}
catch (winrt::hresult_error const& error)
{
    std::wcerr << L"VirtualCameraControl failed: " << error.message().c_str()
               << L" (0x" << std::hex << static_cast<unsigned long>(error.code().value) << L")\n";
    return 1;
}
catch (std::exception const& error)
{
    std::cerr << "VirtualCameraControl failed: " << error.what() << "\n";
    return 1;
}
catch (...)
{
    std::wcerr << L"VirtualCameraControl failed with an unknown error.\n";
    return 1;
}
