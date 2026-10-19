# GitHub Copilot Instructions for Thunderbird for Android

When providing suggestions or completing code for this repository:
1. Adhere to the white-label architecture (`app-thunderbird` vs `app-k9mail`) and module boundaries in `AGENTS.md` and `docs/engineering/adr/`.
2. Adhere to ADR-0009: Depend only on `:api` modules, never import `:internal` classes directly into other features or legacy modules.
3. Use Kotlin idioms and Jetpack Compose for UI components.
4. Strict privacy requirement: Never log email addresses or personal identifiers.
5. Check `.memory-bank/` files before proposing changes to established architectural patterns.

