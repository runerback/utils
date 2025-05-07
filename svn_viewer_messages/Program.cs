using System.ComponentModel.DataAnnotations;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, _) => cts.Cancel();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSignalR();

var app = builder.Build();

app.MapPost("/message", async ([FromQuery, Required] string id, HttpContext context, [FromServices] IHubContext<MessageHub> hub, CancellationToken cancellationToken) =>
{
    var reader = new StreamReader(context.Request.Body);
    var content = await reader.ReadToEndAsync(cancellationToken);
    await hub.Clients.All.SendAsync("message", JsonSerializer.Serialize(new { id, content }), cancellationToken);
    return Results.NoContent();
});

app.MapHub<MessageHub>("/messages");

try
{
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}