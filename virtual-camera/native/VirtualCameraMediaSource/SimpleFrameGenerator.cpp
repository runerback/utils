//
// Copyright (C) Microsoft Corporation. All rights reserved.
//
#include "pch.h"

HRESULT SimpleFrameGenerator::Initialize(_In_ IMFMediaType* pMediaType, _In_opt_z_ LPCWSTR imagePath)
{
    RETURN_HR_IF_NULL(E_INVALIDARG, pMediaType);

    RETURN_IF_FAILED(pMediaType->GetGUID(MF_MT_SUBTYPE, &m_subType));
    if (m_subType != MFVideoFormat_RGB32 && m_subType != MFVideoFormat_NV12)
    {
        RETURN_HR_MSG(MF_E_UNSUPPORTED_FORMAT, "Unsupported format: %s", winrt::to_hstring(m_subType).data());
    }
    MFGetAttributeSize(pMediaType, MF_MT_FRAME_SIZE, &m_width, &m_height);
    m_loadedRGB32Frame.clear();

    if (imagePath != nullptr && imagePath[0] != L'\0')
    {
        RETURN_IF_FAILED(_LoadImageFrame(imagePath));
    }

    return S_OK;
}

/*:
   Writes to a buffer representing a 2D image.
   Writes a different constant to each line based on row number and current time.
   Assumes top down image, no negative stride and pBuf points to the begnning of the buffer of length len.
   Param:
   pBuf - pointer to beginning of buffer
   pitch - line length in bytes
   len - length of buffer in bytes
*/
HRESULT SimpleFrameGenerator::CreateFrame(
    _Inout_updates_bytes_(len) BYTE* pBuf,
    _In_ DWORD len,
    _In_ LONG pitch,
    _In_ ULONG rgbMask)
{
    if (m_subType == MFVideoFormat_RGB32)
    {
        DEBUG_MSG(L"RGB32 frames %s\n", winrt::to_hstring(MFVideoFormat_RGB32).data());

        if (!m_loadedRGB32Frame.empty())
        {
            RETURN_IF_FAILED(_CopyLoadedRGB32Frame(pBuf, len, pitch, m_width, m_height));
        }
        else
        {
            RETURN_IF_FAILED(_CreateRGB32Frame(pBuf, len, pitch, m_width, m_height, rgbMask));
        }
    }
    else if(m_subType == MFVideoFormat_NV12)
    {
        DEBUG_MSG(L"NV12 frames %s \n", winrt::to_hstring(MFVideoFormat_NV12).data());

        DWORD frameBuffLen = m_width * m_height * 4;
        wil::unique_cotaskmem_ptr<BYTE[]> spBuff = wil::make_unique_cotaskmem_nothrow<BYTE[]>(frameBuffLen);
        RETURN_IF_NULL_ALLOC(spBuff.get());

        if (!m_loadedRGB32Frame.empty())
        {
            memcpy_s(spBuff.get(), frameBuffLen, m_loadedRGB32Frame.data(), m_loadedRGB32Frame.size());
        }
        else
        {
            RETURN_IF_FAILED(_CreateRGB32Frame(spBuff.get(), frameBuffLen, m_width * 4, m_width, m_height, rgbMask));
        }
        RETURN_IF_FAILED(RGB32ToNV12Frame(spBuff.get(), frameBuffLen, m_width * 4, m_width, m_height, pBuf, len, pitch));
    }
    else
    {
        return MF_E_UNSUPPORTED_FORMAT;
    }

    return S_OK;
}

//////////////////////////////////////////////////
// private

HRESULT SimpleFrameGenerator::_LoadImageFrame(_In_z_ LPCWSTR imagePath)
{
    wil::com_ptr_nothrow<IWICImagingFactory> imagingFactory;
    wil::com_ptr_nothrow<IWICBitmapDecoder> decoder;
    wil::com_ptr_nothrow<IWICBitmapFrameDecode> frame;
    wil::com_ptr_nothrow<IWICBitmapSource> source;
    wil::com_ptr_nothrow<IWICBitmapScaler> scaler;
    wil::com_ptr_nothrow<IWICFormatConverter> converter;

    RETURN_IF_FAILED(CoCreateInstance(
        CLSID_WICImagingFactory,
        nullptr,
        CLSCTX_INPROC_SERVER,
        IID_PPV_ARGS(&imagingFactory)));
    RETURN_IF_FAILED(imagingFactory->CreateDecoderFromFilename(
        imagePath,
        nullptr,
        GENERIC_READ,
        WICDecodeMetadataCacheOnLoad,
        &decoder));
    RETURN_IF_FAILED(decoder->GetFrame(0, &frame));

    UINT frameWidth = 0;
    UINT frameHeight = 0;
    RETURN_IF_FAILED(frame->GetSize(&frameWidth, &frameHeight));

    if (frameWidth != m_width || frameHeight != m_height)
    {
        RETURN_IF_FAILED(imagingFactory->CreateBitmapScaler(&scaler));
        RETURN_IF_FAILED(scaler->Initialize(frame.get(), m_width, m_height, WICBitmapInterpolationModeFant));
        source = scaler;
    }
    else
    {
        source = frame;
    }

    RETURN_IF_FAILED(imagingFactory->CreateFormatConverter(&converter));
    RETURN_IF_FAILED(converter->Initialize(
        source.get(),
        GUID_WICPixelFormat32bppBGRA,
        WICBitmapDitherTypeNone,
        nullptr,
        0.0,
        WICBitmapPaletteTypeCustom));

    const UINT stride = m_width * 4;
    const UINT bufferSize = stride * m_height;
    m_loadedRGB32Frame.resize(bufferSize);
    RETURN_IF_FAILED(converter->CopyPixels(nullptr, stride, bufferSize, m_loadedRGB32Frame.data()));

    return S_OK;
}

