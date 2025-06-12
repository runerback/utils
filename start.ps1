$root = $PWD.Path
try {
    cd ./svn_viewer_host/
    & dotnet run
} finally {
    cd $root
}
