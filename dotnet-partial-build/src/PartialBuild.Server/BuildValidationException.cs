namespace PartialBuild.Server;

public sealed class BuildValidationException(IEnumerable<string> errors) : Exception("The build plan cannot be started because one or more reused projects are missing compatible outputs.")
{

    public IReadOnlyList<string> Errors { get; } = errors.ToList();
}
