using System.Diagnostics;
using System.Text.RegularExpressions;

internal sealed partial class FreeTCPPortRangeProvider
{
    private static readonly List<(int start, int end)> freePortRanges = [];

    public static int RandomPort(int min = 30000, int max = 50000)
    {
        var nextRange = Random.Shared.GetItems(freePortRanges.Where(it => it.start > min && it.start < max).ToArray(), 1);
        return Random.Shared.GetItems(Enumerable.Range(nextRange[0].start, nextRange[0].end - nextRange[0].start).ToArray(), 1)[0];
    }

    public static void Fetch()
    {
        if (freePortRanges.Count > 0)
        {
            return;
        }

        var netsh = Process.Start(new ProcessStartInfo(
            "powershell.exe",
            "& netsh interface ipv4 show excludedportrange protocol=tcp")
        {
            RedirectStandardOutput = true,
        }) ?? throw new InvalidOperationException("process no found");
        netsh.WaitForExit();
        var ipRanges = netsh.StandardOutput.ReadToEnd();
        var matches = PortRangePattern().Matches(ipRanges);
        var excludedPorts = new List<(int start, int end)>();
        foreach (var match in matches.OfType<Match>())
        {
            if (
                !int.TryParse(match.Groups["start"]?.Value, out var start) ||
                !int.TryParse(match.Groups["end"]?.Value, out var end))
            {
                continue;
            }

            excludedPorts.Add((start, end));
        }
        if (excludedPorts.Count > 1)
        {
            var previous = excludedPorts[0].end + 1;
            for (int i = 1, j = excludedPorts.Count; i < j; i++)
            {
                var (startPort, endPort) = excludedPorts[i];
                if (previous < startPort)
                {
                    freePortRanges.Add((previous, startPort - 1));
                }
                previous = endPort + 1;
            }
            if (previous < 60000)
            {
                freePortRanges.Add((previous, 60000));
            }
        }
    }

    [GeneratedRegex("(?<start>\\d+)\\s*(?<end>\\d+)")]
    private static partial Regex PortRangePattern();
}