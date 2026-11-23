# TOKEN-LEAN (WO-TOKEN-LEAN-01) — rules R1–R13 for all MAU bots

- R1 Rooms are for coordination only: max 5 lines, no pasted logs/diffs/reports; link a file path, PR or commit instead.
- R2 Heavy work only in your OWN thread; new thread per work order. When a thread passes ~150 tool calls or MausBot says it summarized the conversation, write a handoff file and start fresh.
- R3 Hand off by file or PR description (`docs/handoffs/WO-xxx.md`) and post one line with the link; the receiver reads the file, never the history.
- R4 @mention only the bot that must act. No broadcasts, no ack/thanks/done replies, no status echoes.
- R5 Query the knowledge graph (`graphify query "..." --budget N`) before reading files; read file ranges, not whole files; never re-read a file already read in this run.
- R6 Bound all tool output: head/tail/grep; `gh` with `--json`/`--jq`; tests show failures only; never cat logs, lockfiles or generated files; no screenshots unless the task needs one.
- R7 No polling or sleep loops; end the turn and let the dispatch pulse re-wake you.
- R8 Max 2 retries of the same failing command, then write the blocker and stop.
- R9 Reviewers post PASS/FAIL plus one line; detail goes in the PR comment.
- R10 Sonnet 5.5 is the default; Opus only when Maat or Tehuti explicitly escalates a decision.

- R11 PowerShell hygiene: never `python -c` with nested quotes; write the script to a .py/.ps1 in a scratch dir and run it. Prefer `git -C <repo>` and `pwsh -File`. Run `Get-Command x` before using a tool. ONE fix attempt, then write the blocker in your handoff and escalate.
- R12 Context discipline: new thread per WO and per routine run; rooms get links/paths plus max 3 lines, never files/logs/diffs; read only the lines you need; never re-read unchanged files; run `graphify query --budget` before opening code; one reply per handoff, no ack chatter; private chat for 1-bot questions, rooms only for decisions affecting 2+ bots.
- R13 Models are native Claude (Grand/Senior/Junior Sonnet 5.5, Ani Haiku 5.5). No model router or proxy is part of the MAU stack; do not reference one in docs, handoffs or routines.

Pointer line for the top of CLAUDE.md / AGENTS.md:
`Token rules: read docs/TOKEN-LEAN.md (R1–R13) before working.`

413 prevention: routines and work orders start FRESH threads; never carry giant histories or images; no image attachments unless required.
