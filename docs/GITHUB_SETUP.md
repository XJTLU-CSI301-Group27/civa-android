# GitHub Setup

Updated 2026-10-30 from the live repository through the GitHub API. Where the API
output and this document disagree, the API output is correct.

## Repository

- URL: https://github.com/XJTLU-CSI301-Group27/civa-android
- Visibility: public (see "Visibility change" below)
- Default branch: `main`
- Issues: enabled
- Actions: enabled; all actions and reusable workflows allowed
- Default Actions token: contents read-only (verified)
- Workflows can approve pull requests: disabled (verified)
- Merge methods: squash only (`allow_merge_commit` and `allow_rebase_merge` disabled)
- Auto-merge: disabled
- Delete branch after merge: enabled

## Branch protection

`main` is protected by the repository ruleset `main protection`
(`gh api repos/XJTLU-CSI301-Group27/civa-android/rulesets` returns its id):

- pull request required before merging;
- one approving review, stale approvals dismissed on new commits;
- review from a code owner required for the paths listed in `.github/CODEOWNERS`;
- all review conversations must be resolved;
- required status check: `CI gate`;
- branch must be up to date with `main` before merging;
- linear history required;
- force pushes and branch deletion blocked;
- no bypass actors: administrators also need the checks and review.

`.github/CODEOWNERS` is the ownership half of this rule. It only takes effect
together with "review from a code owner required"; it does not block merges by
itself.

Two accounts (`@DumbFoxxxxy`, `@KLEIBER404`) own every controlled path and either
one can approve. This is deliberate: GitHub never counts an author's own
approval, so a single owner could never merge their own change to these paths.

Because `main` is protected, every change after the bootstrap commit goes
through a pull request, including CI and documentation-only changes.

## CI

`.github/workflows/ci.yml` is the only workflow. It has four jobs:

| Job | Runs |
| --- | --- |
| `Contracts` | contract tool self-tests, `validateContract generateClient generateServerApi checkContracts` |
| `Android` | `assembleDebug testDebugUnitTest lint` |
| `Server` | `test bootJar` against a `postgres:16` service |
| `CI gate` | requires all three jobs to succeed; `skipped`, `cancelled` or `failed` all fail the gate |

`CI gate` is the single required status check, so adding or renaming an inner job
does not require editing the ruleset. The workflow uses `permissions: contents:
read`, `fetch-depth: 0`, `persist-credentials: false`, and cancels superseded
pull-request runs.

The old `.github/workflows/android-ci.yml` bootstrap guard is removed: it skipped
the build when `gradlew` was missing, so a green `Android CI` check did not prove
a build.

## Organization Project

- URL: https://github.com/orgs/XJTLU-CSI301-Group27/projects/2
- Title: `Civa Android - Development & Integration`
- Repository link: `civa-android`
- Template status: marked as an organization project template

Fields:

- Status: Backlog, Ready, In Progress, In Review, Integration, Done, Blocked
- Feature: AC-Recipe, AC-Inventory, BD-Planning, BD-Records, EF-Foundation,
  EF-UI, EF-Integration
- Priority: P0, P1, P2
- Contract Status: Draft, Agreed, Implemented, Tested

Views:

- Feature Roadmap
- Development Board
- Integration & Blockers

Initial Issues:

- `#1`: foundation, contracts, and bootstrap decisions
- `#2`: unified Android scaffold and real CI
- `#3`: two-account shared meal-plan vertical slice
- `#4`: recipe, inventory, cooking, and meal-record integration

## Recommended template

GitHub exposes marking a project as a template through the GitHub CLI, but the
organization's recommended-template list does not currently have a public CLI
or REST API command.

An organization owner must complete this final step:

1. Open the organization settings.
2. Select `Projects` under the code planning and automation section.
3. Under `Recommended templates`, choose `Customize recommended templates`.
4. Select `Civa Android - Development & Integration`.
5. Save.

## Visibility change

This repository was recorded here as private. It is now public, which is also why
branch protection and rulesets became available on the Free plan (private
repositories need GitHub Pro or Team for those features).

`Civa_GitHub_AI_Preflight_Guide.md` section 13 says not to change visibility just
to unlock a free feature. The team decided to keep the repository public; record
any further visibility change here before making it.

## Known gaps

- **Organization default repository permission is `admin`**, so all four members
  are administrators on this repository and can edit or delete the ruleset. The
  ruleset stops accidental force pushes and untested merges; it does not stop a
  determined administrator. Setting the organization default to `write` and
  granting admin only where needed would close this.
- **No organization teams exist**, so `.github/CODEOWNERS` names an account
  rather than a team. Creating an `ef` team later means editing CODEOWNERS only.
- **Enforcement is verified through the API, not through a rejected pull
  request.** The first real proof is a pull request that has not passed `CI gate`
  and cannot be merged.
- The organization Project is not yet on the recommended-template list (manual
  organization setting above).
