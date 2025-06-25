using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;
using System.ComponentModel;
using System.Windows.Input;

namespace svn.viewer.loader;

public sealed class MainViewModel : INotifyPropertyChanged
{
    public MainViewModel() : this(default, default, default) { }

    public MainViewModel(
        string? uiAddress,
        string? hostAddress,
        CoreWebView2CreationProperties? webviewProps = default)
    {
        Title = "Svn Viewer - wv2:" + CoreWebView2Environment.GetAvailableBrowserVersionString();
        UIAddress = uiAddress;
        HostAddress = hostAddress;
        WebviewProps = webviewProps ?? new();
    }

    public string Title { get; }
    public string? UIAddress { get; }
    public string? HostAddress { get; }
    public CoreWebView2CreationProperties WebviewProps { get; }
    public LoadingViewModel LoadingContext { get; } = new LoadingViewModel { ViewSize = new(300, 300), };

    public bool UIReady { get; set; }

    private ICommand? _uiReadyCommand;
    public ICommand UIReadyCommand => _uiReadyCommand ??= new RelayCommand(OnUIReady);

    private void OnUIReady(object? obj)
    {
        UIReady = true;
        PropertyChanged?.Invoke(null, new PropertyChangedEventArgs(nameof(UIReady)));
    }

    private ICommand? _uiLoadingCommand;
    public ICommand UILoadingCommand => _uiLoadingCommand ??= new RelayCommand(OnUILoading);

    private void OnUILoading(object? obj)
    {
        UIReady = false;
        PropertyChanged?.Invoke(null, new PropertyChangedEventArgs(nameof(UIReady)));
    }

    public bool HostReady { get; set; }

    private ICommand? _hostReadyCommand;
    public ICommand HostReadyCommand => _hostReadyCommand ??= new RelayCommand(OnHostReady);

    private void OnHostReady(object? obj)
    {
        HostReady = true;
        PropertyChanged?.Invoke(null, new PropertyChangedEventArgs(nameof(HostReady)));
    }

    private ICommand? _hostLoadingCommand;
    public ICommand HostLoadingCommand => _hostLoadingCommand ??= new RelayCommand(OnHostLoading);

    private void OnHostLoading(object? obj)
    {
        HostReady = false;
        PropertyChanged?.Invoke(null, new PropertyChangedEventArgs(nameof(HostReady)));
    }

    public event PropertyChangedEventHandler? PropertyChanged;
}