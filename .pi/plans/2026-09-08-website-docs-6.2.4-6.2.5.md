# Website documentation snapshots for 6.2.4 and 6.2.5

## Scope and decisions

Copy the current unversioned `website/docs/` and `website/sidebars.js` into
Docusaurus snapshots for missing version 6.2.4 and upcoming release 6.2.5.
Both snapshots intentionally contain the same current documentation, as requested;
6.2.4 is not reconstructed from historical source. Preserve document contents,
the unversioned docs, and all existing snapshots.

Make 6.2.5 the default documentation version at the root route. Keep current docs
at `/unreleased`, and mark 6.2.4 and 6.2.3 unmaintained using the existing version
configuration convention. This prepares local release content without publishing.
There are no Java or public API changes.

The referenced `.pi/prompts/` workflow and naming guidance are absent from this
checkout; this dated plan follows the required `.pi/plans/` location.

## Implementation

- [x] Run `npm run docusaurus -- docs:version 6.2.4` from `website/`.
- [x] Run `npm run docusaurus -- docs:version 6.2.5` from `website/`.
- [x] Update `website/docusaurus.config.js` default and maintenance metadata.
- [x] Verify both snapshots are byte-for-byte copies of current docs, both
      sidebars match the current sidebar, and versions are registered newest first.
- [x] Run `npm run build` from `website/` with existing strict link validation.
- [x] Inspect generated version routes and banners, check the diff, and stage
      only this plan and the documentation versioning changes in Git.

## Validation strategy

Use snapshot equality checks and a full Docusaurus production build to validate
content, sidebar references, MDX compilation, and links across all versions.
Inspect generated route metadata for the new default, archived versions, and
unreleased docs. No Maven or Gradle validation is needed for this docs-only change.

## Results

- Both Docusaurus version commands passed and registered 6.2.5 then 6.2.4.
- Node assertions passed: all 48 files in each snapshot are byte-for-byte copies
  of current docs; both sidebars match; version entries are unique and ordered.
- `npm run build` passed with strict broken-link validation enabled. Node emitted
  a non-fatal experimental localStorage warning.
- Generated metadata and rendered HTML checks passed: 6.2.5 is latest at `/`
  without a version banner; 6.2.4 and 6.2.3 use version-prefixed routes and legacy
  banners; current docs remain at `/unreleased` with an unreleased banner.
- `git diff --check` passed. Only the plan, two new snapshots and sidebars,
  version registry, and Docusaurus version configuration are staged.
- No release, commit, push, or website publication was performed.
