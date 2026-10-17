# Security policy

## Supported versions

Security fixes go into the latest release of Orbit for Android.

## Reporting a vulnerability

Please don't open a public issue for a suspected vulnerability. Report it privately instead, with **Report a vulnerability** under this repository's **Security** tab, and include:

- the app's version (Settings, About) or the commit, and which build: `play` or `libre`;
- the steps to reproduce it;
- what someone could do with it, and any workaround you know of;
- whether it's already public.

Reports are answered as soon as possible, and you'll hear how the fix is going until it's released. Reporters are credited in the release notes if they'd like to be.

The parts that face other devices are the most sensitive: the servers the app runs on the local network to send maps and to present in browsers, and the encryption of maps sent between devices. How they work is in [docs/architecture.md](docs/architecture.md).
