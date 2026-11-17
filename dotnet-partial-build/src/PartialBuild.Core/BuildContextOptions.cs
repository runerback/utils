namespace PartialBuild.Core;

public sealed class BuildContextOptions
{
    public string RootProjectPath { get; set; } = string.Empty;

    public string Configuration { get; set; } = "Debug";

    public string? TargetFramework { get; set; }

    public string? RuntimeIdentifier { get; set; }

    public string? Platform { get; set; }

    public bool RestoreBeforeBuild { get; set; } = true;

    public Dictionary<string, string> AdditionalProperties { get; set; } = new(StringComparer.OrdinalIgnoreCase);

    public string GetNormalizedRootProjectPath()
    {
        return NormalizeProjectPath(RootProjectPath);
    }

    public BuildContextOptions CreateNormalizedCopy()
    {
        return new BuildContextOptions
        {
            RootProjectPath = GetNormalizedRootProjectPath(),
            Configuration = Configuration,
            TargetFramework = TargetFramework,
            RuntimeIdentifier = RuntimeIdentifier,
            Platform = Platform,
            RestoreBeforeBuild = RestoreBeforeBuild,
            AdditionalProperties = new Dictionary<string, string>(AdditionalProperties, StringComparer.OrdinalIgnoreCase)
        };
    }

    public Dictionary<string, string> GetGlobalProperties()
    {
        var properties = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        if (!string.IsNullOrWhiteSpace(Configuration))
        {
            properties["Configuration"] = Configuration;
        }

        if (!string.IsNullOrWhiteSpace(TargetFramework))
        {
            properties["TargetFramework"] = TargetFramework;
        }

        if (!string.IsNullOrWhiteSpace(RuntimeIdentifier))
        {
            properties["RuntimeIdentifier"] = RuntimeIdentifier;
        }

        if (!string.IsNullOrWhiteSpace(Platform))
        {
            properties["Platform"] = Platform;
        }

        foreach (var pair in AdditionalProperties)
        {
            if (!string.IsNullOrWhiteSpace(pair.Key))
            {
                properties[pair.Key] = pair.Value ?? string.Empty;
            }
        }

        return properties;
    }

    public Dictionary<string, string> GetReferencedProjectGlobalProperties()
    {
        var properties = GetGlobalProperties();
        properties.Remove("TargetFramework");
        return properties;
    }

    public string GetContextKey()
    {
        var additionalProperties = AdditionalProperties
            .Where(pair => !string.IsNullOrWhiteSpace(pair.Key))
            .OrderBy(pair => pair.Key, StringComparer.OrdinalIgnoreCase)
            .Select(pair => $"{pair.Key}={pair.Value}");

        return string.Join(
            "|",
            GetNormalizedRootProjectPath(),
            Configuration ?? string.Empty,
            TargetFramework ?? string.Empty,
            RuntimeIdentifier ?? string.Empty,
            Platform ?? string.Empty,
            string.Join("|", additionalProperties));
    }

    public static string NormalizeProjectPath(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            throw new ArgumentException("A project path is required.", nameof(path));
        }

        return Path.GetFullPath(Environment.ExpandEnvironmentVariables(path.Trim()));
    }
}
