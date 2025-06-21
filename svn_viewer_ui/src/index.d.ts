declare type Settings = {
  readonly svn_root: string;
  readonly svn_repo?: string;
  readonly svn_rev?: string;
  readonly dark_theme?: boolean;
};

declare type SettingsRequest = Pick<Settings, "svn_root" | "dark_theme">;

declare type SettingsRequestStream = SettingsRequest & {
  needToSync?: boolean;
};

declare type CreateJobResult = Promise<string | undefined>;

declare type INetwork = {
  readonly messages$: Observable<Message>;
  readonly errors$: Observable<any>;
  test_server: () => Promise<string | undefined>;
  get_settings: () => Promise<Settings | undefined>;
  update_settings: (settings: SettingsRequest) => Promise<string | undefined>;
  fetch_status: () => CreateJobResult;
  fetch_diff: (source: string) => CreateJobResult;
  fetch_unversioned: (source: string) => CreateJobResult;
  fetch_file_remote: (source: string) => CreateJobResult;
  fetch_logs: (source: string) => CreateJobResult;
  fetch_info: (source: string) => CreateJobResult;
  fetch_log_diffs: (
    source?: string,
    params?: FetchLogDiffsRange
  ) => CreateJobResult;
  fetch_file_status: (source: string) => CreateJobResult;
  fetch_tree: (source?: string) => CreateJobResult;
  pick_dir: (init?: string) => Promise<string | undefined>;
  open_in_dir: (path?: string) => Promise<void | undefined>;
};

declare type Job =
  | "FETCH_SETTINGS"
  | "FETCH_STATUS"
  | "FETCH_DIFFS"
  | "FETCH_UNVERSIONED"
  | "FETCH_LOGS"
  | "FETCH_LOG_DIFFS"
  | "FETCH_FILE_STATUS"
  | "FETCH_FILE_REMOTE"
  | "FETCH_TREE"
  | "FETCH_INFO";

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

declare type MessageStream = {
  readonly id: string;
  readonly content?: MessageContent;
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

declare type SvnDiffStream = {
  readonly id: string;
  readonly job?: Job;
  readonly chunks?: Chunk1[];
  readonly logs?: SvnLogs[];
  readonly unversioned?: string[];
  readonly missing?: string[];
  readonly finished?: boolean;
};

declare type FetchLogDiffsRange = {
  n?: number;
  m?: number;
};

declare type SvnLogDiffsStream = {
  readonly id: string;
  readonly job?: Job;
  readonly chunks?: Chunk1[];
  readonly finished?: boolean;
} & FetchLogDiffsRange;

declare type SvnTreeNode = {
  readonly kind: "FILE" | "DIR";
  readonly name: string;
  readonly expandable?: boolean;
};

declare type SvnTreeStream = {
  readonly id: string;
  readonly job?: Job;
  readonly nodes?: SvnTreeNode[];
  readonly finished?: boolean;
};

declare type SvnTreeNodeInfo = {
  readonly revision?: string;
  readonly lastChangedAuthor?: string;
  readonly lastChangedRev?: string;
  readonly lastChangedTime?: string;
};

declare type SvnInfoStream = {
  readonly id: string;
  readonly job?: Job;
  readonly info?: SvnTreeNodeInfo;
  readonly finished?: boolean;
};
