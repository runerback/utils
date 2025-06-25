using System.Windows;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace svn.viewer.loader;

public class YourWebView2 : WebView2
{
    public YourWebView2()
    {
        if (OperatingSystem.IsWindowsVersionAtLeast(10, 0, 17763))
        {
            CoreWebView2InitializationCompleted += OnCoreWebView2Initialized;
        }
        else
        {
            Console.WriteLine("require windows version 10.0.17763 at least to work");
        }
    }

    public static readonly RoutedEvent ReadyEvent = EventManager.RegisterRoutedEvent(
        name: "Ready",
        routingStrategy: RoutingStrategy.Direct,
        handlerType: typeof(RoutedEventHandler),
        ownerType: typeof(YourWebView2));

    public event RoutedEventHandler Ready
    {
        add { AddHandler(ReadyEvent, value); }
        remove { RemoveHandler(ReadyEvent, value); }
    }

    public static readonly RoutedEvent LoadingEvent = EventManager.RegisterRoutedEvent(
        name: "Loading",
        routingStrategy: RoutingStrategy.Direct,
        handlerType: typeof(RoutedEventHandler),
        ownerType: typeof(YourWebView2));

    public event RoutedEventHandler Loading
    {
        add { AddHandler(LoadingEvent, value); }
        remove { RemoveHandler(LoadingEvent, value); }
    }

    private void OnCoreWebView2Initialized(object? sender, CoreWebView2InitializationCompletedEventArgs e)
    {
        if (e.IsSuccess && sender is WebView2 wv2 && wv2.CoreWebView2 is { } core)
        {
            core.ContentLoading += OnDOMLoading;
            core.DOMContentLoaded += OnDOMContentLoaded;
        }
    }

    private void OnDOMLoading(object? sender, CoreWebView2ContentLoadingEventArgs e)
    {
        RaiseEvent(new RoutedEventArgs(LoadingEvent));
    }

    private void OnDOMContentLoaded(object? sender, CoreWebView2DOMContentLoadedEventArgs e)
    {
        RaiseEvent(new RoutedEventArgs(ReadyEvent));
    }
}