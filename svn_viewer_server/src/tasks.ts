import { Inngest } from "inngest";

export const inngest = new Inngest({
  id: "svn",
  eventKey: "SVN_EVENTS",
  fetch: fetch.bind(globalThis),
});

export const events = {
  "run-cli": "inngest/cli-port-ready",
};

const helloWorld = inngest.createFunction(
  { id: "hello-world" },
  { event: "test/hello.world" },
  async ({ event, step }) => {
    await step.sleep("wait-a-moment", "1s");
    return { message: `Hello ${event.data?.email}!` };
  }
);

const runCli = inngest.createFunction(
  {
    id: "run-cli",
  },
  {
    event: events["run-cli"],
  },
  async ({ event, step }) => {
    console.log(event);
    await step.run("start-cli-in-powershell", async () => {
      return { nothing: "here" };
    });
  }
);

export const functions = [helloWorld, runCli];
