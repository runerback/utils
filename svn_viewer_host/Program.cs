using System.Diagnostics;

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, _) => cts.Cancel();

string? svn_executable = default;
try
{
    var finder = Process.Start(new ProcessStartInfo("powershell", "-c (Get-Command svn).Source")
    {
        UseShellExecute = false,
        RedirectStandardOutput = true,
    });
    if (finder == null)
    {
        Console.WriteLine("could not launch powershell process");
        return;
    }
    finder.BeginOutputReadLine();
    finder.OutputDataReceived += (_, e) =>
    {
        if (svn_executable == null && !string.IsNullOrWhiteSpace(e.Data))
        {
            var path = e.Data.Trim();
            if (File.Exists(path))
            {
                svn_executable = path.Replace('\\', '/');
            }
        }
    };
    await finder.WaitForExitAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
    return;
}
if (string.IsNullOrWhiteSpace(svn_executable))
{
    Console.WriteLine("svn executable not found");
    return;
}

var builder = DistributedApplication.CreateBuilder(args);

FreeTCPPortRangeProvider.Fetch();

var messages = builder.AddProject<Projects.svn_viewer_messages>("messages")
    .WithExternalHttpEndpoints();

#pragma warning disable ASPIREHOSTINGPYTHON001
var svnPorts = FreeTCPPortRangeProvider.RandomPorts(2);

var svn = builder.AddPythonApp("svn", "../svn_viewer_svn", "main.py")
    .WithReference(messages).WaitFor(messages)
    .WithEnvironment("INNGEST_PORT", svnPorts[0].ToString())
    .WithEnvironment("INNGEST_DEV_PORT", svnPorts[1].ToString())
    .WithEnvironment("INNGEST_SIGNING_KEY", builder.Configuration.GetSection("INNGEST_SIGNING_KEY").Value)
    .WithEnvironment("INNGEST_EVENT_KEY", builder.Configuration.GetSection("INNGEST_EVENT_KEY").Value)
    .WithEnvironment("INNGEST_DEV", "0")
    .WithEnvironment("SVN_EXECUTABLE", svn_executable)
    .WithOtlpExporter()
    .WithExternalHttpEndpoints();

var svncli = builder.AddNpmApp("svncli", "../svn_viewer_svn_dev")
    .WithReference(svn).WaitFor(svn)
    .WithHttpEndpoint(env: "PORT", targetPort: svnPorts[1])
    .WithEnvironment("INNGEST_PORT", svnPorts[0].ToString());
#pragma warning restore ASPIREHOSTINGPYTHON001

var ui_helper = builder.AddProject<Projects.svn_viewer_ui_helper>("uihelper")
    .WithExternalHttpEndpoints();

var server = builder.AddNpmApp("server", "../svn_viewer_server")
    .WithReference(ui_helper).WaitFor(ui_helper)
    .WithReference(svn).WaitFor(svn)
    .WithEnvironment("BROWSER", "none")
    .WithEnvironment("INNGEST_PORT", svnPorts[0].ToString())
    .WithEnvironment("INNGEST_EVENT_KEY", builder.Configuration.GetSection("INNGEST_EVENT_KEY").Value)
    .WithHttpEndpoint(env: "SERVER_PORT");

var ui = builder.AddNpmApp("ui", "../svn_viewer_ui", scriptName: "dev")
    .WithReference(ui_helper).WaitFor(ui_helper)
    .WithReference(server).WaitFor(server)
    .WithEnvironment("BROWSER", "none")
    .WithExternalHttpEndpoints()
    .WithHttpEndpoint(env: "UI_PORT");

var app = builder.Build();

try
{
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}