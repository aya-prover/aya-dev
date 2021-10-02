# Contribution guidelines

All contributors should read and understand this guideline before their contribution. Maintainers will reject any contribution not following this guideline as necessary.

This guideline is agreed upon by all maintainers.

## Outside contributors

You are encouraged to:

- Report any bugs encountered in using Aya.
  - Please include a minimal reproduction and the commit hash you are using to run the reproduction.
  - Please include the error report.
- Ask any questions about Aya in the discussion area.

These are currently not accepted:

- PRs adding new language features without prior consent,
  because we have our own plans and accepting a PR means maintaining it.
  We refer to [Lean FAQ](https://leanprover.github.io/lean4/doc/faq.html)
  "Are pull requests welcome?" to explain the rationale.
- Feature proposals -- but please do share your ideas!
  We do not guarantee anything yet, so suggested features
  probably won't be considered.

Your responsibility:

- Unethical behaviors not adhering to [the Code of Conduct](CODE_OF_CONDUCT.md) is not allowed and will be taken proper action upon by maintainers.

If you are interested in contributing more and becoming a member of the organization, please contact the maintainers.

## Members of the organization

You are encouraged to:

- Open issues about the feature planning, implementation and other aspects of the Aya prover.
- Open bug fixing PRs.
- Open feature adding PRs when the addition of the feature is already agreed on by the community.

You are not allowed to:

- Push to the main branch, except for a few exceptions.
  Merging your PR into the main branch is wholly handled by the @bors bot.
- Force-push to the main branch. This is strictly prohibited. No exception.
- Perform other destructive and/or irreversible actions on the organization or repository settings without the maintainers' consent.

Your responsibility:

- PRs should be up to the contribution standard.
  - Your code should be properly formatted and linted.
  - Commit messages should follow the format `KEYWORD: detailed message ....`.
  - Please do not include overly fragmented commits; amending you commit and force-pushing to your PR branch is accepted and encouraged.
- Please be collaborative and open to others' comments and review.
  - When submitting your PR and issues, make sure to include proper tags and milestones (if applicable).
  - Please request review from relevant members and at least one maintainer for your PR.
- Base your PRs on latest main branch if possible and make sure you look at the CI results.
  Make sure you understand the purpose of using CI and address the problems it reports.
- Include test fixtures if your change is beyond a refactoring.
- Other unethical behaviors not adhering to [the Code of Conduct](CODE_OF_CONDUCT.md) is not allowed and will be taken proper action upon by maintainers.

## Maintainers

Maintainers are the collaborators that are either sponsored by PLCT or declared to be a maintainer by other maintainers' consensus.

Current maintainers: @ice1000, @imkiva, @Glavo, @re-xyr

Maintainers should:

- Continuously contribute to Aya's prosperity.
- Be open-minded and willing to accept and/or discuss different opinions.
- Always work towards consensus.

Maintainers' responsibility:

- For any important decisions that may affect Aya's prospect and general development,
  including major change in language goal, features and repository configuration,
  _no irreversible actions should be done before **all** maintainers reach consensus on a detailed plan and collective consent of potential consequences_. (ref. #60)
- For any important decisions or conflicts, maintainers' consensus is the final decision that cannot be rejected.
- Maintainers have the responsibility of maintaining the community's ethics uncorrupted, per [the Code of Conduct](CODE_OF_CONDUCT.md).
