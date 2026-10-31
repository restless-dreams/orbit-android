/**
 * Study mode on the map's page: the ideas under each card wait out of view
 * until they're revealed. It's only how the map looks: Orbit never changes
 * the map for it, so nothing here is ever saved.
 */
import type { OrbitSVGRenderer } from '@restless-dreams/orbit';

/** The app's commands that hide and reveal ideas (`HideChildren` and `RevealChildren` in the app's page/). */
export type StudyCommand =
  | { type: 'hideChildren'; ids: string[] }
  | { type: 'revealChildren'; ids: string[] | null };

/** Hides the ideas under the cards a command names, or reveals them: with no ids, every hidden idea. */
export const study = (map: Pick<OrbitSVGRenderer, 'hideChildren' | 'revealChildren'>, command: StudyCommand): void => {
  if (command.type === 'hideChildren') {
    map.hideChildren(command.ids);
  } else {
    map.revealChildren(command.ids ?? undefined);
  }
};
