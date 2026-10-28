# Integration Rules

1. `main` is the unified buildable branch.
2. Every feature uses a separate branch.
3. Every feature merges through a pull request.
4. Merge the latest `main` before final validation.
5. Public contract changes update `docs/DATA_CONTRACT.md` and tests.
6. A failing build cannot merge.
7. Never bypass failures by deleting tests, disabling CI, or fabricating results.
8. Update the local branch soon after `main` changes.
9. Resolve conflicts by understanding both changes. Never blindly choose
   `ours` or `theirs`.
10. AI must report checks that were not executed.

## Initial branch policy

- Direct pushes to `main` should be disabled.
- A pull request is required for every change after repository bootstrap.
- The initial bootstrap commit may be pushed by the repository administrator.
- Required status checks should be enabled only after the real Android project
  exists and the workflow has passed once.

## Contract changes

Breaking or cross-group contract changes require:

1. a contract proposal linked to an Issue;
2. agreement from every affected group;
3. a coordinated implementation that keeps callers buildable;
4. updated tests;
5. successful integration checks.

When in doubt, stop and escalate instead of inventing a competing contract.
