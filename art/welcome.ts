// The welcome map's picture, video and sound, drawn here in the logo's
// colors and written into app/src/main/assets/welcome/, the media the
// welcome map comes with in every language:
//
//   bun art/welcome.ts
//
// - ringed-planet.webp, the picture: a ringed planet among the stars;
// - planets-in-orbit.mp4, the video: the logo's six planets going round
//   their sun, eight seconds that loop, with planets-in-orbit.jpg, its
//   poster, as Orbit keeps one beside each video;
// - sound-twinkle.mp4, the sound: "Twinkle, Twinkle, Little Star" on a
//   music box, AAC in an .mp4, as Orbit keeps sounds.
//
// It needs rsvg-convert (from librsvg) and ffmpeg, with libx264 and libwebp.
import { join } from 'node:path';
import { mkdirSync, mkdtempSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';

type Point = [number, number];

const root = join(import.meta.dir, '..');
const output = join(root, 'app/src/main/assets/welcome');
const work = mkdtempSync(join(tmpdir(), 'orbit-welcome-'));

// The logo's colors (art/logo.ts).
const COLORS = {
  sun: '#FAD86F',
  coral: '#FF9E96',
  peach: '#F8B478',
  mint: '#7FDAAB',
  sky: '#86CDFF',
  lilac: '#C9B1FE',
  pink: '#F7A3D4',
  orbit: '#E2E6FF',
  duskLight: '#4A35A8',
  duskDark: '#18134A',
};
const PLANETS = [COLORS.coral, COLORS.peach, COLORS.mint, COLORS.sky, COLORS.lilac, COLORS.pink];

const PICTURE_SIZE = 1200;
const VIDEO_SIZE = 640;
const VIDEO_SECONDS = 8;
const FRAMES_PER_SECOND = 24;
const SAMPLE_RATE = 44100;

const number = (value: number): string => {
  return String(Math.round(value * 100) / 100);
};

const polar = (radius: number, degrees: number, [x, y]: Point): Point => {
  const radians = (degrees * Math.PI) / 180;
  return [x + radius * Math.cos(radians), y + radius * Math.sin(radians)];
};

const mix = (color: string, other: string, amount: number): string => {
  const channels = (hex: string) => [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16));
  const [from, to] = [channels(color), channels(other)];
  return `#${from.map((channel, index) => Math.round(channel + (to[index] - channel) * amount).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
};

/** The same stars every time: a small random generator, from a seed. */
const randomFrom = (seed: number) => {
  let state = seed;

  return (): number => {
    state = (state + 0x6d2b79f5) | 0;
    let value = Math.imul(state ^ (state >>> 15), 1 | state);
    value = (value + Math.imul(value ^ (value >>> 7), 61 | value)) ^ value;
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
};

const run = (command: string[]): void => {
  const result = Bun.spawnSync(command, { stdout: 'ignore', stderr: 'pipe' });

  if (result.exitCode !== 0) {
    throw new Error(`${command[0]} failed: ${result.stderr.toString()}`);
  }
};

/** The dusk sky the logo is drawn on, lit from the top left. */
const sky = (id: string, size: number): string => {
  return `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${number(size * 0.3)}" cy="${number(size * 0.22)}" r="${number(size * 0.95)}"><stop offset="0" stop-color="${COLORS.duskLight}"/><stop offset="1" stop-color="${COLORS.duskDark}"/></radialGradient>`;
};

/** A sphere lit from the top left, as Orbit and the logo shade their planets. */
const sphere = (id: string, color: string, [x, y]: Point, radius: number, light = 0.42, dark = 0.18): string => {
  return `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${number(x - 0.32 * radius)}" cy="${number(y - 0.4 * radius)}" r="${number(1.56 * radius)}"><stop offset="0" stop-color="${mix(color, '#FFFFFF', light)}"/><stop offset="0.55" stop-color="${color}"/><stop offset="1" stop-color="${mix(color, '#000000', dark)}"/></radialGradient>`;
};

