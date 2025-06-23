using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using Windows.Win32;
using Windows.Win32.Foundation;

namespace svn.viewer.loader;

partial class Program
{
    static int Main(string[] args)
    {
        Process? host = default!;
        void Cleanup()
        {
            try
            {
                if (host != null)
                {
                    try
                    {
                        try { PInvoke.GenerateConsoleCtrlEvent(0, (uint)host.Id); } catch { }
                        host.Kill();
                    }
                    finally
                    {
                        host = null;
                        Console.WriteLine("host process exited");
                    }
                }
            }
            catch { }
        }
        var hostProj =
#if BUILT_INSIDE_VS
            Path.GetFullPath("../../../../svn_viewer_host/svn_viewer_host.csproj")
#else
            Path.GetFullPath("../svn_viewer_host/svn_viewer_host.csproj")
#endif
            ;
        try
        {
            AppDomain.CurrentDomain.ProcessExit += (_, _) =>
            {
                Cleanup();
            };
            IntPtr hWnd = PInvoke.GetConsoleWindow();
            using var cts = new CancellationTokenSource();
            Console.CancelKeyPress += (_, _) => cts.Cancel();
            var cancellationToken = cts.Token;
            // load host
            string? uiAddress = default;
            string? serviceAddress = default;
            var hostFailedToStart = false;
            using (var hostHandle = new AutoResetEvent(false))
            {
                cancellationToken.Register(() => hostHandle.Set());
                host = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = "dotnet",
                        Arguments = $"run --project \"{hostProj}\" /p:DefineConstants=\"LOADER\"",
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardOutput = true,
                    },
                };
                host.Start();
                host.Exited += (_, e) =>
                {
                    hostHandle.Set();
                };
                host.OutputDataReceived += (_, e) =>
                {
                    if (e.Data is { Length: > 0 } message)
                    {
                        Console.WriteLine(message);
                        if (message.Trim() == "Hosting failed to start")
                        {
                            hostFailedToStart = true;
                            hostHandle.Set();
                            return;
                        }
                        if (string.IsNullOrWhiteSpace(serviceAddress) &&
                            ServiceAddrPattern().Match(message) is { Success: true } serviceAddrMatch &&
                            serviceAddrMatch.Groups["addr"] is { Success: true } serviceAddrGroup)
                        {
                            serviceAddress = serviceAddrGroup.Value;
                        }
                        if (string.IsNullOrWhiteSpace(uiAddress) &&
                            UIAddrPattern().Match(message) is { Success: true } uiAddrMatch &&
                            uiAddrMatch.Groups["addr"] is { Success: true } uiAddrGroup)
                        {
                            uiAddress = uiAddrGroup.Value;
                            hostHandle.Set();
                        }
                    }
                };
                host.BeginOutputReadLine();
                hostHandle.WaitOne(); // wait for addresses
                if (hostFailedToStart)
                {
                    return -1;
                }
                host.CancelOutputRead();
                Console.WriteLine("host detached");
            }
            cancellationToken.ThrowIfCancellationRequested();
            if (string.IsNullOrWhiteSpace(serviceAddress) && string.IsNullOrWhiteSpace(uiAddress))
            {
                Console.WriteLine("Something went wrong with host process");
                return 0;
            }
            if (hWnd == IntPtr.Zero)
            {
                Console.WriteLine("Console window not found");
            }
            else
            {
                PInvoke.ShowWindow((HWND)hWnd, Windows.Win32.UI.WindowsAndMessaging.SHOW_WINDOW_CMD.SW_HIDE);
                Console.WriteLine("Console window hidden");
            }
            cancellationToken.ThrowIfCancellationRequested();
            var sta = new Thread(() =>
            {
                var app = new Application();
                app.LoadCompleted += (_, _) =>
                {
                    app.MainWindow.BringIntoView();
                    app.MainWindow.Focus();
                };
                app.Exit += (_, _) => Cleanup();
                app.Run(new MainWindow
                {
                    DataContext = new MainViewModel(uiAddress: uiAddress, serviceAddress: serviceAddress),
                });
            });
            sta.TrySetApartmentState(ApartmentState.STA);
            sta.Start();
            Console.WriteLine("wpf thread started");
            sta.Join();
            Console.WriteLine("wpf thread exited");
            return 0;
        }
        catch (Exception exp)
        {
            Console.WriteLine(exp);
            return -1;
        }
        finally
        {
            Cleanup();
        }
    }

    [GeneratedRegex(@".*Login\s+to\s+the\s+dashboard\s+at\s+(?<addr>http.+login\?t\=\w+)", RegexOptions.Compiled)]
    private static partial Regex ServiceAddrPattern();
    [GeneratedRegex(@".*The\s+UI\s+address\s+is\:\s+(?<addr>http.+)", RegexOptions.Compiled)]
    private static partial Regex UIAddrPattern();
}