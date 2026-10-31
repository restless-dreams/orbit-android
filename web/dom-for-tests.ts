/**
 * Tests run in a pretend browser, happy-dom's, as the library's own tests
 * do: a test can draw a map the way the pages do and look at what's on it.
 * Tests never reach the network: frames, scripts and stylesheets stay
 * unloaded.
 */
import { GlobalRegistrator } from '@happy-dom/global-registrator';

GlobalRegistrator.register({
  url: 'http://localhost',
  settings: {
    disableIframePageLoading: true,
    disableJavaScriptFileLoading: true,
    disableCSSFileLoading: true,
  },
});