/** The white sheen on a planet's lit side. */
const sheen = ([x, y]: Point, radius: number, opacity = 0.45): string => {
  const [sheenX, sheenY] = [x - 0.3 * radius, y - 0.38 * radius];
  return `<ellipse cx="${number(sheenX)}" cy="${number(sheenY)}" rx="${number(0.38 * radius)}" ry="${number(0.22 * radius)}" transform="rotate(-35 ${number(sheenX)} ${number(sheenY)})" fill="#fff" fill-opacity="${opacity}"/>`;
};

/** A four-pointed sparkle, for the brightest stars. */
const sparkle = ([x, y]: Point, reach: number, opacity: number): string => {
  const points = [[x, y - reach], [x + reach, y], [x, y + reach], [x - reach, y]];
  const curves = points.map(([pointX, pointY], index) => `${index === 0 ? 'M' : ''}${number(pointX)},${number(pointY)} Q${number(x)},${number(y)} `).join('');
  return `<path d="${curves}${number(points[0][0])},${number(points[0][1])} Z" fill="#fff" fill-opacity="${opacity}"/>`;
};

interface Star {
  center: Point;
  radius: number;
  opacity: number;
  twinkle: number;
  phase: number;

  /** One of the few with a sparkle. */
  bright: boolean;
}

const starsFor = (size: number, count: number, seed: number): Star[] => {
  const random = randomFrom(seed);
  return Array.from({ length: count }, () => ({
    center: [random() * size, random() * size] as Point,
    radius: (0.6 + random() ** 3 * 2) * (size / 600),
    opacity: 0.25 + random() * 0.65,
    // Whole turns over the video, so it loops without a jump.
    twinkle: 1 + Math.floor(random() * 3),
    phase: random() * Math.PI * 2,
    bright: random() > 0.94,
  }));
};

const svgStars = (stars: Star[], brightness: (star: Star) => number = () => 1): string => {
  return stars.map((star) => {
    const opacity = number(star.opacity * brightness(star));
    const dot = `<circle cx="${number(star.center[0])}" cy="${number(star.center[1])}" r="${number(star.radius)}" fill="#fff" fill-opacity="${opacity}"/>`;
    return star.bright ? dot + sparkle(star.center, star.radius * 4 + 4, star.opacity * brightness(star) * 0.6) : dot;
  }).join('');
};

// The picture: a ringed planet, its two moons, and the stars.

