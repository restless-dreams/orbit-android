// Orbit's logo: the focus view in miniature, a sun with six planets on a
// dotted orbit, on a dusk sky. Drawn once here and written out as every file
// that shows it:
//
//   bun art/logo.ts
//
// - the launcher icon's layers and its themed (one-color) version, and the
//   splash screen's animation, into app/src/main/res/drawable/;
// - art/logo.svg, the icon as launchers show it, for pages and READMEs;
// - art/logo-layers.svg, the full 108 × 108 background and foreground;
// - art/favicon.svg, a flat version for browser tabs.
//
// The store's icon and feature graphic (fastlane/metadata/android/en-US/images/)
// are drawn from art/logo.svg.
import { join } from 'node:path';
import { writeFileSync } from 'node:fs';

type Point = [number, number];
type Stop = [offset: number, color: string];

interface Radial {
  center: Point;
  radius: number;
  stops: Stop[];
}

const root = join(import.meta.dir, '..');
const drawables = join(root, 'app/src/main/res/drawable');

// The adaptive icon's 108 × 108 space: launchers show the middle 72, and the middle 66 is always safe.
const CENTER = 54;
const TURN = 15;
const ORBIT_RADIUS = 23.5;
const PLANET_RADIUS = 6.4;
const SUN_RADIUS = 11;
const GLOW_RADIUS = SUN_RADIUS * 1.75;
const HALO_RADIUS = SUN_RADIUS * 1.22;
const DOT_RADIUS = 0.78;
const DOT_COUNT = 48;
const DOT_CLEARANCE = 1.8;
// Themed icons sit beside other apps' one-color glyphs, which fill about half the mask: drawn smaller, with bolder dots.
const MONOCHROME_SCALE = 0.72;
const MONOCHROME_DOT_RADIUS = 1.05;
const COLORS = {
  sun: '#FAD86F',
  // Round the color wheel, with the sun in yellow's place: coral, peach, mint, sky, lilac, pink.
  planets: ['#FF9E96', '#F8B478', '#7FDAAB', '#86CDFF', '#C9B1FE', '#F7A3D4'],
  orbit: '#E2E6FF',
  duskLight: '#4A35A8',
  duskDark: '#18134A',
};
// x, y, radius, opacity: a few stars between the orbit and the edge of the mask.
const STARS: [number, number, number, number][] = [
  [30, 26, 0.5, 0.7], [79, 30, 0.45, 0.55], [84, 62, 0.4, 0.5], [27, 70, 0.45, 0.6], [62, 84, 0.35, 0.45],
  [44, 22, 0.3, 0.4], [86, 44, 0.3, 0.35], [24, 48, 0.3, 0.4], [72, 80, 0.4, 0.5], [36, 85, 0.3, 0.35],
];

const number = (value: number): string => {
  return String(Math.round(value * 100) / 100);
};

const polar = (radius: number, degrees: number, [x, y]: Point = [CENTER, CENTER]): Point => {
  const radians = (degrees * Math.PI) / 180;
  return [x + radius * Math.cos(radians), y + radius * Math.sin(radians)];
};

const mix = (color: string, other: string, amount: number): string => {
  const channels = (hex: string) => [1, 3, 5].map((index) => parseInt(hex.slice(index, index + 2), 16));
  const [from, to] = [channels(color), channels(other)];
  return `#${from.map((channel, index) => Math.round(channel + (to[index] - channel) * amount).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
};

/** A color with an opacity, as Android writes it: #AARRGGBB. */
const androidColor = (color: string, opacity = 1): string => {
  if (opacity === 1) {
    return color.toUpperCase();
  }

  return `#${Math.round(opacity * 255).toString(16).padStart(2, '0').toUpperCase()}${color.slice(1).toUpperCase()}`;
};

const ellipsePath = ([x, y]: Point, radiusX: number, radiusY: number): string => {
  return `M${number(x - radiusX)},${number(y)} a${number(radiusX)},${number(radiusY)} 0 1,0 ${number(radiusX * 2)},0 a${number(radiusX)},${number(radiusY)} 0 1,0 ${number(-radiusX * 2)},0 Z`;
};

const circlePath = (center: Point, radius: number): string => {
  return ellipsePath(center, radius, radius);
};

const HALO_COLOR = mix(COLORS.sun, '#FFFFFF', 0.45);
const planets = COLORS.planets.map((color, index) => ({ color, center: polar(ORBIT_RADIUS, -90 + TURN + index * 60) }));

