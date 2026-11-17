using Microsoft.AspNetCore.SignalR;

namespace PartialBuild.Server;

public sealed class BuildHub : Hub
{
    public static string GetBuildGroup(string buildId)
    {
        return $"build:{buildId}";
    }

    public static string GetGraphLoadGroup(string progressId)
    {
        return $"graph-load:{progressId}";
    }

    public Task JoinBuild(string buildId)
    {
        return Groups.AddToGroupAsync(Context.ConnectionId, GetBuildGroup(buildId));
    }

    public Task LeaveBuild(string buildId)
    {
        return Groups.RemoveFromGroupAsync(Context.ConnectionId, GetBuildGroup(buildId));
    }

    public Task JoinGraphLoad(string progressId)
    {
        return Groups.AddToGroupAsync(Context.ConnectionId, GetGraphLoadGroup(progressId));
    }

    public Task LeaveGraphLoad(string progressId)
    {
        return Groups.RemoveFromGroupAsync(Context.ConnectionId, GetGraphLoadGroup(progressId));
    }
}
