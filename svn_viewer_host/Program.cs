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
    .WithEnvironment("INNGEST_DEV", "1")
    .WithEnvironment("INNGEST_EVENT_KEY", "INNGEST-SVN")
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
    .WithEnvironment("INNGEST_EVENT_KEY", "INNGEST-SVN")
    .WithHttpEndpoint(env: "SERVER_PORT");

var ui = builder.AddNpmApp("ui", "../svn_viewer_ui", scriptName: "dev")
    .WithReference(server).WaitFor(server)
    .WithEnvironment("BROWSER", "none")
    .WithExternalHttpEndpoints()
    .WithHttpEndpoint(env: "UI_PORT");

var app = builder.Build();

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, _) => cts.Cancel();
try
{
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}