// The dotted orbit leaves a little space around each planet, which keeps three dots between neighbors.
const dots = Array.from({ length: DOT_COUNT }, (_, index) => polar(ORBIT_RADIUS, -90 + TURN + (index * 360) / DOT_COUNT)).filter((dot) =>
  planets.every((planet) => Math.hypot(dot[0] - planet.center[0], dot[1] - planet.center[1]) > PLANET_RADIUS + DOT_CLEARANCE),
);
const dotsPath = dots.map((dot) => circlePath(dot, DOT_RADIUS)).join(' ');

/** A sphere lit from the top left, as Orbit shades its planets. */
const sphere = (color: string, [x, y]: Point, radius: number, light: number, dark: number): Radial => {
  return {
    center: [x - 0.32 * radius, y - 0.4 * radius],
    radius: 1.56 * radius,
    stops: [[0, mix(color, '#FFFFFF', light)], [0.55, color], [1, mix(color, '#000000', dark)]],
  };
};

const sunShading = sphere(COLORS.sun, [CENTER, CENTER], SUN_RADIUS, 0.55, 0.08);
const planetShading = planets.map((planet) => sphere(planet.color, planet.center, PLANET_RADIUS, 0.42, 0.18));
const glow: Radial = { center: [CENTER, CENTER], radius: GLOW_RADIUS, stops: [[0.5, COLORS.sun], [1, COLORS.sun]] };
const GLOW_OPACITIES = [0.5, 0];
const dusk: Radial = { center: [32.4, 23.76], radius: 102.6, stops: [[0, COLORS.duskLight], [1, COLORS.duskDark]] };
const sheens = planets.map((planet) => ({
  center: [planet.center[0] - 0.3 * PLANET_RADIUS, planet.center[1] - 0.38 * PLANET_RADIUS] as Point,
  radiusX: 0.38 * PLANET_RADIUS,
  radiusY: 0.22 * PLANET_RADIUS,
}));

const indent = (text: string, spaces: number): string => {
  return text.split('\n').map((line) => (line ? ' '.repeat(spaces) + line : line)).join('\n');
};

const svgRadial = (id: string, { center, radius, stops }: Radial, opacities: number[] = []): string => {
  const stopElements = stops.map(([offset, color], index) => `<stop offset="${offset}" stop-color="${color}"${opacities[index] !== undefined ? ` stop-opacity="${opacities[index]}"` : ''}/>`);
  return `<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${number(center[0])}" cy="${number(center[1])}" r="${number(radius)}">${stopElements.join('')}</radialGradient>`;
};

const svgCircle = ([x, y]: Point, radius: number, attributes: string): string => {
  return `<circle cx="${number(x)}" cy="${number(y)}" r="${number(radius)}" ${attributes}/>`;
};

const svgLayers = (prefix: string) => {
  const definitions = [
    svgRadial(`${prefix}-dusk`, dusk),
    svgRadial(`${prefix}-sun`, sunShading),
    svgRadial(`${prefix}-glow`, glow, GLOW_OPACITIES),
    ...planetShading.map((shading, index) => svgRadial(`${prefix}-planet-${index}`, shading)),
  ].join('');
  const background = [
    `<rect width="108" height="108" fill="url(#${prefix}-dusk)"/>`,
    ...STARS.map(([x, y, radius, opacity]) => svgCircle([x, y], radius, `fill="#fff" fill-opacity="${opacity}"`)),
  ].join('');
  const foreground = [
    `<path d="${dotsPath}" fill="${COLORS.orbit}" fill-opacity="0.5"/>`,
    svgCircle([CENTER, CENTER], GLOW_RADIUS, `fill="url(#${prefix}-glow)"`),
    svgCircle([CENTER, CENTER], HALO_RADIUS, `fill="none" stroke="${HALO_COLOR}" stroke-opacity="0.5" stroke-width="0.8"`),
    svgCircle([CENTER, CENTER], SUN_RADIUS, `fill="url(#${prefix}-sun)"`),
    ...planets.map((planet, index) => svgCircle(planet.center, PLANET_RADIUS, `fill="url(#${prefix}-planet-${index})"`)),
    ...sheens.map(({ center, radiusX, radiusY }) =>
      `<ellipse cx="${number(center[0])}" cy="${number(center[1])}" rx="${number(radiusX)}" ry="${number(radiusY)}" transform="rotate(-35 ${number(center[0])} ${number(center[1])})" fill="#fff" fill-opacity="0.45"/>`),
  ].join('');
  return { definitions, background, foreground };
};

