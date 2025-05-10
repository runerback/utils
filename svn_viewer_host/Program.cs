var builder = DistributedApplication.CreateBuilder(args);

var messages = builder.AddProject<Projects.svn_viewer_messages>("messages")
    .WithExternalHttpEndpoints();

var ui_helper = builder.AddProject<Projects.svn_viewer_ui_helper>("uihelper")
    .WithExternalHttpEndpoints();

var server = builder.AddNpmApp("server", "../svn_viewer_server")
    .WithReference(messages).WaitFor(messages)
    .WithReference(ui_helper).WaitFor(ui_helper)
    .WithEnvironment("BROWSER", "none")
    .WithExternalHttpEndpoints()
    .WithHttpEndpoint(env: "SERVER_PORT");

var ui = builder.AddNpmApp("ui", "../svn_viewer_ui", scriptName: "dev")
    .WithReference(messages).WaitFor(messages)
    .WithReference(server).WaitFor(server)
    .WithReference(ui_helper).WaitFor(ui_helper)
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