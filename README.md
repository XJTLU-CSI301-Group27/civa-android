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

An initial Java/Android Views module layout is being prepared against
`PROJECT_DOC.md`. Android and server builds exist, but the complete API contract,
tests and server implementation are incomplete and the baseline is not approved.
See `docs/verification/BOOTSTRAP.md` and `OPEN_QUESTIONS.md` for current status
and the outstanding team contract decision. Do not create competing projects.

The single `contracts/openapi.json` is an unapproved candidate. Check it from the
root with `./gradlew validateContract generateClient generateServerApi
:api-client:testCandidateClient` and from `server/` with `./gradlew
testCandidateContract`. Generated interfaces are compiled into both projects;
successful generation does not mean the team has approved the contract.

The server's `unitTest` task runs the explicitly isolated unit tests without a
database. Its `test` task requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
in the running shell and connects to verify that the actual PostgreSQL database
matches the URL and ends in `_test` before starting tests. This guard does not
yet replace the required database integration suite or full preflight.

`python scripts/contract_diff.py --base <old.json> --head <new.json> --output <report.json>`
reports candidate OpenAPI file changes and returns a nonzero exit code when
changes are found. `python scripts/contract_guard.py check|draft` adds the IC
authorization gate (a change must be covered by an ACCEPTED interface-change
record) and digest verification; neither replaces the full preflight.

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

The preflight scripts run the configured gates from `PROJECT_DOC.md` section 15:
a Python 3.11+/PyYAML check, `validateContract generateClient generateServerApi
checkContracts`, `assembleDebug testDebugUnitTest lint`, the contract tool
self-tests, and the server `bootJar`. Server integration tests (`test`) need a
running `_test` PostgreSQL database and are run separately.

## Building and running locally

Prerequisites: JDK 17, Android SDK with platform 36, Python 3.11+, and either
Docker Compose or a local PostgreSQL 16 instance. The debug app defaults to
`http://10.0.2.2:8080/` (the emulator's route to the host).

```powershell
# Root: install the pinned contract tooling, then validate and generate.
python --version                  # must be 3.11+
python -m pip install -r scripts/requirements.txt
.\gradlew.bat validateContract generateClient generateServerApi checkContracts
.\gradlew.bat assembleDebug testDebugUnitTest lint

# Start only the development database (server runs on the host).
docker compose --env-file .env -f infra/compose.yaml up -d

# Server: set your own database/media variables, then run or test.
$env:DB_URL='jdbc:postgresql://localhost:5432/civa'
$env:DB_USERNAME='civa'
$env:DB_PASSWORD='<your password>'
$env:CIVA_MEDIA_DIR='<local media directory>'
cd server; .\gradlew.bat bootRun      # or: .\gradlew.bat test bootJar
```

Server integration tests (`test`) require a PostgreSQL database whose name ends
in `_test`; the test guard refuses any other name. macOS/Linux use `./gradlew`
and `export` instead of `.\gradlew.bat` and `$env:`.

The release build requires an explicit HTTPS `CIVA_API_BASE_URL`; plain HTTP is
allowed only for debug on a trusted local machine or network.

## Local API address

The debug app defaults to `http://10.0.2.2:8080/`, the Android emulator's route
to a server on the host. To use another reachable server, set
`CIVA_API_BASE_URL` in `~/.gradle/gradle.properties` or pass, for example,
`-PCIVA_API_BASE_URL=http://192.168.1.20:8080/`. The address must be an origin
ending in `/`. Use plain HTTP only on a trusted local machine or network; do not
expose the database port or disable the firewall. The debug manifest alone
permits cleartext. Release tasks require an explicitly supplied HTTPS address.

Start the server separately with its documented database and media settings;
the debug APK does not start the server. Account and household creation work
only with a running backend. Invitation creation and most business screens are
still incomplete; see `docs/verification/BOOTSTRAP.md`.

The following operations are placeholders and return `CONTRACT_NOT_IMPLEMENTED`
when reached with a valid authenticated request: `addWishlist`,
`changeInventory`, `commitPlan`, `confirmCooking`, `createInventoryBatch`,
`createInvitation`, `createMealEntry`, `createRecipe`, `deleteMealEntry`,
`getMealSummary`, `getMediaContent`, `getPreferences`, `getRecipe`,
`listCookingEvents`, `listIngredients`, `listInventoryBatches`,
`listInventoryMovements`, `listMealEntries`, `listPlanSlots`, `listRecipes`,
`listWishlist`, `matchRecipes`, `previewCooking`, `removeMember`,
`removeWishlist`, `replacePlanSlot`, `setPlanSlotLock`, `setRecipeArchived`,
`updateInventoryBatch`, `updateMealEntry`, `updatePreferences`, `updateRecipe`,
and `uploadMedia`. Authentication and request validation still run first.

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
