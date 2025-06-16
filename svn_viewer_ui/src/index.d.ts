declare type Settings = {
  readonly svn_root: string;
  readonly dark_theme?: boolean;
};

declare type Job =
  | "IDLE"
  | "FETCH_SETTINGS"
  | "FETCH_STATUS"
  | "FETCH_DIFFS"
  | "FETCH_UNVERSIONED"
  | "FETCH_LOGS"
  | "FETCH_LOG_DIFFS";

declare type Message = {
  readonly id: string;
  readonly content?: string;
};

declare type MessageContent = {
  readonly job?: Job;
  readonly processing?: boolean;
  readonly data?: string | object;
  readonly error?: string;
  readonly completed?: boolean;
  readonly timestamp: string;
};

declare type SvnChangelistName = "NO-CHANGE-LIST" | string;

declare type SvnStatusItem = {
  readonly state: string;
  readonly source: string;
};

declare type SvnStatus = {
  readonly changelist: SvnChangelistName;
  readonly changes: SvnStatusItem[];
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

declare type SvnLog = {
  readonly revision: string;
  readonly author?: string;
  readonly timestamp?: string;
  message?: string;
  readonly changes?: number;
};

declare type SvnLogs = {
  readonly status?: SvnStatusItem;
  readonly logs?: SvnLog[];
};

declare type SvnProviderStream = {
  readonly id: string;
  readonly job?: Job;
  readonly chunks?: Chunk1[];
  readonly logs?: SvnLogs[];
  readonly unversioned?: string[];
  readonly finished?: boolean;
};

declare type FetchLogDiffsRange = {
  n?: number;
  m?: number;
};

declare type SvnLogDiffsProviderStream = {
  readonly id: string;
  readonly job?: Job;
  readonly chunks?: Chunk1[];
  readonly finished?: boolean;
} & FetchLogDiffsRange;
