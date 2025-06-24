using System.ComponentModel.DataAnnotations;
using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;

using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, _) => cts.Cancel();

var builder = WebApplication.CreateBuilder(args);

var app = builder.Build();

app.MapPost("/pickdir", async ([FromQuery] string? path, CancellationToken cancellationToken) =>
{
    var result = await Task.Run(async () =>
    {
        var tcs = new TaskCompletionSource<string?>();
        var sta = new Thread(state =>
        {
            try
            {
                var container = new Form
                {
                    TopMost = true,
                    Width = 1,
                    Height = 1,
                    FormBorderStyle = FormBorderStyle.None,
                };
                container.Show();
                container.BringToFront();
                Task.Delay(500).ContinueWith(_ => container
                    .BeginInvoke(() => container.BringToFront()));
                container.BringToFront();
                try
                {
                    var dialog = new FolderBrowserDialog();
                    if (state is string initDir)
                    {
                        dialog.InitialDirectory = initDir;
                    }
                    if (dialog.ShowDialog(owner: container) == DialogResult.OK)
                    {
                        tcs.SetResult(dialog.SelectedPath);
                    }
                    else
                    {
                        tcs.SetResult(null);
                    }
                }
                finally
                {
                    container.Close();
                }
            }
            catch (Exception ex)
            {
                app.Logger.LogError("open dialog failed: {error}", ex);
                tcs.SetResult(null);
            }
        });
        sta.TrySetApartmentState(ApartmentState.STA);
        sta.Start(path);
        return await tcs.Task;
    });
    if (result is { Length: > 0 } choosen)
    {
        return Results.Text(choosen);
    }
    return Results.NoContent();
});

app.MapPost("/opendir", async ([FromQuery, Required] string path, CancellationToken cancellationToken) =>
{
    if (!string.IsNullOrWhiteSpace(path) && OperatingSystem.IsWindows())
    {
        var explorer = Process.Start("explorer", $"/select,{path.Replace('/', '\\')}");
        if (explorer != null)
        {
            await explorer.WaitForExitAsync(cancellationToken);
        }
    }
    return Results.NoContent();
});


app.MapPost("/win/exec", ([FromBody, Required] WinExecRequestModel request, CancellationToken cancellationToken) =>
{
    if (!string.IsNullOrWhiteSpace(request.Executable) && OperatingSystem.IsWindows())
    {
        try
        {
            Process.Start(new ProcessStartInfo(request.Executable)
            {
                Arguments = request.Args?.Length > 0 ? string.Join(' ', request.Args) : "",
                UseShellExecute = true,
            });
        }
        catch { }
    }
});

try
{
    await app.RunAsync(cts.Token);
}
catch (Exception exp)
{
    Console.WriteLine(exp);
}