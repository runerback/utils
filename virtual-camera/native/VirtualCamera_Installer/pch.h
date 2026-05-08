#pragma once

#include <windows.h>
#include <winerror.h>

#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>

#include <string>
#include <string_view>
#include <vector>
#include <iostream>

#define RESULT_DIAGNOSTICS_LEVEL 4

#include <wil/cppwinrt.h>
#include <wil/result.h>
#include <wil/com.h>
#include <wil/resource.h>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Devices.Enumeration.h>
#include <winrt/Windows.Media.Devices.h>

#pragma comment(lib, "windowsapp")
#pragma comment(lib, "mfuuid")
#pragma comment(lib, "mfplat")
#pragma comment(lib, "mf")