const svg = svgLayers('orbit-logo');
const layersSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><title>Orbit</title><defs>${svg.definitions}</defs><g id="background">${svg.background}</g><g id="foreground">${svg.foreground}</g></svg>\n`;
const logoSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><title>Orbit</title><defs>${svg.definitions}<clipPath id="orbit-logo-shape"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath></defs><g clip-path="url(#orbit-logo-shape)">${svg.background}${svg.foreground}</g></svg>\n`;
// Stars, sheen and shading vanish at 16 px, so the favicon goes without them.
const faviconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs>${svgRadial('d', dusk)}</defs><rect x="18" y="18" width="72" height="72" rx="16" fill="url(#d)"/><path d="${dotsPath}" fill="${COLORS.orbit}" fill-opacity=".5"/>${svgCircle([CENTER, CENTER], SUN_RADIUS, `fill="${COLORS.sun}"`)}${planets.map((planet) => svgCircle(planet.center, PLANET_RADIUS, `fill="${planet.color}"`)).join('')}</svg>\n`;

const androidGradient = ({ center, radius, stops }: Radial, opacities: number[] = []): string => {
  return `<aapt:attr name="android:fillColor">
    <gradient
        android:type="radial"
        android:centerX="${number(center[0])}"
        android:centerY="${number(center[1])}"
        android:gradientRadius="${number(radius)}">
${stops.map(([offset, color], index) => `        <item android:offset="${offset}" android:color="${androidColor(color, opacities[index] ?? 1)}" />`).join('\n')}
    </gradient>
</aapt:attr>`;
};

/** A path filled with a color, or with a gradient from androidGradient. */
const androidPath = (pathData: string, fill: string, attributes = ''): string => {
  if (fill.startsWith('<')) {
    return `<path android:pathData="${pathData}"${attributes}>\n${indent(fill, 4)}\n</path>`;
  }

  return `<path\n    android:fillColor="${fill}"${attributes}\n    android:pathData="${pathData}" />`;
};

const androidGroup = (attributes: string, children: string): string => {
  return `<group${attributes}>\n${indent(children, 4)}\n</group>`;
};

const androidOrbit = (name = '') => androidPath(dotsPath, COLORS.orbit, `${name}\n    android:fillAlpha="0.5"`);
const androidStars = STARS.map(([x, y, radius, opacity]) => androidPath(circlePath([x, y], radius), '#FFFFFF', `\n    android:fillAlpha="${opacity}"`)).join('\n');
const androidSun = [
  androidPath(circlePath([CENTER, CENTER], GLOW_RADIUS), androidGradient(glow, GLOW_OPACITIES)),
  `<path\n    android:fillColor="#00000000"\n    android:strokeColor="${HALO_COLOR}"\n    android:strokeAlpha="0.5"\n    android:strokeWidth="0.8"\n    android:pathData="${circlePath([CENTER, CENTER], HALO_RADIUS)}" />`,
  androidPath(circlePath([CENTER, CENTER], SUN_RADIUS), androidGradient(sunShading)),
].join('\n');

const androidPlanet = (index: number): string => {
  const { center, radiusX, radiusY } = sheens[index];
  return [
    androidPath(circlePath(planets[index].center, PLANET_RADIUS), androidGradient(planetShading[index])),
    androidGroup(
      `\n    android:pivotX="${number(center[0])}"\n    android:pivotY="${number(center[1])}"\n    android:rotation="-35"`,
      androidPath(ellipsePath(center, radiusX, radiusY), '#FFFFFF', '\n    android:fillAlpha="0.45"'),
    ),
  ].join('\n');
};

const androidVector = (comment: string, body: string): string => {
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- ${comment} Drawn by art/logo.ts. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${indent(body, 4)}
</vector>
`;
};

const background = androidVector(
  'Dusk, with a few stars around the orbit.',
  [androidPath('M0,0 H108 V108 H0 Z', androidGradient(dusk)), androidStars].join('\n'),
);

const foreground = androidVector(
  'The sun, and six planets on a dotted orbit, inside the middle 66 dp that every mask shows.',
  [androidOrbit(), androidSun, ...planets.map((_, index) => androidPlanet(index))].join('\n'),
);

