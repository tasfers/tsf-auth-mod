# tsf-smp-mod — Project Rules

## ✅ Commit Style
- Every commit message must be **clear, professional, and cool-looking**.
- Follow this format:
  ```
  <emoji> <type>(<scope>): <short description>
  ```
  Examples:
  - `✨ feat(screens): add skin pre-fetching preview on account screen`
  - `🐛 fix(mixin): resolve AbstractClientPlayerMixin collision with other client mods`
  - `🔒 security(network): encrypt session sync payload via custom packet channels`
  - `♻️ refactor(auth): simplify TokenRefresher background thread pool lifecycle`
  - `🚀 perf(skins): cache fetched skins asynchronously in a local directory`
  - `📝 docs(readme): update mod requirements and setup instructions`
- Use present tense, imperative mood in the short description.
- Add a meaningful body when the change is non-trivial, explaining **why**, not just **what**.

## 🚫 Deployment to `main`
- **NEVER** push, merge, or deploy anything to the `main` branch without **explicit, direct permission from the user**.
- Always ask for consent before modifying or pushing to `main` branch.

## 🛑 Code Quality and AI Restrictions
- **NEVER** use generic AI-slop descriptions, comments, or placeholder texts (e.g. "simple admin panel HTML", "sample test", etc.).
- Avoid redundant or excessive comments. Write clean, production-grade code directly without extra explanations.
- Ensure all tests are meaningful, rigorous, and verify edge cases, rather than just asserting basics.

## 📝 Documentation Maintenance
- **ALWAYS** update relevant `.md` documentation files (such as `README.md`) immediately when making changes to schemas, configurations, or features. Never leave documentation outdated.
