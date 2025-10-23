using aspire_webview2_wpf;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace svn.viewer.loader;

internal sealed class SvnViewerLoaderService(
    IAspireWebview2WpfLoader uiLoader,
    IHostApplicationLifetime lifetime,
    IConfiguration configuration,
    ILogger<SvnViewerLoaderService> logger)
    : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        try
        {
            var aspireProjectPath = configuration.GetValue<string?>("aspireProjectPath");
            if (string.IsNullOrWhiteSpace(aspireProjectPath))
            {
                logger.LogWarning("invalid configuration: missing `aspireProjectPath`");
                Environment.ExitCode = -1;
                return;
            }
            var settings = new AspireWebview2WpfLoaderSettings(
                title: "Svn Viewer",
                icon: "assets/SVNLogo.ico");
            await uiLoader.Run(aspireProjectPath, settings, cancellationToken: cancellationToken);
        }
        catch (Exception exp)
        {
            logger.LogError(exp, "");
        }
        finally
        {
            lifetime.StopApplication();
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
}