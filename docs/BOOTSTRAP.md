# Repository Bootstrap

## Completed baseline

- Repository governance documents
- Cross-group contracts
- Issue and pull request templates
- Preflight scripts
- Initial Android CI template

## Decisions required before the Android scaffold

- package name and application ID;
- minimum and target Android versions;
- Kotlin, Gradle, and Android Gradle Plugin versions;
- Jetpack Compose or Android Views;
- shared authentication and data service;
- local persistence and offline behavior;
- final Gradle module structure.

## Android scaffold checklist

- [ ] Generate one unified Gradle project
- [ ] Add the Gradle wrapper
- [ ] Add package and application ID
- [ ] Add shared theme, navigation, and base components
- [ ] Add public domain models and repository interfaces
- [ ] Add a Mock data implementation for parallel development
- [ ] Replace the guarded CI template with real build and test tasks
- [ ] Run CI successfully on `main`
- [ ] Require the successful Android CI check on `main`
- [ ] Add the organization Project and project template

## Security

Do not commit:

- passwords or API keys;
- signing keys;
- personal tokens;
- `local.properties`;
- local IDE or Gradle caches.
