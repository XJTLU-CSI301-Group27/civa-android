# Civa Android Mandatory AI Development Rules

For the architecture initialization, also read `PROJECT_DOC.md` and
`OPEN_QUESTIONS.md`. Generated code must not be edited by hand; public contract
proposals follow the review process in the existing team rules.

## 1. Read project rules first

Before modifying code, read:

- `docs/PRODUCT_RULES.md`
- `docs/DATA_CONTRACT.md`
- `docs/UI_RULES.md`
- `docs/INTEGRATION_RULES.md`
- `docs/TEST_PLAN.md`

If your coding tool does not automatically read `AGENTS.md`, read it explicitly
before implementation.

## 2. Work only on the assigned task

- Identify the relevant GitHub Issue and acceptance criteria.
- Work on the assigned feature branch, not directly on `main`.
- Inspect the current repository structure before creating files.
- Reuse existing components, interfaces, and dependencies.
- Do not create a second Android project or duplicate global infrastructure.
- Do not rewrite unrelated modules to make your task easier.

## 3. Synchronize before integration

- Fetch the latest `origin/main` before final validation.
- Inspect the full diff between your branch and `main`.
- Integrate the latest `main` using the team's agreed Git workflow.
- Resolve conflicts by understanding both changes. Never blindly select one side.
- Run the required build and tests after integration.
- Do not claim that checking `main` alone proves your feature branch is compatible.

## 4. Public contracts

- Reuse existing public data models and interfaces.
- Do not create duplicate models for the same business concept.
- Do not change public method signatures, shared fields, units, nullability,
  error semantics, or data ownership without documenting the change.
- If a contract must change, update `docs/DATA_CONTRACT.md` and identify all
  affected modules.
- Add or update tests for the new contract.
- Do not bypass another module's public interface by accessing its internal
  storage.

## 5. Product invariants

- A meal plan is not a cooking record.
- Cooking does not mean every household member ate the meal.
- A personal meal entry must not deduct inventory again.
- Inventory must never become negative.
- Duplicate requests must not cause duplicate cooking records or inventory
  deductions.
- Personal data and household-shared data must remain separate.
- Unknown values must not be silently treated as zero.
- Historical meal entries must preserve the intended historical snapshot.
- Shared writes must handle concurrent updates safely.

## 6. UI and architecture

- Reuse the existing theme, design tokens, navigation, and common UI components.
- Do not introduce another visual system.
- Follow `docs/UI_RULES.md`.
- Do not add a new dependency without explaining why it is necessary.
- Do not change Gradle configuration, database schema, global navigation, or
  shared models as an incidental part of an unrelated feature.
- Keep module dependencies within the documented architecture.

## 7. Required preflight

Before requesting a push or pull request:

1. Inspect all changed files and the full diff.
2. Identify affected modules and public contracts.
3. Fetch and integrate the latest `main` according to the project workflow.
4. Run the required build, unit tests, lint, and formatting checks.
5. Fix failures caused by your changes and rerun the checks.
6. Add tests for new behavior and bug fixes where practical.
7. Report exact commands executed and actual results.
8. Clearly identify checks that could not be run.

Never claim a test passed unless it was actually executed.
Never delete or weaken tests merely to make a build pass.
Never replace real behavior with mocks without explicitly documenting it.
Never hide errors with empty implementations or hard-coded fake success results.
Never use destructive Git commands to discard another person's work.

## 8. Stop conditions

Stop and report the issue instead of guessing when:

- a required public interface has not been defined;
- a shared data model needs a breaking change;
- a database migration could affect existing data;
- authentication, authorization, or transaction behavior is unclear;
- the project cannot build for reasons you cannot safely resolve;
- the task conflicts with an established product invariant.

Do not independently invent a competing contract.

## 9. Final report

Before requesting a pull request, report:

- what behavior was implemented;
- which files and modules changed;
- public interfaces and dependencies affected;
- build/test commands actually executed;
- pass/fail results;
- remaining risks and incomplete behavior;
- whether mock data remains in the implementation.