HRESULT SimpleFrameGenerator::_CopyLoadedRGB32Frame(
    _Inout_updates_bytes_(len) BYTE* pBuf,
    _In_ DWORD len,
    _In_ LONG pitch,
    _In_ DWORD width,
    _In_ DWORD height)
{
    RETURN_HR_IF_NULL(E_INVALIDARG, pBuf);
    RETURN_HR_IF(E_UNEXPECTED, m_loadedRGB32Frame.empty());

    const LONG sourceStride = static_cast<LONG>(width * 4);
    if (len < (static_cast<DWORD>(abs(pitch)) * height))
    {
        return HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER);
    }

    for (DWORD row = 0; row < height; ++row)
    {
        BYTE* destination = (pitch >= 0)
            ? pBuf + (row * pitch)
            : pBuf + ((height - 1 - row) * (-pitch));
        const BYTE* source = m_loadedRGB32Frame.data() + (row * sourceStride);
        memcpy_s(destination, static_cast<size_t>(abs(pitch)), source, sourceStride);
    }

    return S_OK;
}

HRESULT SimpleFrameGenerator::_CreateRGB32Frame(
    _Inout_updates_bytes_(len) BYTE* pBuf,
    _In_ DWORD len,
    _In_ LONG pitch,
    _In_ DWORD width,
    _In_ DWORD height,
    _In_ ULONG rgbMask )
{
    RETURN_HR_IF_NULL(E_INVALIDARG, pBuf);
    if (len < (abs(pitch) * height ))
    {
        return HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER);
    }

    LONGLONG curSysTimeInS = MFGetSystemTime() / (MFTIME)10000000;
    int offset = curSysTimeInS % height;

    for (unsigned int r = 0; r < height; r++)
    {
        uint32_t* p = (uint32_t*)(pBuf + (r * pitch));
        for (unsigned int c = 0; c < width; c++)
        {
            BYTE gray = (BYTE)(r + offset);
            *p = ((uint32_t)gray << 16 | (uint32_t)gray << 8 | (uint32_t)gray) & rgbMask;
            p++;
        }
    }

    return S_OK;
}

//////////////////////////////////////////////////
// pixelFormatConverter

void SimpleFrameGenerator::RGB24ToYUY2(int R, int G, int B, BYTE* pY, BYTE* pU, BYTE* pV)
{
    *pY = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
    *pU = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
    *pV = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
}

void SimpleFrameGenerator::RGB24ToY(int R, int G, int B, BYTE* pY)
{
    *pY = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
}

void SimpleFrameGenerator::RGB32ToNV12(BYTE RGB1[8], BYTE RGB2[8], BYTE* pY1, BYTE* pY2, BYTE* pUV)
{
    RGB24ToYUY2(RGB1[2], RGB1[1], RGB1[0], pY1, pUV, pUV + 1);
    RGB24ToY(RGB1[6], RGB1[5], RGB1[4], pY1 + 1);
    RGB24ToYUY2(RGB2[2], RGB2[1], RGB2[0], pY2, pUV, pUV + 1);
    RGB24ToY(RGB2[6], RGB2[5], RGB2[4], pY2 + 1);
};

//////////////////////////////////////////////////
// FrameFormatConverter

HRESULT SimpleFrameGenerator::RGB32ToNV12Frame(_Inout_updates_bytes_(len) BYTE* pbBuff, ULONG cbBuff, long stride, UINT width, UINT height, BYTE* pbBuffOut, ULONG cbBuffOut, long strideOut)
{
    do
    {
        RETURN_HR_IF(E_UNEXPECTED, width * 4 * height > cbBuff);
        RETURN_HR_IF(E_UNEXPECTED, width * 1.5 * height > cbBuffOut);
        RETURN_HR_IF_NULL(E_INVALIDARG, pbBuff);

        RETURN_HR_IF_NULL(E_INVALIDARG, pbBuffOut);
        for (DWORD h = 0; h < height - 1; h += 2)
        {
            BYTE* pRGB1 = h * stride + pbBuff;
            BYTE* pRGB2 = (h + 1) * stride + pbBuff;
            BYTE* pY1 = h * strideOut + pbBuffOut;
            BYTE* pY2 = (h + 1) * strideOut + pbBuffOut;
            BYTE* pUV = (h / 2 + height) * strideOut + pbBuffOut;

            for (DWORD w = 0; w < width; w += 2)
            {
                RGB32ToNV12(pRGB1, pRGB2, pY1, pY2, pUV);
                pRGB1 += 8;
                pRGB2 += 8;
                pY1 += 2;
                pY2 += 2;
                pUV += 2;
            }
        }
    } while (FALSE);

    return S_OK;
}
