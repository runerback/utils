export const serverPort = process.env["PORT"];
export const messageHubUri = process.env["services__messages__http__0"];
export const uiHelperUri = process.env["services__uihelper__http__0"];
export const svnUri = `http://127.0.0.1:${process.env["SVN_PORT"]}/svn`;

export const settings: Settings = {
  svn_root: "",
  svn_repo: "",
  svn_rev: "",
  dark_theme: false,
};
