import { createGeneratedTextFileName, createTextFile } from "./clipboard";

const MAX_INLINE_TEXT_LENGTH = 4000;

export interface TextSubmissionResult {
  submitMode: string;
  routedAsFile: boolean;
  fileName: string | null;
}

export function buildTextSubmission(
  formData: FormData,
  trimmedText: string,
): TextSubmissionResult {
  if (trimmedText.length <= MAX_INLINE_TEXT_LENGTH) {
    formData.append("text", trimmedText);
    return { submitMode: "text", routedAsFile: false, fileName: null };
  }
  const fileName = createGeneratedTextFileName("message");
  formData.append("files", createTextFile(trimmedText, fileName));
  return { submitMode: "text", routedAsFile: true, fileName };
}
