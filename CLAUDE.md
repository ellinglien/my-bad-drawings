# Product philosophy

Read `PHILOSOPHY.md` before adding or changing anything the user sees.
My Bad Drawings is a deliberately minimal fork of MonoPaint: few, clear tools, no
settings. Proposals that add tools, modes or options must justify themselves
against it.

# Text style

Write any words the app shows (labels, buttons, captions, "This brush",
notes, dialogs, screen-reader text) to `docs/TEXT-STYLE.md`: its 12 rules, its
word list (one word per concept) and its templates. Display labels only:
never rename a choice's key, a style's name or anything in a share code.

# Architecture rules

Before implementing any feature:

1. Search for analogous functionality already in the repository.
2. Identify the abstraction that currently owns this responsibility.
3. Prefer extending that abstraction over creating parallel code.
4. Do not duplicate logic merely to avoid modifying existing code.
5. If two code paths would become substantially similar, refactor shared behavior first.
6. Preserve package/layer boundaries described in DESIGN.md.
7. Before finishing, review the diff specifically for:
   - duplicated logic
   - new helpers duplicating existing helpers
   - bypassed abstractions
   - special cases that belong in the core model
   - feature-specific state that should be generalized

# Upstream

See `docs/INTEGRATION.md` before touching upstream MonoPaint files or the brush engine: keep the engine pure Java and separable, keep My Bad Drawings UI in its own classes, edit upstream files only when needed, split generic fixes into small focused commits, and update that doc when engine boundaries or upstream-touching changes land.
