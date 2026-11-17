namespace PartialBuild.Server;

public sealed class FileSystemEntryResponse
{
    public required string Name { get; init; }

    public required string FullPath { get; init; }
}

public sealed class FileSystemBrowseResponse
{
    public string? CurrentPath { get; init; }

    public string? ParentPath { get; init; }

    public List<FileSystemEntryResponse> Directories { get; init; } = new();

    public List<FileSystemEntryResponse> Projects { get; init; } = new();
}
