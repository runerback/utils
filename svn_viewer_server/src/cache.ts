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
    console.log("[💾cache] pending", id, request);
  },
  set: (id: string, payload: Object, job: Job) => {
    const request = requestCache.get(id) as string;
    if (!!request) {
      resultCache.set(request, { id, payload, job }, RESULT_CACHE_TTL);
      console.log("[💾cache] set", id, request);
    } else {
      console.log("[💾cache] set: request not found", id);
    }
  },
  get: <TPayload>(request: string) => {
    return resultCache.get(request) as {
      id: string;
      payload: TPayload;
      job: Job;
    };
  },
  clear: (request: string) => {
    resultCache.del(request);
    console.log("[💾cache] reset", request);
  },
};

export default {
  fetch_info: {
    pending: (request: string, id: string) => {
      tasks.pending(request, id);
    },
    set: (id: string, payload: SvnTreeNodeInfo, job: Job) => {
      tasks.set(id, payload, job);
    },
    get: (request: string) => {
      return tasks.get<SvnTreeNodeInfo>(request);
    },
    reset: (request: string) => {
      tasks.clear(request);
    },
  },
  fetch_status: {
    pending: (request: string, id: string) => {
      tasks.pending(request, id);
    },
    set: (id: string, payload: SvnStatus[], job: Job) => {
      tasks.set(id, payload, job);
    },
    get: (request: string) => {
      return tasks.get<SvnStatus[]>(request);
    },
    reset: (request: string) => {
      tasks.clear(request);
    },
  },
  fetch_logs: {
    pending: (request: string, id: string) => {
      tasks.pending(request, id);
    },
    set: (id: string, payload: SvnLog[], job: Job) => {
      tasks.set(id, payload, job);
    },
    get: (request: string) => {
      return tasks.get<SvnLog[]>(request);
    },
    reset: (request: string) => {
      tasks.clear(request);
    },
  },
};
