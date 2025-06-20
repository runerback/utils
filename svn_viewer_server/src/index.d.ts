declare type Settings = {
  svn_root: string;
  svn_repo: string;
  svn_rev: string;
  dark_theme: boolean;
  fetched?: boolean;
};

declare type Message = {
  readonly id: string;
  readonly content?: string;
};

declare type Job =
  | "FETCH_SETTINGS"
  | "FETCH_STATUS"
  | "FETCH_DIFFS"
  | "FETCH_LOGS"
  | "FETCH_LOG_DIFFS"
  | "FETCH_TREE"
  | "FETCH_INFO";

declare type MessageContent = {
  readonly job?: Job;
  readonly processing?: boolean;
  readonly data?: string | object;
  readonly error?: string;
  readonly completed?: boolean;
  readonly timestamp: string;
};

declare type SvnChangelistName = "<NO-CHANGE-LIST>" | string;

declare type SvnStatusItem = {
  readonly state: string;
  readonly source: string;
};

declare type SvnStatus = {
  readonly changelist: SvnChangelistName;
  readonly changes: SvnStatusItem[];
};

declare type SvnLog = {
  readonly revision: string;
  readonly author?: string;
  readonly timestamp?: string;
  message?: string;
  readonly changes?: number;
};

declare type Chunk0 = {
  readonly index: string;
  readonly sections: Array<string>;
};

declare type ChunkSectionHunkType = "+" | "-";

// @@ -1,1 +1,1 @@
//    ab,c de,f
declare type ChunkSectionHunk = {
  readonly a: ChunkSectionHunkType;
  readonly b: number;
  readonly c: number;
  readonly d: ChunkSectionHunkType;
  readonly e: number;
  readonly f: number;
};

declare type ChunkSection = {
  readonly hunk: ChunkSectionHunk;
  readonly changes: string[];
};

declare type Chunk1 = {
  readonly index: string;
  readonly versions: {
    readonly indicator: string;
    readonly version: string;
  }[];
  readonly sections: ChunkSection[];
};

declare type SvnTreeNodeInfo = {
  revision?: string;
  lastChangedAuthor?: string;
  lastChangedRev?: string;
  lastChangedTime?: string;
};
