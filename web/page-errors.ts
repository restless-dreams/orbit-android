// An error on the page reaches the app, which shows it on the map: something
// went wrong there. Chromium also reports a notice as an error: when a
// ResizeObserver's observations don't all fit in one frame, the rest come in
// the next, and it says so, in one of two wordings by version. Nothing went
// wrong, and the map stays as it is, so the app never hears of it.

/** Chromium's two wordings for a ResizeObserver's observations going on in the next frame. */
const RESIZE_OBSERVER_NOTICES = [
  'ResizeObserver loop completed with undelivered notifications',
  'ResizeObserver loop limit exceeded',
];

/** The message the app shows for an error on the page. */
export interface PageError {
  type: 'error';
  message: string;
}

/** Where a page's errors are heard: its window. */
interface ErrorSource {
  addEventListener(type: 'error', listener: (event: ErrorEvent) => void): void;
}

/** What the app hears of an error on the page: its message, or nothing for Chromium's ResizeObserver notice. */
export const pageError = (message: string): PageError | null => {
  return RESIZE_OBSERVER_NOTICES.some((notice) => message.includes(notice)) ? null : { type: 'error', message };
};

/** Tells the app, with `post`, of each error on `page` but Chromium's ResizeObserver notice. */
export const reportErrors = (page: ErrorSource, post: (error: PageError) => void): void => {
  page.addEventListener('error', (event) => {
    const error = pageError(event.message);

    if (error) {
      post(error);
    }
  });
};
