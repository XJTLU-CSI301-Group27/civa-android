# Contributing

## Before starting

- Read `AGENTS.md` and the documents in `docs/`.
- Select a GitHub Issue with clear scope and acceptance criteria.
- Confirm any cross-group contract before implementation.
- Create a feature branch from the latest `main`.

## Pull requests

Every pull request must:

- link to its Issue;
- stay within the assigned scope;
- state whether public contracts changed;
- include real commands and results for executed checks;
- identify tests or checks that could not be run;
- include screenshots for user-interface changes;
- identify remaining mocks, incomplete behavior, and known risks.

Do not weaken tests, disable CI, or fabricate check results to merge a change.

## Commit messages

Use a short conventional prefix where practical:

```text
feat(recipe): add recipe search
fix(inventory): prevent negative stock
test(planning): cover locked meal regeneration
docs(contract): define cooking event idempotency
```

## Integration

- Merge the latest `origin/main` into the feature branch before final checks.
- Resolve conflicts by understanding both sides.
- Do not rewrite `main` history.
- Do not merge while required checks are failing.
