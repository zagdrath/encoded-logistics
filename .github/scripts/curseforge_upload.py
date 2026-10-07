"""Uploads a release jar to CurseForge with its changelog, game versions and dependencies.

release.yml runs it after the GitHub Release is published. The token is a CurseForge upload API token
(authors.curseforge.com, account settings, API tokens), passed in the CURSEFORGE_TOKEN environment variable from
the repository secret of the same name. With --dry-run it looks up the game versions and prints the upload's
metadata without uploading, e.g.:

    CURSEFORGE_TOKEN=... python .github/scripts/curseforge_upload.py --project 1731316 --jar build/libs/x.jar \
        --notes release-notes.md --version 1.0.0 --minecraft 26.3 --java 25 --optional arcforge,jei,jade --dry-run
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

API = "https://minecraft.curseforge.com/api"


def fail(message):
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::error::{message}")
    else:
        print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def request(token, path, body=None, content_type=None):
    req = urllib.request.Request(API + path, data=body, method="POST" if body is not None else "GET")
    req.add_header("X-Api-Token", token)
    req.add_header("User-Agent", "encodedlogistics-release")
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return json.load(response)
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        fail(f"CurseForge {req.get_method()} {path} failed: HTTP {e.code} {detail}")


def game_version_ids(token, minecraft, java):
    """CurseForge's ids for the Minecraft version, NeoForge, the Java version, client and server.

    Names alone aren't unique (26.3 is listed under more than one type), so each is picked within its type.
    """
    type_slugs = {t["id"]: t["slug"] for t in request(token, "/game/version-types")}
    versions = request(token, "/game/versions")
    wanted = [
        ("minecraft-" + "-".join(minecraft.split(".")[:2]), minecraft),
        ("modloader", "NeoForge"),
        ("java", f"Java {java}"),
        ("environment", "Client"),
        ("environment", "Server"),
    ]
    ids = []
    for type_slug, name in wanted:
        match = [v["id"] for v in versions if v["name"] == name and type_slugs.get(v["gameVersionTypeID"]) == type_slug]
        if not match:
            fail(f"CurseForge has no game version '{name}' of type '{type_slug}' yet")
        ids.append(match[0])
        print(f"Game version: {name} ({type_slug}) = {match[0]}")
    return ids


def release_type(version):
    pre = version.partition("-")[2].lower()
    if not pre:
        return "release"
    return "alpha" if pre.startswith("alpha") else "beta"


def multipart(fields, file_field, file_name, file_bytes):
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8"))
    parts.append(
        (f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{file_name}"\r\n'
         f"Content-Type: application/java-archive\r\n\r\n").encode("utf-8") + file_bytes + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description="Upload a release jar to CurseForge.")
    parser.add_argument("--project", required=True, help="CurseForge project id")
    parser.add_argument("--jar", required=True)
    parser.add_argument("--notes", required=True, help="Markdown changelog for the file")
    parser.add_argument("--version", required=True, help="mod_version, e.g. 1.0.0 or 1.1.0-beta.1")
    parser.add_argument("--minecraft", required=True, help="minecraft_version, e.g. 26.3")
    parser.add_argument("--java", required=True, help="Java version, e.g. 25")
    parser.add_argument("--name", default="Encoded Logistics", help="display name before the version")
    parser.add_argument("--optional", default="", help="comma-separated CurseForge slugs of optional dependencies")
    parser.add_argument("--dry-run", action="store_true", help="print the metadata instead of uploading")
    args = parser.parse_args()

    token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    if not token:
        fail("CURSEFORGE_TOKEN isn't set (add it as a repository secret)")
    if not os.path.isfile(args.jar):
        fail(f"{args.jar} doesn't exist")
    with open(args.notes, encoding="utf-8") as f:
        notes = f.read().strip()
    if not notes:
        fail(f"{args.notes} is empty")

    metadata = {
        "changelog": notes,
        "changelogType": "markdown",
        "displayName": f"{args.name} {args.version}",
        "gameVersions": game_version_ids(token, args.minecraft, args.java),
        "releaseType": release_type(args.version),
        "relations": {"projects": [{"slug": slug.strip(), "type": "optionalDependency"}
                                   for slug in args.optional.split(",") if slug.strip()]},
    }
    if args.dry_run:
        shown = dict(metadata, changelog=f"<{len(notes)} characters from {args.notes}>")
        print(json.dumps(shown, indent=2))
        print(f"Dry run: would upload {args.jar} to CurseForge project {args.project}.")
        return

    with open(args.jar, "rb") as f:
        body, content_type = multipart({"metadata": json.dumps(metadata)}, "file", os.path.basename(args.jar), f.read())
    result = request(token, f"/projects/{args.project}/upload-file", body, content_type)
    print(f"Uploaded {os.path.basename(args.jar)} to CurseForge project {args.project} as file {result.get('id')}.")


if __name__ == "__main__":
    main()
