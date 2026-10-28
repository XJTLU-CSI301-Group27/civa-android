# GitHub Setup

## Repository

- URL: https://github.com/XJTLU-CSI301-Group27/civa-android
- Visibility: private
- Default branch: `main`
- Issues: enabled
- Actions: enabled
- Delete branch after merge: enabled

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

## Current GitHub plan limitation

The organization is on GitHub Free and the repository is private. GitHub
returns HTTP 403 when branch protection or repository rulesets are requested:

```text
Upgrade to GitHub Pro or make this repository public to enable this feature.
```

Consequences:

- Pull requests are the required team convention, but GitHub cannot currently
  enforce them on this private repository.
- The Android CI workflow runs, but a failing check cannot currently be made a
  required merge gate.
- Administrators must enforce the convention manually until the repository
  becomes public or the organization obtains a plan with private repository
  rules.

## CI bootstrap state

The first `Android CI` run passed because the workflow detects that `gradlew`
does not exist and skips the build with a warning. This is temporary and must
not be described as a real Android build gate.

After Issue `#2` adds the unified Android project:

1. remove the bootstrap guard from `.github/workflows/android-ci.yml`;
2. confirm a real build, test, and lint run succeeds;
3. require the successful CI check if the repository plan supports it.
