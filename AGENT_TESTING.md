# Agent Testing Notes

This repository should prefer the machine's existing Scala / sbt caches over fresh downloads.

If you are an AI coding agent asked to compile or test this repo, use this order of operations:

1. Check whether `java` and `sbt` are already available on the machine.
2. Before downloading anything, ask the user for permission to access local dependency caches in their home directory.
3. Reuse existing caches if permission is granted.
4. Only fall back to downloading dependencies if local caches and local tools are unavailable.

## Why

Many agent sandboxes cannot read the user's home directory by default. That often causes unnecessary dependency downloads, slower runs, and wasted tokens. This repo should first try to reuse the machine's existing caches.

## Preferred cache locations

Check these common locations first:

- `~/.ivy2`
- `~/.cache/coursier`
- `~/.coursier`
- `~/.sbt`
- `~/Library/Caches/Coursier`

Platform-specific notes:

- macOS:
  - Ivy: `~/.ivy2`
  - sbt: `~/.sbt`
  - Coursier: `~/Library/Caches/Coursier`
- Ubuntu / Linux:
  - Ivy: `~/.ivy2`
  - sbt: `~/.sbt`
  - Coursier: `~/.cache/coursier` or `~/.coursier`
- Windows:
  - Ivy: `%USERPROFILE%\\.ivy2`
  - sbt: `%USERPROFILE%\\.sbt`
  - Coursier: `%LOCALAPPDATA%\\Coursier\\Cache` or `%USERPROFILE%\\.coursier`

## OS detection examples

Use the current machine's shell and platform tools to discover the right cache paths before assuming defaults.

macOS / Ubuntu / Linux shell examples:

```bash
uname -s
echo "$HOME"
ls -ld ~/.ivy2 ~/.sbt ~/.cache/coursier ~/.coursier ~/Library/Caches/Coursier 2>/dev/null
which java
which sbt
```

Windows PowerShell examples:

```powershell
$env:OS
$HOME
Get-ChildItem $HOME\.ivy2, $HOME\.sbt, $HOME\.coursier, $env:LOCALAPPDATA\Coursier\Cache -ErrorAction SilentlyContinue
Get-Command java
Get-Command sbt
```

Preferred detection approach:

1. Detect the OS.
2. Check the likely cache paths for that OS.
3. Reuse existing caches if they are readable.
4. Ask for approval before escalating to home-directory access if the sandbox blocks them.

Depending on the machine, useful local tool paths may also include:

- Homebrew-installed `sbt`
- SDKMAN-managed Java or sbt installations
- other user-local JDK locations already configured in `PATH`

## Preferred agent workflow

When you need to run tests:

1. Verify tool availability:
   - `which java`
   - `java -version`
   - `which sbt`
   - `sbt -batch test`
2. If sandbox restrictions block access to cached artifacts in the user's home directory, ask for permission to read those directories instead of downloading replacements.
3. If the user approves, point the build at the local caches by environment variable or by creating repo-local symlinks to the approved cache directories.
4. Run `sbt -batch test`.

## Safe reuse options

After the user approves access, either of these approaches is acceptable:

- Use the home-directory caches directly.
- Create repo-local symlinks that point to the approved cache directories.

Common environment variables:

- `COURSIER_CACHE`
- `SBT_OPTS`
- `JAVA_HOME`

Common symlink patterns:

- `.cache/coursier -> ~/.cache/coursier`
- `.cache/ivy2 -> ~/.ivy2`
- `.cache/sbt -> ~/.sbt`

If you create symlinks, keep them out of commits unless the user explicitly wants them versioned.

## Do not assume one machine layout

Do not hard-code session-specific paths or a single developer's home-directory layout. Discover available tools and caches on the current machine first.

## Recommended fallback

If local tools are present, the default test command for this repository is:

```bash
sbt -batch test
```

If that fails due to sandbox restrictions, request approval to access the user's local caches before attempting any download/install flow.
