export const svn = await new Promise<string | undefined>((resolve, reject) => {
  resolve("echo");
});
if (!svn) {
  console.warn("svn not found");
} else {
  console.log("svn", [svn]);
}

export const serverPort = process.env["PORT"];
// export const messageHubUri = process.env["services__messages__http__0"];
export const uiHelperUri = process.env["services__uihelper__http__0"];
export const svnUri = `http://127.0.0.1:${process.env["SVN_PORT"]}/svn`;

export const settings = {
  svn_root: "",
  svn_root_hash: "",
  dark_theme: false,
};
