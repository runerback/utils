import { exec } from "child_process";

export const svn = await new Promise<string | undefined>((resolve, reject) => {
  try {
    const ps1 = exec(
      "powershell -c (Get-Command svn).Source",
      (error, stdout, stderror) => {
        if (!!stdout) {
          return resolve(stdout.trimEnd().replaceAll("\\", "/"));
        }
        if (!!error || !!stderror) {
          return reject(error || stderror);
        }
      }
    );
    ps1.on("close", () => {
      resolve(undefined);
    });
  } catch (error) {
    reject(error);
  }
});
if (!svn) {
  console.warn("svn not found");
} else {
  console.log("svn", [svn]);
}

export const serverPort = process.env["SERVER_PORT"];
export const messageHubUri = process.env["services__messages__http__0"];
export const uiHelperUri = process.env["services__uihelper__http__0"];
export const svnUri = `http://localhost:${process.env["INNGEST_PORT"]}/e/${process.env["INNGEST_EVENT_KEY"]}`;
console.log(svnUri);

export const settings = {
  svn_root: "",
  svn_root_hash: "",
  dark_theme: false,
};
