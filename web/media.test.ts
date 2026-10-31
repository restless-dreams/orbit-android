import { describe, expect, test } from 'bun:test';
import { parseOrbit } from '@restless-dreams/orbit';
import { mergedMedia } from './media';

/** A live photo with fields Orbit doesn't read, its motion's too, as another app or a newer Orbit writes it. */
const LIVE_PHOTO = '# Trip\n## Lake\n<!-- orbit\nimage:\n  src: media/lake.jpg\n  motion:\n    src: media/lake-motion.mp4\n    still: 0.8\n    loop: true\n  width: 640\n  alt: The lake\n-->\n';

const lake = (): Record<string, unknown> => {
  return parseOrbit(LIVE_PHOTO).root.children[0].image as unknown as Record<string, unknown>;
};

describe('a change to a node\'s own picture, video, sound or page', () => {
  test('takes what the app changed, and keeps every other field as it is, its motion too', () => {
    const current = lake();
    const merged = mergedMedia(current, { caption: 'Mist before seven' });

    expect(merged).toEqual({ src: 'media/lake.jpg', alt: 'The lake', motion: current.motion, caption: 'Mist before seven' });
    // The motion is the node's own, which Orbit keeps the fields of that it doesn't read, as it does the picture's for the same file.
    expect(merged?.motion).toBe(current.motion);
  });

  test('takes out a field the app sets to null', () => {
    expect(mergedMedia(lake(), { alt: null })).not.toHaveProperty('alt');
    expect(mergedMedia({ src: 'media/memo.mp4', caption: 'Kickoff' }, { caption: null })).toEqual({ src: 'media/memo.mp4' });
  });

  test('is merged too when it names the node\'s own file', () => {
    expect(mergedMedia({ src: 'https://example.com', caption: 'The site' }, { src: 'https://example.com' })).toEqual({ src: 'https://example.com', caption: 'The site' });
  });

  test('gives a live photo its motion, keeping the picture\'s fields', () => {
    const merged = mergedMedia(lake(), { motion: { src: 'media/lake-motion-2.mp4' } });
    expect(merged).toEqual({ src: 'media/lake.jpg', alt: 'The lake', motion: { src: 'media/lake-motion-2.mp4' } });
  });
});

describe('another file', () => {
  test('is the node\'s new value, with nothing of the one it replaces', () => {
    expect(mergedMedia(lake(), { src: 'media/shore.jpg', caption: 'Shore' })).toEqual({ src: 'media/shore.jpg', caption: 'Shore' });
    expect(mergedMedia(undefined, { src: 'media/shore.jpg', alt: null })).toEqual({ src: 'media/shore.jpg' });
  });

  test('is needed for a node with none: a change without one changes nothing', () => {
    expect(mergedMedia(undefined, { caption: 'Mist' })).toBeUndefined();
    expect(mergedMedia(null, { caption: 'Mist' })).toBeUndefined();
  });
});
