# Civa Android

CSI301 Android group project for household recipes, meal planning, inventory,
cooking records, personal meal logs, and shared household access.

This repository currently contains the collaboration and delivery baseline:

- mandatory AI development rules;
- product, data, UI, integration, and test contracts;
- GitHub Issue and pull request templates;
- local preflight scripts;
- an Android CI workflow that becomes an active build gate after the unified
  Android project is added.

The Android project scaffold is intentionally not generated yet. The team must
first agree on the Kotlin version, Gradle/AGP version, UI stack, package name,
and shared data service. Do not create competing Android projects in separate
branches.

## Required reading

Read these before changing code:

- `AGENTS.md`
- `docs/PRODUCT_RULES.md`
- `docs/DATA_CONTRACT.md`
- `docs/UI_RULES.md`
- `docs/INTEGRATION_RULES.md`
- `docs/TEST_PLAN.md`

## Team groups

| Group | Scope |
| --- | --- |
| AC | Recipes, ingredients, inventory, wishlist, and cooked history |
| BD | Meal planning, recommendation, cooking confirmation, and meal records |
| EF | Android foundation, accounts, household access, shared UI, and integration |

## Branch workflow

1. Pick an Issue and confirm its acceptance criteria.
2. Update `main`.
3. Create `feature/<issue-number>-<short-name>`.
4. Implement only the assigned scope.
5. Integrate the latest `origin/main`.
6. Run the preflight checks on the feature branch.
7. Open a pull request and report the real commands and results.
8. Merge only after required checks pass.

PowerShell:

```powershell
.\scripts\preflight.ps1
```

macOS or Linux:

```bash
./scripts/preflight.sh
```

The preflight currently reports that the Android scaffold is missing until a
valid `gradlew` project is added.

## Current bootstrap status

- [x] Repository governance files
- [x] Product and integration contracts
- [x] Issue, pull request, and CI templates
- [ ] Unified Android project
- [ ] Real Android build and unit-test CI
- [ ] Branch rules that require the real Android CI check
- [x] Organization Project
- [x] Project marked as an organization template
- [ ] Project added to the organization's recommended-template list
