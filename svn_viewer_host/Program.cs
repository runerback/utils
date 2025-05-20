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

FreeTCPPortRangeProvider.Fetch();

var builder = DistributedApplication.CreateBuilder(args);

var messages = builder.AddProject<Projects.svn_viewer_messages>("messages");

var thridPartyPorts = FreeTCPPortRangeProvider.RandomPorts(2);
var svn_port = thridPartyPorts[0];
var server_port = thridPartyPorts[1];

#pragma warning disable ASPIREHOSTINGPYTHON001
var svn = builder.AddPythonApp("svn", "../svn_viewer_svn", "main.py")
    .WithReference(messages).WaitFor(messages)
    .WithEnvironment("SVN_EXECUTABLE", svn_executable)
    .WithEnvironment("PYTHONIOENCODING", "utf-8")
    .WithOtlpExporter()
    .WithEnvironment("PORT", svn_port.ToString())
    .WithExternalHttpEndpoints();
#pragma warning restore ASPIREHOSTINGPYTHON001

var ui_helper = builder.AddProject<Projects.svn_viewer_ui_helper>("uihelper");

var server = builder.AddNpmApp("server", "../svn_viewer_server")
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

var app = builder.Build();

try
{
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}