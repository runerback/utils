import Cache from "node-cache";
import { SERVER_CACHE_PAYLOAD, SERVER_CACHE_PENDING } from "./settings.js";

const requestCache = new Cache();
const resultCache = new Cache();

const PENDING_CACHE_TTL =
  (SERVER_CACHE_PENDING ? parseInt(SERVER_CACHE_PENDING) : undefined) ?? 5 * 60;
const RESULT_CACHE_TTL =
  (SERVER_CACHE_PAYLOAD ? parseInt(SERVER_CACHE_PAYLOAD) : undefined) ?? 5 * 60;

const tasks = {
  pending: (request: string, id: string) => {
    requestCache.set(id, request, PENDING_CACHE_TTL);
  },
  set: (id: string, payload: Object, job: Job) => {
    const request = requestCache.get(id) as string;
    if (!!request) {
      resultCache.set(request, { id, payload, job }, RESULT_CACHE_TTL);
    }
  },
  get: <TPayload>(body: string) => {
    return resultCache.get(body) as { id: string; payload: TPayload; job: Job };
  },
};

export default {
  fetch_info: {
    pending: (body: string, id: string) => {
      tasks.pending(body, id);
    },
    set: (id: string, payload: SvnTreeNodeInfo, job: Job) => {
      tasks.set(id, payload, job);
    },
    get: (body: string) => {
      return tasks.get<SvnTreeNodeInfo>(body);
    },
  },
};