const monochrome = androidVector(
  'The themed icon: launchers tint these shapes with the wallpaper\'s colors.',
  androidGroup(
    `\n    android:pivotX="${CENTER}"\n    android:pivotY="${CENTER}"\n    android:scaleX="${MONOCHROME_SCALE}"\n    android:scaleY="${MONOCHROME_SCALE}"`,
    [
      androidPath(dots.map((dot) => circlePath(dot, MONOCHROME_DOT_RADIUS)).join(' '), '#FFFFFF', '\n    android:fillAlpha="0.7"'),
      androidPath(circlePath([CENTER, CENTER], SUN_RADIUS), '#FFFFFF'),
      androidPath(planets.map((planet) => circlePath(planet.center, PLANET_RADIUS)).join(' '), '#FFFFFF'),
    ].join('\n'),
  ),
);

// The splash screen: on its own dusk disc (the middle 72, as splash screens show), the orbit turns into place,
// the sun swells, and the planets appear one after another around it.
const PLANET_START = 140;
const PLANET_DELAY = 70;
const PLANET_GROWTH = 360;

const scaleTarget = (target: string, from: number, duration: number, delay = 0): string => {
  const animators = ['scaleX', 'scaleY'].map((property) => {
    const grow = `<objectAnimator
    android:duration="${duration}"
    android:interpolator="@interpolator/splash_pop"
    android:propertyName="${property}"
    android:valueFrom="${from}"
    android:valueTo="1" />`;

    if (delay === 0) {
      return grow;
    }

    // Held at its start while it waits, so it doesn't show at full size first.
    return `<set android:ordering="sequentially">
    <objectAnimator
        android:duration="${delay}"
        android:propertyName="${property}"
        android:valueFrom="${from}"
        android:valueTo="${from}" />
${indent(grow, 4)}
</set>`;
  });

  return `<target android:name="${target}">
    <aapt:attr name="android:animation">
        <set android:ordering="together">
${indent(animators.join('\n'), 12)}
        </set>
    </aapt:attr>
</target>`;
};

const splashSystem = androidGroup(
  `\n    android:name="system"\n    android:pivotX="${CENTER}"\n    android:pivotY="${CENTER}"`,
  [
    androidOrbit('\n    android:name="orbit"'),
    androidGroup(`\n    android:name="sun"\n    android:pivotX="${CENTER}"\n    android:pivotY="${CENTER}"`, androidSun),
    ...planets.map((planet, index) =>
      androidGroup(`\n    android:name="planet${index}"\n    android:pivotX="${number(planet.center[0])}"\n    android:pivotY="${number(planet.center[1])}"`, androidPlanet(index))),
  ].join('\n'),
);

const splash = `<?xml version="1.0" encoding="utf-8"?>
<!-- The launcher icon on a dusk disc: the orbit turns into place, the sun swells and the planets appear one after another. Drawn by art/logo.ts. -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
${indent([androidPath(circlePath([CENTER, CENTER], 36), androidGradient(dusk)), androidStars, splashSystem].join('\n'), 12)}
        </vector>
    </aapt:attr>
${indent([
  `<target android:name="system">
    <aapt:attr name="android:animation">
        <objectAnimator
            android:duration="900"
            android:interpolator="@android:interpolator/decelerate_cubic"
            android:propertyName="rotation"
            android:valueFrom="-75"
            android:valueTo="0" />
    </aapt:attr>
</target>`,
  `<target android:name="orbit">
    <aapt:attr name="android:animation">
        <objectAnimator
            android:duration="600"
            android:interpolator="@android:interpolator/fast_out_slow_in"
            android:propertyName="fillAlpha"
            android:valueFrom="0"
            android:valueTo="0.5" />
    </aapt:attr>
</target>`,
  scaleTarget('sun', 0.4, 520),
  ...planets.map((_, index) => scaleTarget(`planet${index}`, 0, PLANET_GROWTH, PLANET_START + index * PLANET_DELAY)),
].join('\n'), 4)}
</animated-vector>
`;

const files: Record<string, string> = {
  [join(drawables, 'ic_launcher_background.xml')]: background,
  [join(drawables, 'ic_launcher_foreground.xml')]: foreground,
  [join(drawables, 'ic_launcher_monochrome.xml')]: monochrome,
  [join(drawables, 'splash_orbit.xml')]: splash,
  [join(import.meta.dir, 'logo.svg')]: logoSvg,
  [join(import.meta.dir, 'logo-layers.svg')]: layersSvg,
  [join(import.meta.dir, 'favicon.svg')]: faviconSvg,
};

for (const [path, content] of Object.entries(files)) {
  writeFileSync(path, content);
  console.log(`${path.slice(root.length + 1)}  ${(content.length / 1024).toFixed(1)} KB`);
}
