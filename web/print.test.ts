import { describe, expect, test } from 'bun:test';
import { materialTheme, parseOrbit, PRINT_THEMES, themeTokens } from '@restless-dreams/orbit';
import type { OrbitRendererTheme } from '@restless-dreams/orbit';
import type { AppTheme } from './look';
import { goalRing, knownStyle, progressRing } from './look';
import { lightTheme, printColors, printNodeTypes, printOptions, ringLine } from './print';

const SEED = '#4F5B92';
const LIGHT: AppTheme = { dark: false, seed: SEED, tokens: { surface: '#FBF8FF', text: '#1B1B21', 'chip-background': '#DEE1F9' } };
const DARK: AppTheme = { dark: true, seed: SEED, tokens: { surface: '#121318', text: '#E4E1E9', 'chip-background': '#424659' } };

describe('lightTheme', () => {
  test('keeps the app’s light colors, and grows light ones from the same color when the app is dark', () => {
    expect(lightTheme(LIGHT)).toBe(LIGHT);
    expect(lightTheme(DARK)).toEqual({ dark: false, seed: SEED, tokens: {} });
  });
});

describe('printOptions', () => {
  test('prints a map in the app’s colors on a light Material theme, whatever mode the phone is in', () => {
    const map = parseOrbit('# Trip\n## Pack');
    const fromDark = printOptions(map, DARK, null, null, 'es').theme as OrbitRendererTheme;
    const light = materialTheme(SEED, { dark: false });
    expect(fromDark.preset).toBe(light.preset);
    expect(fromDark.tokens).toEqual(light.tokens);
    // The app's light colors are its own exactly.
    expect((printOptions(map, LIGHT, null, null, 'es').theme as OrbitRendererTheme).tokens?.text).toBe('#1B1B21');
  });

  test('leaves a map’s own style to Orbit, which prints it in its light counterpart', () => {
    const map = parseOrbit('---\ntheme: night\n---\n# Trip');
    expect(printOptions(map, DARK, knownStyle(map.options.theme), null, 'en').theme).toBeUndefined();
  });

  test('draws the picture the app asks for, in the page’s language, with the app’s kinds of ideas', () => {
    const map = parseOrbit('# Trip\n## Savings <!-- orbit type: goal, target: 300, current: 120 -->\n- [ ] Pack');
    const options = printOptions(map, LIGHT, null, { view: 'all', focus: 'savings' }, 'es');
    expect([options.view, options.focus, options.language]).toEqual(['all', 'savings', 'es']);
    expect(options.nodeTypes?.goal).toBeDefined();
    expect(printOptions(map, LIGHT, null, { view: 'focus', focus: null }, 'en').focus).toBeUndefined();
    expect(printOptions(map, LIGHT, null, null, 'en').view).toBeUndefined();
  });
});

describe('rings on paper', () => {
  const map = parseOrbit([
    '# Trip',
    '## Savings <!-- orbit type: goal, target: 300, current: 120, unit: EUR -->',
    '## Flights paid <!-- orbit type: goal, target: 2, current: 2 -->',
    '## Packing <!-- orbit type: progress -->',
    '- [x] Socks',
    '- [ ] Shirts',
    '- [ ] Hat',
  ].join('\n'));
  const [savings, flights, packing] = map.root.children;

  test('say on one line what the ring says: the full number, the target, and a check once reached', () => {
    expect(ringLine(goalRing(savings, 'en'))).toBe('€120 of €300');
    expect(ringLine(goalRing(flights, 'en'))).toBe('2 ✓');
    expect(ringLine(progressRing(packing))).toBe('33%');
  });

  test('are what goals and Progress ideas show under their names, and the other kinds of ideas keep their own look', () => {
    const types = printNodeTypes('es');
    expect(typeof types.goal.details).toBe('function');
    expect(typeof types.progress.details).toBe('function');
    expect(types.goal.icon).toBe('🎯');
    expect(types.milestone.details).toBeUndefined();
  });
});

describe('printColors', () => {
  test('gives rings on paper the colors of the layout\'s chips and links, under Orbit\'s public names', () => {
    const map = parseOrbit('# Trip');
    const inAppColors = printColors(printOptions(map, LIGHT, null, null, 'en').theme);
    expect(inAppColors['--orbit-print-chip-background']).toBe('#DEE1F9');
    expect(inAppColors['--orbit-print-link-text']).toMatch(/^#|^rgb/);
    // A map in its own dark style prints in its light counterpart, chips and links included.
    const counterpart = themeTokens(PRINT_THEMES.night);
    expect(printColors('night')).toEqual({ '--orbit-print-chip-background': counterpart['chip-background'], '--orbit-print-link-text': counterpart['link-text'] });
  });
});
