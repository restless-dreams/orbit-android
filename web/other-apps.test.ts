import { describe, expect, test } from 'bun:test';
import { parseOrbit } from '@restless-dreams/orbit';
import { fromBase64, importFile, toBase64 } from './other-apps';

const base64Of = (text: string): string => toBase64(new TextEncoder().encode(text));

/** A zip of stored files, as small as XMind writes them. */
const storedZip = (files: Record<string, Uint8Array>): Uint8Array => {
  const encoder = new TextEncoder();
  const parts: Uint8Array[] = [];
  const directory: Uint8Array[] = [];
  let offset = 0;

  for (const [name, data] of Object.entries(files)) {
    const nameBytes = encoder.encode(name);
    const header = new DataView(new ArrayBuffer(30));
    header.setUint32(0, 0x04034b50, true);
    header.setUint16(4, 20, true);
    header.setUint32(14, Bun.hash.crc32(data), true);
    header.setUint32(18, data.length, true);
    header.setUint32(22, data.length, true);
    header.setUint16(26, nameBytes.length, true);
    parts.push(new Uint8Array(header.buffer), nameBytes, data);
    const entry = new DataView(new ArrayBuffer(46));
    entry.setUint32(0, 0x02014b50, true);
    entry.setUint16(4, 20, true);
    entry.setUint16(6, 20, true);
    entry.setUint32(16, Bun.hash.crc32(data), true);
    entry.setUint32(20, data.length, true);
    entry.setUint32(24, data.length, true);
    entry.setUint16(28, nameBytes.length, true);
    entry.setUint32(42, offset, true);
    directory.push(new Uint8Array(entry.buffer), nameBytes);
    offset += 30 + nameBytes.length + data.length;
  }

  const size = directory.reduce((total, part) => total + part.length, 0);
  const end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true);
  end.setUint16(8, Object.keys(files).length, true);
  end.setUint16(10, Object.keys(files).length, true);
  end.setUint32(12, size, true);
  end.setUint32(16, offset, true);
  const all = [...parts, ...directory, new Uint8Array(end.buffer)];
  const zip = new Uint8Array(all.reduce((total, part) => total + part.length, 0));
  let position = 0;

  for (const part of all) {
    zip.set(part, position);
    position += part.length;
  }

  return zip;
};

describe('files from other apps', () => {
  test('an OPML outline becomes orbit-md the app keeps as it is', () => {
    const file = importFile(base64Of('<opml version="2.0"><body><outline text="Trip"><outline text="Flights" _status="checked"/><outline text="Hotel" _note="Near the river"/></outline></body></opml>'), 'trip.opml', 'Untitled map');
    expect(file).toEqual({ format: 'opml', maps: [{ markdown: '# Trip\n\n## Flights <!-- orbit status: done -->\n\n## Hotel\nNear the river\n', media: {}, warnings: [] }] });
  });

  test('a FreeMind map, with what didn\'t come over', () => {
    const file = importFile(base64Of('<map version="1.0.1"><node TEXT="Plan"><node TEXT="Specs" LINK="specs.mm"/></node></map>'), null, 'Untitled map');

    if (!('maps' in file)) {
      throw new Error(file.error.message);
    }

    expect(parseOrbit(file.maps[0].markdown).root.children[0].text).toBe('Specs');
    expect(file.maps[0].warnings).toEqual([{ code: 'local-links-not-kept', count: 1, message: 'One link (“specs.mm”) to files on the computer the map was made on was left out.' }]);
  });

  test('an XMind workbook gives a map a sheet, and its pictures by their names in the media folder', () => {
    const picture = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 1, 2, 3]);
    const content = JSON.stringify([
      { id: 'one', rootTopic: { id: 'root', title: 'Garden', image: { src: 'xap:resources/plan.png' } } },
      { id: 'two', rootTopic: { id: 'root-2', title: 'Shopping' } },
    ]);
    const file = importFile(toBase64(storedZip({ 'content.json': new TextEncoder().encode(content), 'resources/plan.png': picture })), 'Garden.xmind', 'Untitled map');

    if (!('maps' in file)) {
      throw new Error(file.error.message);
    }

    expect(file.format).toBe('xmind');
    expect(file.maps.map((map) => parseOrbit(map.markdown).root.text)).toEqual(['Garden', 'Shopping']);
    expect(file.maps[0].markdown).toContain('![](media/plan.png)');
    expect(Object.keys(file.maps[0].media)).toEqual(['plan.png']);
    expect([...fromBase64(file.maps[0].media['plan.png'])]).toEqual([...picture]);
  });

  test('a center for several top outlines takes the app\'s word for untitled', () => {
    const file = importFile(base64Of('<opml><body><outline text="A"/><outline text="B"/></body></opml>'), null, 'Mapa sin título');
    expect('maps' in file && parseOrbit(file.maps[0].markdown).root.text).toBe('Mapa sin título');
  });

  test('a file Orbit can\'t read comes back with Orbit\'s code, for the app to say in its words', () => {
    expect(importFile(base64Of('# Just markdown'), 'notes.md', 'Untitled map')).toEqual({
      error: { code: 'unknown-format', message: "notes.md isn't a map Orbit can bring in from another app: it reads OPML, FreeMind and Freeplane (.mm), and XMind (.xmind)." },
    });
    expect(importFile(base64Of('<opml><body/></opml>'), 'empty.opml', 'Untitled map')).toEqual({ error: { code: 'empty', message: 'The OPML file has no outlines.' } });
  });

  test('base64 both ways, for files far larger than one call can spread', () => {
    const bytes = new Uint8Array(300_000).map((_, index) => (index * 7919) % 256);
    expect([...fromBase64(toBase64(bytes))]).toEqual([...bytes]);
    expect(toBase64(bytes)).toBe(Buffer.from(bytes).toString('base64'));
  });
});
