# Contributing to My Bad Drawings

Bug reports and ideas are welcome as issues. For a bug, say which tablet (Manta or Nomad), its firmware version,
what you did and what you expected. Leave device serial numbers and personal drawings out of logs and screenshots.

Before proposing a feature, read [PHILOSOPHY.md](PHILOSOPHY.md): the app keeps few, clear tools on purpose, and a
change that adds a tool, mode or setting has to earn its place. Words the app shows follow
[docs/TEXT-STYLE.md](docs/TEXT-STYLE.md).

For code:

- Build and check as in [docs/BUILDING.md](docs/BUILDING.md); run `bash scripts/test_inkwash.sh` before a pull
  request, and say what you tried on a tablet (or that you couldn't).
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) maps the code. Much of it is MonoPaint's: read
  [docs/INTEGRATION.md](docs/INTEGRATION.md) before changing upstream files or the brush engine, keep the engine
  pure Java, and keep fixes that would suit MonoPaint too small and separate, so they can go upstream.
- Keep existing notebooks, share codes and saved settings readable.
- Never commit signing keys, passwords or local paths (`keystore.properties` is gitignored for this).

Contributions are under the repository's [MIT licence](LICENSE); keep the notices of any third-party work you use
([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and the app's copy in `app/src/main/assets`).
