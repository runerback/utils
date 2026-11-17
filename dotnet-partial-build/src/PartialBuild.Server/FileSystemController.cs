using Microsoft.AspNetCore.Mvc;

namespace PartialBuild.Server;

[ApiController]
[Route("api/filesystem")]
public sealed class FileSystemController : ControllerBase
{
    private const int EntryLimit = 500;

    [HttpGet("browse")]
    public ActionResult<FileSystemBrowseResponse> Browse([FromQuery] string? path)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(path))
            {
                return Ok(new FileSystemBrowseResponse
                {
                    Directories = [.. DriveInfo.GetDrives()
                        .Where(drive => drive.IsReady)
                        .Select(drive => new FileSystemEntryResponse
                        {
                            Name = drive.Name,
                            FullPath = drive.RootDirectory.FullName
                        })
                        .OrderBy(entry => entry.Name, StringComparer.OrdinalIgnoreCase)]
                });
            }

            var requestedPath = Path.GetFullPath(path.Trim());
            var currentPath = requestedPath;

            if (System.IO.File.Exists(requestedPath))
            {
                currentPath = Path.GetDirectoryName(requestedPath) ?? requestedPath;
            }

            if (!Directory.Exists(currentPath))
            {
                return NotFound(new { error = $"Directory '{currentPath}' was not found." });
            }

            var directories = SafeEnumerate(() => Directory.EnumerateDirectories(currentPath))
                .Take(EntryLimit)
                .Select(directory => new FileSystemEntryResponse
                {
                    Name = Path.GetFileName(directory.TrimEnd(Path.DirectorySeparatorChar)) is { Length: > 0 } name
                        ? name
                        : directory,
                    FullPath = directory
                })
                .OrderBy(entry => entry.Name, StringComparer.OrdinalIgnoreCase)
                .ToList();

            var projects = SafeEnumerate(() => Directory.EnumerateFiles(currentPath, "*.csproj", SearchOption.TopDirectoryOnly))
                .Take(EntryLimit)
                .Select(project => new FileSystemEntryResponse
                {
                    Name = Path.GetFileName(project),
                    FullPath = project
                })
                .OrderBy(entry => entry.Name, StringComparer.OrdinalIgnoreCase)
                .ToList();

            return Ok(new FileSystemBrowseResponse
            {
                CurrentPath = currentPath,
                ParentPath = Directory.GetParent(currentPath)?.FullName,
                Directories = directories,
                Projects = projects
            });
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or ArgumentException)
        {
            return BadRequest(new { error = exception.Message });
        }
    }

    private static IEnumerable<string> SafeEnumerate(Func<IEnumerable<string>> enumerate)
    {
        try
        {
            return enumerate().Where(path =>
            {
                try
                {
                    var attributes = System.IO.File.GetAttributes(path);
                    return (attributes & FileAttributes.ReparsePoint) == 0;
                }
                catch
                {
                    return false;
                }
            }).ToList();
        }
        catch
        {
            return [];
        }
    }
}
