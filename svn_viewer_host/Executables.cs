using System.Diagnostics;

internal sealed class Executables
{
    private string? _svn;
    public string? Svn => _svn;

    private string? _tortoisesvn;
    public string? TortoiseSvn => _tortoisesvn;

    public async Task Fetch(CancellationToken cancellationToken = default)
    {
        var exceptions = new List<Exception>();
        if (string.IsNullOrWhiteSpace(_svn))
        {
            try
            {
                _svn = await Fetch("svn", cancellationToken);
            }
            catch (Exception exp)
            {
                exceptions.Add(exp);
            }
        }
        if (string.IsNullOrWhiteSpace(_tortoisesvn))
        {
            try
            {
                _tortoisesvn = await Fetch("TortoiseProc", cancellationToken);
            }
            catch (Exception exp)
            {
                exceptions.Add(exp);
            }
        }
        if (exceptions.Count > 0)
        {
            throw new AggregateException(exceptions);
        }
    }

    private static async Task<string> Fetch(string executable, CancellationToken cancellationToken)
    {
        string? result = default;
        var finder = Process.Start(new ProcessStartInfo("powershell", $"-c (Get-Command {executable}).Source")
        {
            UseShellExecute = false,
            RedirectStandardOutput = true,
        }) ?? throw new InvalidOperationException("could not launch powershell process");
        finder.BeginOutputReadLine();
        finder.OutputDataReceived += (_, e) =>
        {
            if (string.IsNullOrWhiteSpace(result) && !string.IsNullOrWhiteSpace(e.Data))
            {
                var path = e.Data.Trim();
                if (File.Exists(path))
                {
                    result = path.Replace('\\', '/');
                    finder.CancelOutputRead();
                }
            }
        };
        await finder.WaitForExitAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(result))
        {
            throw new InvalidOperationException($"{executable} executable not found");
        }
        return result;
    }
}