using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace svn.viewer.loader;

public sealed class MainViewModel
{
    public MainViewModel() : this(default, default, default) { }

    public MainViewModel(
        string? uiAddress,
        string? serviceAddress,
        CoreWebView2CreationProperties? webviewProps = default)
    {
        Title = "Svn Viewer - wv2:" + CoreWebView2Environment.GetAvailableBrowserVersionString();
        UiAddress = uiAddress;
        ServiceAddress = serviceAddress;
        WebviewProps = webviewProps ?? new();
    }

    public string Title { get; }
    public string? UiAddress { get; }
    public string? ServiceAddress { get; }
    public CoreWebView2CreationProperties WebviewProps { get; }
}