declare type SvnSettings = {
  readonly svn_root: string;
};

declare type Job = "IDLE" | "FETCH_SETTINGS" | "FETCH_STATUS" | "FETCH_DIFFS";

declare type Message = {
  readonly id: string;
  readonly content?: string;
};

declare type MessageContent = {
  readonly processing?: boolean;
  readonly data?: string;
  readonly error?: string;
  readonly completed?: boolean;
  readonly timestamp: string;
};

declare type SvnChangelistName = "NO_CHANGE_LIST" | string;

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

declare type ChunkSection = {
  readonly summary: string;
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
