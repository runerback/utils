$root = $PWD.Path
try {
    cd ./svn_viewer_loader/
    & dotnet run
} finally {
    cd $root
}
