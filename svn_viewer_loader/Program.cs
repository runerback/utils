using aspire_webview2_wpf;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Serilog;
using svn.viewer.loader;

try
{
    using var cts = new CancellationTokenSource();
    Console.CancelKeyPress += (_, _) => cts.Cancel();

    var builder = Host.CreateApplicationBuilder();
    builder.Services.ConfigureAspireWebview2WpfLoader();
    builder.Services.AddHostedService<SvnViewerLoaderService>();
    builder.Services.AddSerilog(logger =>
    {
        logger.WriteTo.Console();
    });

    var app = builder.Build();
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.Error.WriteLine(exp);
}