import Cache from "node-cache";

const requestCache = new Cache();
const resultCache = new Cache();
// TODO: from configure
const PENDING_CACHE_TTL = 5 * 60;
const RESULT_CACHE_TTL = 1 * 60;

const tasks = {
  pending: (request: string, id: string) => {
    requestCache.set(id, request, PENDING_CACHE_TTL);
  },
  set: (id: string, payload: Object) => {
    const request = requestCache.get(id) as string;
    if (!!request) {
      resultCache.set(request, { id, payload }, RESULT_CACHE_TTL);
    }
  },
  get: <TPayload>(body: string) => {
    return resultCache.get(body) as { id: string; payload: TPayload };
  },
};

export default {
  fetch_info: {
    pending: (body: string, id: string) => {
      tasks.pending(body, id);
    },
    set: (id: string, payload: SvnTreeNodeInfo) => {
      tasks.set(id, payload);
    },
    get: (body: string) => {
      return tasks.get<SvnTreeNodeInfo>(body);
    },
  },
};