const pictureSvg = (): string => {
  const size = PICTURE_SIZE;
  const center: Point = [600, 620];
  const radius = 230;
  const tilt = -14;
  const rings = [
    { radiusX: 372, width: 14, color: COLORS.coral, opacity: 0.75 },
    { radiusX: 430, width: 44, color: COLORS.peach, opacity: 0.92 },
    { radiusX: 488, width: 8, color: COLORS.sun, opacity: 0.6 },
  ];
  const ringEllipses = rings.map(({ radiusX, width, color, opacity }) =>
    `<ellipse cx="${center[0]}" cy="${center[1]}" rx="${radiusX}" ry="${number(radiusX * 0.26)}" fill="none" stroke="${color}" stroke-opacity="${opacity}" stroke-width="${width}"/>`).join('');
  const bands = [
    { offset: -150, height: 34, color: '#FFFFFF', opacity: 0.16 },
    { offset: -70, height: 58, color: COLORS.lilac, opacity: 0.45 },
    { offset: 20, height: 26, color: '#FFFFFF', opacity: 0.14 },
    { offset: 70, height: 46, color: COLORS.pink, opacity: 0.3 },
    { offset: 150, height: 30, color: COLORS.lilac, opacity: 0.35 },
  ].map(({ offset, height, color, opacity }) =>
    `<rect x="${center[0] - radius}" y="${center[1] + offset - height / 2}" width="${radius * 2}" height="${height}" fill="${color}" fill-opacity="${opacity}"/>`).join('');
  const moons: { color: string; center: Point; radius: number }[] = [
    { color: COLORS.mint, center: [250, 300], radius: 54 },
    { color: COLORS.pink, center: [972, 958], radius: 32 },
    { color: COLORS.sun, center: [1012, 286], radius: 17 },
  ];
  const stars = starsFor(size, 150, 7).filter((star) => Math.hypot(star.center[0] - center[0], star.center[1] - center[1]) > radius + 12);
  const definitions = [
    sky('sky', size),
    `<radialGradient id="glow" gradientUnits="userSpaceOnUse" cx="${center[0]}" cy="${center[1]}" r="${radius * 1.9}"><stop offset="0.45" stop-color="${COLORS.lilac}" stop-opacity="0.35"/><stop offset="1" stop-color="${COLORS.lilac}" stop-opacity="0"/></radialGradient>`,
    `<radialGradient id="nebula" gradientUnits="userSpaceOnUse" cx="${size * 0.82}" cy="${size * 0.86}" r="${size * 0.5}"><stop offset="0" stop-color="${COLORS.pink}" stop-opacity="0.22"/><stop offset="1" stop-color="${COLORS.pink}" stop-opacity="0"/></radialGradient>`,
    sphere('planet', COLORS.sky, center, radius, 0.35, 0.3),
    `<radialGradient id="shade" gradientUnits="userSpaceOnUse" cx="${number(center[0] - 0.35 * radius)}" cy="${number(center[1] - 0.45 * radius)}" r="${number(1.7 * radius)}"><stop offset="0.5" stop-color="${COLORS.duskDark}" stop-opacity="0"/><stop offset="1" stop-color="${COLORS.duskDark}" stop-opacity="0.6"/></radialGradient>`,
    `<clipPath id="body"><circle cx="${center[0]}" cy="${center[1]}" r="${radius}"/></clipPath>`,
    // The rings' near half, in front of the planet: below their long axis, as the rings turn.
    `<clipPath id="near"><rect x="-200" y="${center[1]}" width="${size + 400}" height="${size}"/></clipPath>`,
    ...moons.map((moon, index) => sphere(`moon-${index}`, moon.color, moon.center, moon.radius)),
  ].join('');
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">`,
    `<defs>${definitions}</defs>`,
    `<rect width="${size}" height="${size}" fill="url(#sky)"/>`,
    `<rect width="${size}" height="${size}" fill="url(#nebula)"/>`,
    svgStars(stars),
    `<circle cx="${center[0]}" cy="${center[1]}" r="${radius * 1.9}" fill="url(#glow)"/>`,
    `<g transform="rotate(${tilt} ${center[0]} ${center[1]})">${ringEllipses}</g>`,
    `<circle cx="${center[0]}" cy="${center[1]}" r="${radius}" fill="url(#planet)"/>`,
    `<g clip-path="url(#body)"><g transform="rotate(${tilt} ${center[0]} ${center[1]})">${bands}</g></g>`,
    `<circle cx="${center[0]}" cy="${center[1]}" r="${radius}" fill="url(#shade)"/>`,
    sheen(center, radius, 0.3),
    `<g transform="rotate(${tilt} ${center[0]} ${center[1]})"><g clip-path="url(#near)">${ringEllipses}</g></g>`,
    ...moons.map((moon, index) => `<circle cx="${moon.center[0]}" cy="${moon.center[1]}" r="${moon.radius}" fill="url(#moon-${index})"/>${sheen(moon.center, moon.radius)}`),
    '</svg>',
  ].join('');
};

// The video: the logo's sun and six planets, going round once in eight seconds, some with a moon.

const videoStars = starsFor(VIDEO_SIZE, 80, 11);

