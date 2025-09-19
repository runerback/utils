using Microsoft.Extensions.Logging;
using System.Buffers;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using Windows.Win32.Foundation;

[SupportedOSPlatform("windows5.0")]
public static partial class PdfPrinterHelper
{
    private const int WM_LBUTTONDOWN = 0x0201;
    private const int WM_LBUTTONUP = 0x0202;
    private const int WM_CHAR = 0x0102;

    private const string SaveFileDialogTitle = "Save Print Output As";

    public static async Task AutoSavePdfFile(string filename, ILogger? logger = default, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(filename))
        {
            logger?.LogWarning("thee need filename");
            return;
        }
        var window = await FindDialogWindow(logger, cancellationToken);
        if (window == default)
        {
            logger?.LogInformation("do it mannually");
            return;
        }
        try
        {
            await AutoSave((HWND)window, filename, logger, cancellationToken);
        }
        catch (Exception exp)
        {
            logger?.LogWarning(exp, "auto save is very very");
        }
        finally
        {
            logger?.LogInformation("bye");
        }
    }

    private async static Task<IntPtr> FindDialogWindow(ILogger? logger = default, CancellationToken cancellationToken = default)
    {
        var retry = 10;
        while (true)
        {
            await Task.Delay(200, cancellationToken);
            var window = Windows.Win32.PInvoke.FindWindow(null, SaveFileDialogTitle);
            if (window != IntPtr.Zero)
            {
                return window;
            }
            if (retry-- <= 0)
            {
                logger?.LogWarning("could not found window \"{title}\"", SaveFileDialogTitle);
                break;
            }
        }
        return default;
    }

    private static async Task AutoSave(HWND handle, string filename, ILogger? logger = default, CancellationToken cancellationToken = default)
    {
        if (!Windows.Win32.PInvoke.SetForegroundWindow(handle))
        {
            logger?.LogWarning("{method}: {message}",
                nameof(Windows.Win32.PInvoke.SetForegroundWindow),
                Marshal.GetLastPInvokeErrorMessage());
            return;
        }
        await Task.Delay(200, cancellationToken);
        var view = Windows.Win32.PInvoke.FindWindowEx(handle, default, "DUIViewWndClassName", null);
        if (view == default)
        {
            logger?.LogWarning("could not found view");
            return;
        }
        var director = Windows.Win32.PInvoke.FindWindowEx(view, default, "DirectUIHWND", null);
        if (director == default)
        {
            logger?.LogWarning("director is gone");
            return;
        }
        var filenameInputLoations = new List<List<(HWND handle, string caption, string className)>>();
        BOOL ChildWindowFound(HWND handle, LPARAM param1)
        {
            var parentHandle = Windows.Win32.PInvoke.GetParent(handle);
            if (parentHandle != default)
            {
                string className, caption;
                using (var pool = MemoryPool<char>.Shared.Rent(30))
                {
                    var clsNameLen = Windows.Win32.PInvoke.GetClassName(handle, pool.Memory.Span);
                    className = pool.Memory.Span[..clsNameLen].ToString();

                    var capLen = Windows.Win32.PInvoke.GetWindowText(handle, pool.Memory.Span);
                    caption = pool.Memory.Span[capLen].ToString();
                }
                logger?.LogInformation("child window found: {handle} {className}", handle, className);
                if (filenameInputLoations.FirstOrDefault(it => it.Count > 0 && it[^1].handle == parentHandle)
                    is { } parent)
                {
                    parent.Add((handle, caption, className));
                }
                else
                {
                    filenameInputLoations.Add([(handle, caption, className)]);
                }
            }
            return true;
        }
        var enumerator = new Windows.Win32.UI.WindowsAndMessaging.WNDENUMPROC(ChildWindowFound);
        if (!Windows.Win32.PInvoke.EnumChildWindows(view, enumerator, default))
        {
            if (Marshal.GetLastPInvokeError() > 0)
            {
                logger?.LogWarning("{method}: {message}",
                    nameof(Windows.Win32.PInvoke.EnumChildWindows),
                    Marshal.GetLastPInvokeErrorMessage());
                return;
            }
        }
        HWND editor = default; //, confirm = default;
        foreach (var items in filenameInputLoations)
        {
            logger?.LogInformation("tree: {tree}", string.Join(", ", items.Select(it => $"\"{it.caption}\" {it.className}")));
            if (items.Count >= 3 &&
                items[^3].className == "FloatNotifySink" &&
                items[^2].className == "ComboBox" &&
                items[^1].className == "Edit")
            {
                editor = items[^1].handle;
            }
        }
        if (editor == default)
        {
            logger?.LogWarning("the editor has been fired");
            return;
        }
        Windows.Win32.PInvoke.SetFocus(editor);
        await Task.Delay(100, cancellationToken);
        foreach (var c in filename)
        {
            Windows.Win32.PInvoke.SendMessage(editor, WM_CHAR, c, IntPtr.Zero);
            if (Marshal.GetLastPInvokeError() != 0)
            {
                logger?.LogWarning("{method}: {message}",
                    nameof(WM_CHAR),
                    Marshal.GetLastPInvokeErrorMessage());
            }
        }
        var confirm = Windows.Win32.PInvoke.FindWindowEx(handle, default, "Button", "&Save");
        if (confirm == default)
        {
            logger?.LogWarning("press [Enter] pls . . .");
            return;
        }
        do
        {
            Windows.Win32.PInvoke.SetFocus(confirm);
            await Task.Delay(100, cancellationToken);
            Windows.Win32.PInvoke.SendMessage(confirm, WM_LBUTTONDOWN, default, default);
            if (Marshal.GetLastPInvokeError() != 0)
            {
                logger?.LogWarning("{method}: {message}",
                    nameof(WM_LBUTTONDOWN),
                    Marshal.GetLastPInvokeErrorMessage());
            }
            Windows.Win32.PInvoke.SetFocus(confirm);
            await Task.Delay(100, cancellationToken);
            Windows.Win32.PInvoke.SendMessage(confirm, WM_LBUTTONUP, default, default);
            if (Marshal.GetLastPInvokeError() != 0)
            {
                logger?.LogWarning("{method}: {message}",
                    nameof(WM_LBUTTONUP),
                    Marshal.GetLastPInvokeErrorMessage());
            }
        }
        while (Windows.Win32.PInvoke.IsWindow(handle));
    }
}