#pragma once

#include <memory>
#include <string>
#include <winerror.h>

class IVirtualCameraBackend
{
public:
    virtual ~IVirtualCameraBackend() = default;

    virtual const wchar_t* GetDisplayName() const noexcept = 0;
    virtual HRESULT CreateVirtualCamera(const std::wstring& friendlyName, const std::wstring& imagePath) = 0;
    virtual HRESULT RemoveVirtualCamera(const std::wstring& friendlyName) = 0;
};

std::unique_ptr<IVirtualCameraBackend> CreateVirtualCameraBackend();
