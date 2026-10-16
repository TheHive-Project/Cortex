package org.thp.cortex

package object models {

  /** Version of the stored document shape. Bumping it makes `POST /api/maintenance/migrate` create a new index from the current models and copy
    * every document into it, so a bump is also an upgrade step every operator has to run before Cortex serves again.
    *
    * Deliberately *not* bumped for the multi-factor attributes (`totpSecret`, `totpScratchCodes`). A full reindex of every job, report and artifact
    * is a steep price for two fields that are only ever read back from `_source` — nothing queries, sorts or aggregates on them. On an index created
    * before they existed, Elasticsearch maps them on first write (`text` with a `keyword` sub-field, rather than the declared `keyword`), which
    * changes nothing for the feature.
    *
    * If a future change needs to *query* either field, bump this and add the matching `DatabaseState` case in `Migration.scala`. An `exists` query —
    * the natural way to ask "who has MFA enabled" — matches under both mapping shapes; only an exact `term` on the value needs the declared
    * `keyword`.
    *
    * Test coverage behind the above: enrolment and login were exercised end to end against a freshly created index. The pre-existing-index case was
    * only checked at the Elasticsearch level — the dynamic mapping it produces, and a `_source` round-trip — not by running Cortex against an index
    * created by an earlier release. Upgrading an installed package to this build was not tested.
    */
  val modelVersion = 6
}