const frameSvg = (frame: number, frames: number): string => {
  const size = VIDEO_SIZE;
  const center: Point = [size / 2, size / 2];
  const time = frame / frames;
  const orbitRadius = 208;
  const sunRadius = 62;
  const planetRadius = 31;
  const turn = 15 + 360 * time;
  const planets = PLANETS.map((color, index) => ({ color, center: polar(orbitRadius, -90 + turn + index * 60, center) }));
  const moons = planets.filter((_, index) => index % 2 === 0).map((planet, index) => ({
    color: mix(planet.color, '#FFFFFF', 0.45),
    center: polar(planetRadius + 20, -90 + 720 * time + index * 120, planet.center),
  }));
  const dots = Array.from({ length: 72 }, (_, index) => polar(orbitRadius, (index * 360) / 72, center))
    .map(([x, y]) => `<circle cx="${number(x)}" cy="${number(y)}" r="2" fill="${COLORS.orbit}" fill-opacity="0.45"/>`).join('');
  const glowOpacity = 0.5 + 0.06 * Math.sin(time * Math.PI * 4);
  const definitions = [
    sky('sky', size),
    `<radialGradient id="glow" gradientUnits="userSpaceOnUse" cx="${center[0]}" cy="${center[1]}" r="${sunRadius * 1.75}"><stop offset="0.5" stop-color="${COLORS.sun}" stop-opacity="${number(glowOpacity)}"/><stop offset="1" stop-color="${COLORS.sun}" stop-opacity="0"/></radialGradient>`,
    sphere('sun', COLORS.sun, center, sunRadius, 0.55, 0.08),
    ...planets.map((planet, index) => sphere(`planet-${index}`, planet.color, planet.center, planetRadius)),
  ].join('');
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">`,
    `<defs>${definitions}</defs>`,
    `<rect width="${size}" height="${size}" fill="url(#sky)"/>`,
    svgStars(videoStars, (star) => 0.7 + 0.3 * Math.sin(time * Math.PI * 2 * star.twinkle + star.phase)),
    dots,
    `<circle cx="${center[0]}" cy="${center[1]}" r="${sunRadius * 1.75}" fill="url(#glow)"/>`,
    `<circle cx="${center[0]}" cy="${center[1]}" r="${number(sunRadius * 1.22)}" fill="none" stroke="${mix(COLORS.sun, '#FFFFFF', 0.45)}" stroke-opacity="0.5" stroke-width="4"/>`,
    `<circle cx="${center[0]}" cy="${center[1]}" r="${sunRadius}" fill="url(#sun)"/>`,
    ...planets.map((planet, index) => `<circle cx="${number(planet.center[0])}" cy="${number(planet.center[1])}" r="${planetRadius}" fill="url(#planet-${index})"/>${sheen(planet.center, planetRadius)}`),
    ...moons.map((moon) => `<circle cx="${number(moon.center[0])}" cy="${number(moon.center[1])}" r="7" fill="${moon.color}"/>`),
    '</svg>',
  ].join('');
};

// The sound: the first line of "Twinkle, Twinkle, Little Star", a traditional tune, on a music box.

const TUNE: [note: number, beats: number][] = [
  [72, 1], [72, 1], [79, 1], [79, 1], [81, 1], [81, 1], [79, 2],
  [77, 1], [77, 1], [76, 1], [76, 1], [74, 1], [74, 1], [72, 2],
];
const BEAT_SECONDS = 0.46;
const TAIL_SECONDS = 2;
const NOTE_SECONDS = 5;

/** A music box's tine: a bright strike, overtones that fade fast, and a long ring. */
const tine = (frequency: number, seconds: number): number => {
  const strike = 1 - Math.exp(-seconds / 0.003);
  const ring = Math.exp(-seconds / 0.9);
  const overtones = [[2, 0.35, 0.25], [3, 0.12, 0.12], [5.4, 0.06, 0.08]]
    .reduce((sum, [multiple, loudness, fade]) => sum + loudness * Math.exp(-seconds / fade) * Math.sin(2 * Math.PI * frequency * multiple * seconds), 0);
  return strike * ring * (Math.sin(2 * Math.PI * frequency * seconds) + overtones);
};

const tuneSamples = (): Float64Array => {
  const total = TUNE.reduce((sum, [, beats]) => sum + beats, 0) * BEAT_SECONDS + TAIL_SECONDS;
  const samples = new Float64Array(Math.ceil(total * SAMPLE_RATE));
  let start = 0;

  for (const [note, beats] of TUNE) {
    const frequency = 440 * 2 ** ((note - 69) / 12);
    const first = Math.round(start * SAMPLE_RATE);
    const length = Math.min(samples.length - first, NOTE_SECONDS * SAMPLE_RATE);

    // Each note rings until it's gone, so none of them stops with a click.
    for (let index = 0; index < length; index++) {
      samples[first + index] += tine(frequency, index / SAMPLE_RATE) * Math.min(1, (NOTE_SECONDS * SAMPLE_RATE - index) / (0.5 * SAMPLE_RATE));
    }

    start += beats * BEAT_SECONDS;
  }

  // A small room: three echoes, each softer.
  const echoes: [seconds: number, loudness: number][] = [[0.11, 0.28], [0.23, 0.15], [0.37, 0.08]];
  const roomy = Float64Array.from(samples, (sample, index) =>
    echoes.reduce((sum, [seconds, loudness]) => sum + loudness * (samples[index - Math.round(seconds * SAMPLE_RATE)] ?? 0), sample));
  const peak = roomy.reduce((highest, sample) => Math.max(highest, Math.abs(sample)), 0);
  const fade = Math.round(0.4 * SAMPLE_RATE);
  return roomy.map((sample, index) => (sample / peak) * 0.7 * Math.min(1, (roomy.length - index) / fade));
};

