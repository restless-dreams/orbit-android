// Maps from other apps (OPML, FreeMind and Freeplane, XMind), read with Orbit for the app: a
// file's bytes come in as base64, and each map it holds goes back as orbit-md, with the pictures
// inside the file as base64 and what didn't come over. A file Orbit can't read goes back as an
// error with Orbit's code, which the app says in its own words.

import { importMap, OrbitImportError, serializeOrbitMarkdown } from '@restless-dreams/orbit';

/** One map a file holds. */
export interface ImportedFileMap {
  markdown: string;

  /** Its pictures, base64, by their names in the map's media folder. */
  media: Record<string, string>;

  warnings: { code: string; count: number; message: string }[];
}

/** What a file from another app makes: its maps, or why it makes none. */
export type ImportedFile =
  | { format: string; maps: ImportedFileMap[] }
  | { error: { code: string; message: string } };

/** The most bytes `fromBase64` and `toBase64` turn at once: well under the arguments a call may take. */
const CHUNK = 0x8000;

export const fromBase64 = (base64: string): Uint8Array => {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes;
};

export const toBase64 = (bytes: Uint8Array): string => {
  let binary = '';

  for (let start = 0; start < bytes.length; start += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(start, start + CHUNK));
  }

  return btoa(binary);
};

/** A file from another app, its bytes as base64, as maps; `untitled` names a map with several top ideas and nothing to name it after. */
export const importFile = (data: string, name: string | null, untitled: string): ImportedFile => {
  try {
    const result = importMap(fromBase64(data), { name: name ?? undefined, untitled });

    return {
      format: result.format,
      maps: result.maps.map((map) => ({
        markdown: serializeOrbitMarkdown(map.document),
        media: Object.fromEntries(Object.entries(map.media).map(([path, bytes]) => [path.replace(/^media\//, ''), toBase64(bytes)])),
        warnings: map.warnings.map(({ code, count, message }) => ({ code, count, message })),
      })),
    };
  } catch (error) {
    if (error instanceof OrbitImportError) {
      return { error: { code: error.code, message: error.message } };
    }

    throw error;
  }
};
