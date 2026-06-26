export interface ImageAttachment {
  name: string;
  url: string;
}

export interface VideoAttachment {
  name: string;
  url: string;
}

export interface FileAttachment {
  name: string;
  downloadUrl: string;
}

export interface Message {
  id: string;
  text: string;
  deviceType: "Phone" | "Tablet" | "Computer";
  clientTimestamp: string;
  images: ImageAttachment[];
  videos: VideoAttachment[];
  files: FileAttachment[];
}