const wave = (samples: Float64Array): Uint8Array => {
  const data = new DataView(new ArrayBuffer(44 + samples.length * 2));
  const text = (offset: number, value: string) => [...value].forEach((letter, index) => data.setUint8(offset + index, letter.charCodeAt(0)));
  text(0, 'RIFF');
  data.setUint32(4, 36 + samples.length * 2, true);
  text(8, 'WAVEfmt ');
  data.setUint32(16, 16, true);
  data.setUint16(20, 1, true);
  data.setUint16(22, 1, true);
  data.setUint32(24, SAMPLE_RATE, true);
  data.setUint32(28, SAMPLE_RATE * 2, true);
  data.setUint16(32, 2, true);
  data.setUint16(34, 16, true);
  text(36, 'data');
  data.setUint32(40, samples.length * 2, true);
  samples.forEach((sample, index) => data.setInt16(44 + index * 2, Math.round(Math.max(-1, Math.min(1, sample)) * 32767), true));
  return new Uint8Array(data.buffer);
};

// Each file is written the same way every time: one thread, and nothing about the tools that made it.
const SAME_EVERY_TIME = ['-threads', '1', '-map_metadata', '-1', '-fflags', '+bitexact', '-flags', '+bitexact'];

mkdirSync(output, { recursive: true });

try {
  const picture = join(work, 'picture.svg');
  writeFileSync(picture, pictureSvg());
  run(['rsvg-convert', '--output', join(work, 'picture.png'), picture]);
  run(['ffmpeg', '-y', '-loglevel', 'error', '-i', join(work, 'picture.png'), ...SAME_EVERY_TIME, '-c:v', 'libwebp', '-quality', '82', '-compression_level', '6', join(output, 'ringed-planet.webp')]);

  const frames = VIDEO_SECONDS * FRAMES_PER_SECOND;

  for (let frame = 0; frame < frames; frame++) {
    const name = join(work, `frame-${String(frame).padStart(3, '0')}`);
    writeFileSync(`${name}.svg`, frameSvg(frame, frames));
    run(['rsvg-convert', '--output', `${name}.png`, `${name}.svg`]);
  }

  run([
    'ffmpeg', '-y', '-loglevel', 'error', '-framerate', String(FRAMES_PER_SECOND), '-i', join(work, 'frame-%03d.png'), ...SAME_EVERY_TIME,
    '-c:v', 'libx264', '-preset', 'veryslow', '-tune', 'animation', '-crf', '27', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', join(output, 'planets-in-orbit.mp4'),
  ]);
  run(['ffmpeg', '-y', '-loglevel', 'error', '-i', join(work, 'frame-000.png'), ...SAME_EVERY_TIME, '-q:v', '4', join(output, 'planets-in-orbit.jpg')]);

  writeFileSync(join(work, 'twinkle.wav'), wave(tuneSamples()));
  run(['ffmpeg', '-y', '-loglevel', 'error', '-i', join(work, 'twinkle.wav'), ...SAME_EVERY_TIME, '-c:a', 'aac', '-b:a', '64k', '-movflags', '+faststart', join(output, 'sound-twinkle.mp4')]);
} finally {
  rmSync(work, { recursive: true, force: true });
}

for (const name of ['ringed-planet.webp', 'planets-in-orbit.mp4', 'planets-in-orbit.jpg', 'sound-twinkle.mp4']) {
  console.log(`${name}: ${Math.round(statSync(join(output, name)).size / 1024)} KB`);
}
