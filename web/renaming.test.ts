import { describe, expect, spyOn, test } from 'bun:test';
import { createOrbitMap, parseOrbit } from '@restless-dreams/orbit';
import { renamings } from './renaming';
import type { Renamed } from './renaming';

/**
 * Draws `text` as the pages draw a map, telling `renamings` what the map's
 * page tells it, and gives `use` the map and what each edit renamed.
 */
const drawn = (text: string, use: (map: ReturnType<typeof createOrbitMap>, host: HTMLElement, said: Array<Renamed | null>) => void, updating: () => string | null = () => null): void => {
  // happy-dom lays nothing out, and a map with no size draws nothing: this one is a phone's.
  const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 412, 700));
  const host = document.body.appendChild(document.createElement('div'));
  const renames = renamings();
  const said: Array<Renamed | null> = [];
  let map: ReturnType<typeof createOrbitMap> | null = null;

  try {
    map = createOrbitMap(host, parseOrbit(text), {
      transitionDuration: 0,
      onEditorClose: ({ node }) => renames.closed(node),
      onEdit: ({ kind, node }) => said.push(renames.edited(kind, node, updating())),
    });
    use(map, host, said);
  } finally {
    map?.destroy();
    host.remove();
    measuring.mockRestore();
  }
};

/** Types `text` in the field open on the map, and leaves it, which keeps it. */
const type = (map: ReturnType<typeof createOrbitMap>, host: HTMLElement, text: string): void => {
  const field = host.querySelector('textarea');

  if (!field) {
    throw new Error('No field open on the map');
  }

  field.value = text;
  map.stopEditing(true);
};

describe('an idea renamed', () => {
  test('by typing on the map says its new id, so the app moves what it keeps about it', () => {
    drawn('# Trip\n- Tokio\n- Kyoto\n', (map, host, said) => {
      map.editNode('tokio');
      type(map, host, 'Tokyo');
      expect(said).toEqual([{ from: 'tokio', to: 'tokyo' }]);
    });
  });

  test('by the app says its new id too', () => {
    let updating: string | null = null;

    drawn('# Trip\n- Tokio\n', (map, _host, said) => {
      updating = 'tokio';
      map.updateNode('tokio', { text: 'Tokyo' });
      updating = null;
      expect(said).toEqual([{ from: 'tokio', to: 'tokyo' }]);
    }, () => updating);
  });

  test('says nothing when its id stays: the same text, an id the map names, or a field closed without a change', () => {
    drawn('# Trip\n- Tokio <!-- orbit id: capital -->\n- Kyoto\n', (map, host, said) => {
      map.editNode('kyoto');
      type(map, host, 'Kyoto');
      map.editNode('capital');
      type(map, host, 'Tokyo');
      expect(said).toEqual([null]);
    });
  });

  test('says nothing for a later edit of another idea, or one the field didn\'t make', () => {
    drawn('# Trip\n- Tokio\n- Kyoto\n', (map, host, said) => {
      // Escape: the field closes on Tokio, and nothing is typed.
      map.editNode('tokio');
      map.stopEditing(false);
      map.updateNode('kyoto', { text: 'Kioto' });
      map.updateNode('tokio', { status: 'done' });
      expect(said).toEqual([null, null]);
    });
  });
});
