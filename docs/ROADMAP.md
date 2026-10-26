# Incremental roadmap
These are internal steps toward the full integrated first public release.
Complete one bounded task at a time; split any task that cannot be reviewed comfortably.

| ID | Deliverable | Done when |
|---|---|---|
| A00 | Repository audit | Baseline and blockers recorded without rewriting code |
| S01 | Shared reference | Optional reference mapped and round-trip tested; integrity/deletion policy documented |
| S02 | Study lifecycle | Standalone session transitions, timing and invalid transitions tested |
| S03 | Study persistence/service | Save/reload, context validation and transaction behavior verified |
| S04 | REST slice | Create/start/pause/resume/finish/read with DTOs, validation and errors |
| E01 | World history | State change and corresponding event commit or roll back together |
| N01 | Notes and associations | Standalone note; attach/reuse/unlink without accidental deletion |
| U01 | Dashboard/UI | Real backend workflow, loading/errors, correct totals and resume |
| W01 | Workspace model | Layout/tab/source state survives a new timed session |
| D01 | Desktop shell | Backend startup/shutdown, local API protection, failure recovery |
| B01 | Embedded browser | Isolated tabs, navigation, permission rules, restoration and resource limits |
| R01 | Sources/articles | Reusable sources, supported reader, quotes and resilient backlinks |
| R02 | Local books | Link/copy, PDF then EPUB, positions, annotations and missing-file recovery |
| V01 | Video | Supported source player with timestamp notes and fallback |
| Q01 | Recall | Manual prompts, attempts, due dates and deterministic scheduling tests |
| P01 | Peg graph | Create anchors/typed associations and navigate real stored links |
| I01 | Integrated workspace | Split view, autosave, search, Git references and full-session usability |
| REL01 | Release | Packaging/database decision, migrations, backup restore, clean-machine check |

At each milestone use the app yourself and report friction. Do not implement finance or AI during this roadmap.
Earlier hour estimates are rough planning ranges, not evidence of completion or fixed deadlines.
