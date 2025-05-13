internal sealed class SvnPortResource : IResourceWithServiceDiscovery
{
    public string Name { get; } = "SVN-PORT";

    public ResourceAnnotationCollection Annotations { get; } = [new SvnPortResourceAnnotation()];

    private sealed class SvnPortResourceAnnotation : IResourceAnnotation
    {
        public int Port { get; set; }
    }
}