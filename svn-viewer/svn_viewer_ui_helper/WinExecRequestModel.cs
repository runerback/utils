using System.ComponentModel.DataAnnotations;

internal sealed class WinExecRequestModel(string executable, string[]? args)
{
    [Required]
    public string Executable { get; } = executable;
    public string[]? Args { get; } = args;
}