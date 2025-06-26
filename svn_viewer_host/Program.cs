using System.Diagnostics;
using Microsoft.Extensions.Configuration;

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, _) => cts.Cancel();

var executables = new Executables();
await executables.Fetch();
try
{
    await executables.Fetch();
}
catch (Exception exp)
{
    Console.WriteLine(exp);
    return;
}
if (string.IsNullOrWhiteSpace(executables.Svn))
{
    Console.WriteLine("svn executable not found");
    return;
}

FreeTCPPortRangeProvider.Fetch();

var builder = DistributedApplication.CreateBuilder(args);

var messages = builder.AddProject<Projects.svn_viewer_messages>("messages");

var thridPartyPorts = FreeTCPPortRangeProvider.RandomPorts(2);
var svn_port = thridPartyPorts[0];
var server_port = thridPartyPorts[1];

#pragma warning disable ASPIREHOSTINGPYTHON001
var svn = builder.AddPythonApp("svn", "../svn_viewer_svn", "main.py")
    .WithReference(messages).WaitFor(messages)
    .WithEnvironment("SVN_EXECUTABLE", executables.Svn.Replace('\\', '/'))
    .WithEnvironment("PYTHONIOENCODING", "utf-8")
    .WithOtlpExporter()
    .WithEnvironment("PORT", svn_port.ToString())
    .WithExternalHttpEndpoints();
if (!string.IsNullOrWhiteSpace(executables.TortoiseSvn))
{
    svn.WithEnvironment("TORTOISE_SVN_EXECUTABLE", executables.TortoiseSvn.Replace('\\', '/'));
}
var scheduler_parallel = builder.Configuration.GetValue<int>("SCHEDULER_PARALLEL");
if (scheduler_parallel > 0)
{
    svn.WithEnvironment("SCHEDULER_PARALLEL", scheduler_parallel.ToString());
}
var scheduler_interval = builder.Configuration.GetValue<double>("SCHEDULER_INTERVAL");
if (scheduler_interval > 0)
{
    svn.WithEnvironment("SCHEDULER_INTERVAL", scheduler_interval.ToString());
}
#pragma warning restore ASPIREHOSTINGPYTHON001

var ui_helper = builder.AddProject<Projects.svn_viewer_ui_helper>("uihelper");

var server = builder.AddNpmApp("server", "../svn_viewer_server")
    .WithReference(messages).WaitFor(messages)
    .WithReference(ui_helper).WaitFor(ui_helper)
    .WithReference(svn).WaitFor(svn)
    .WithEnvironment("SVN_PORT", svn_port.ToString())
    .WithEnvironment("PORT", server_port.ToString());

var ui = builder.AddNpmApp("ui", "../svn_viewer_ui", scriptName: "dev")
    .WithReference(messages).WaitFor(messages)
    .WithReference(ui_helper).WaitFor(ui_helper)
    .WithReference(server).WaitFor(server)
    .WithEnvironment("BROWSER", "none")
    .WithEnvironment("SERVER_PORT", server_port.ToString())
    .WithHttpEndpoint(env: "PORT")
    .WithExternalHttpEndpoints();
var client_task_parallel = builder.Configuration.GetValue<int>("CLIENT_TASK_PARALLEL");
if (client_task_parallel > 0)
{
    ui.WithEnvironment("CLIENT_TASK_PARALLEL", client_task_parallel.ToString());
}

var app = builder.Build();

try
{
    builder.Eventing.Subscribe<ResourceReadyEvent>((e, c) =>
    {
        switch (e.Resource.Name)
        {
            case "ui":
                if (e.Resource.TryGetUrls(out var urls))
                {
                    foreach (var uri in urls)
                    {
                        if (!string.IsNullOrWhiteSpace(uri.Url))
                        {
#if LOADER
                            Console.WriteLine($"The UI address is: {uri.Url}");
#else
                            Process.Start(new ProcessStartInfo
                            {
                                UseShellExecute = true,
                                FileName = uri.Url,
                            });
#endif
                            break;
                        }
                    }
                }
                break;
            default: break;
        }
        return Task.CompletedTask;
    });

    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}