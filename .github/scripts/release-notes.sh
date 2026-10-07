#!/usr/bin/env bash
# Checks CHANGELOG.md is ready to release VERSION for MINECRAFT and writes that version's section, the release notes,
# to OUT. The release's tag is vVERSION+MINECRAFT; each Minecraft version's branch has its own CHANGELOG.md.
# Usage: .github/scripts/release-notes.sh VERSION MINECRAFT [CHANGELOG] [OUT]
# Run it locally before tagging (bash .github/scripts/release-notes.sh 1.0.0 26.1.2); release.yml runs it on the tag.
set -euo pipefail

usage="usage: release-notes.sh VERSION MINECRAFT [CHANGELOG] [OUT]"
version="${1:?${usage}}"
minecraft="${2:?${usage}}"
changelog="${3:-CHANGELOG.md}"
out="${4:-release-notes.md}"
repo_url="https://github.com/zagdrath/encoded-logistics"
heading="## [${version}]"
tag="v${version}+${minecraft}"
tag_re="$(printf '%s' "${tag}" | sed 's/[.+]/\\&/g')"
errors=0

fail() {
  if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error::$1"; else echo "error: $1" >&2; fi
  errors=$((errors + 1))
}

# The version's section: everything between its heading and the next "## [" heading, without link definitions.
awk -v heading="${heading}" '
  index($0, heading) == 1 { found = 1; next }
  found && index($0, "## [") == 1 { exit }
  index($0, "[") == 1 && index($0, "]: ") > 0 { next }
  found { print }
' "${changelog}" > "${out}"

heading_line="$(grep -F -m1 "${heading}" "${changelog}" || true)"
if [ -z "${heading_line}" ]; then
  fail "${changelog} has no '${heading}' section (rename '## [Unreleased]' to '${heading} - YYYY-MM-DD')"
else
  if ! printf '%s\n' "${heading_line}" | grep -Eq "^## \[${version//./\\.}\] - [0-9]{4}-[0-9]{2}-[0-9]{2}\$"; then
    fail "'${heading_line}' needs a release date: '${heading} - YYYY-MM-DD'"
  fi
  if ! grep -q '[^[:space:]]' "${out}"; then
    fail "the '${heading}' section is empty"
  fi
  if grep -qi 'suggested version' "${out}"; then
    fail "the '${heading}' section still has its 'Suggested version' line"
  fi
  # An empty Unreleased section must sit above the version, ready for the next changes.
  unreleased_line="$(grep -n -m1 -F '## [Unreleased]' "${changelog}" | cut -d: -f1 || true)"
  version_line="$(grep -n -m1 -F "${heading}" "${changelog}" | cut -d: -f1)"
  if [ -z "${unreleased_line}" ] || [ "${unreleased_line}" -gt "${version_line}" ]; then
    fail "there's no '## [Unreleased]' section above '${heading}'"
  fi
fi

# Link definitions: the version's own (a compare from the branch's previous tag, or the tag's commits for the
# branch's first release), and Unreleased comparing from this version's tag.
if ! grep -Eq "^\[${version//./\\.}\]: ${repo_url}/(compare/.+\.\.\.${tag_re}|commits/${tag_re})\$" "${changelog}"; then
  fail "no '[${version}]: ${repo_url}/compare/vPREVIOUS...${tag}' link (or '.../commits/${tag}' for the branch's first release)"
fi
if ! grep -Fxq "[Unreleased]: ${repo_url}/compare/${tag}...HEAD" "${changelog}"; then
  fail "the '[Unreleased]' link must be '${repo_url}/compare/${tag}...HEAD'"
fi

if [ "${errors}" -gt 0 ]; then
  echo "${changelog} isn't ready to release ${tag} (${errors} problem(s))." >&2
  exit 1
fi
echo "${changelog} is ready to release ${tag}; release notes written to ${out}."
