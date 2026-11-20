#!/usr/bin/env python3
"""Upload Rattil's release bundle (AAB) to Google Play and roll it out, through the Google Play Developer API.

No browser and no size limit (resumable upload). Access: the service account in SERVICE_ACCOUNT, without a key file;
the active gcloud user impersonates it (roles/iam.serviceAccountTokenCreator) for a 15-minute androidpublisher token.
In the Play Console the account needs release rights for Rattil. Copied from Honey Blast's play-store/tools.

Version and notes: versionCode/versionName come from app/build.gradle.kts (the upload must report the same versionCode);
release notes from play-store/release-notes/<versionName>.txt, in the Play Console format (one <lang>…</lang> block per
language, as "Aus einem vorherigen Release kopieren" fills the field).

  play_release.py                    upload app/build/outputs/bundle/madaniRelease/app-madani-release.aab, production, 100 %
  play_release.py --rollout 0.2      staged rollout to 20 % of users
  play_release.py --track internal   another track (internal, alpha, beta, production)
  play_release.py --dry-run          upload and validate, then throw the edit away: nothing changes in Play
  play_release.py --check            only show the track's releases (tests access; uploads nothing)
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

PACKAGE = "com.appcoholic.quran"
SERVICE_ACCOUNT = "rattil-play-publisher@quran-82a42.iam.gserviceaccount.com"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD_API = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
REPO = Path(__file__).resolve().parents[2]
AAB = REPO / "app/build/outputs/bundle/madaniRelease/app-madani-release.aab"
NOTES = REPO / "play-store/release-notes"


class PlayError(RuntimeError):
    pass


def token():
    user = subprocess.run(["gcloud", "auth", "print-access-token"], capture_output=True, text=True,
                          check=True, timeout=60).stdout.strip()
    req = urllib.request.Request(
        f"https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/{SERVICE_ACCOUNT}:generateAccessToken",
        data=json.dumps({"scope": ["https://www.googleapis.com/auth/androidpublisher"], "lifetime": "900s"}).encode(),
        method="POST", headers={"Authorization": f"Bearer {user}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)["accessToken"]


def call(tok, method, url, body=None, data=None, headers=None, timeout=120):
    hdrs = {"Authorization": f"Bearer {tok}"}
    if body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    hdrs.update(headers or {})
    req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return (json.loads(raw) if raw else {}), r.headers
    except urllib.error.HTTPError as exc:
        text = exc.read().decode(errors="replace")
        try:
            text = json.loads(text)["error"]["message"]
        except (ValueError, KeyError):
            pass
        raise PlayError(f"{method} {url.split(PACKAGE)[-1] or url}: HTTP {exc.code}: {text}") from None


def gradle_version():
    gradle = (REPO / "app/build.gradle.kts").read_text()
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    if not code or not name:
        raise PlayError("versionCode/versionName not found in app/build.gradle.kts")
    return int(code.group(1)), name.group(1)


def release_notes(version_name):
    path = NOTES / f"{version_name}.txt"
    if not path.exists():
        raise PlayError(f"no release notes: {path} (one <lang>…</lang> block per language)")
    blocks = re.findall(r"<([A-Za-z0-9-]+)>\n(.*?)\n</\1>", path.read_text(), re.S)
    if not blocks:
        raise PlayError(f"{path}: no <lang>…</lang> blocks")
    too_long = [lang for lang, text in blocks if len(text) > 500]
    if too_long:
        raise PlayError(f"{path}: over 500 characters in {', '.join(too_long)}")
    return [{"language": lang, "text": text.strip()} for lang, text in blocks]


def upload_bundle(tok, edit, aab):
    data = aab.read_bytes()
    _, headers = call(tok, "POST", f"{UPLOAD_API}/edits/{edit}/bundles?uploadType=resumable", data=b"",
                      headers={"X-Upload-Content-Type": "application/octet-stream",
                               "X-Upload-Content-Length": str(len(data)), "Content-Length": "0"})
    session = headers.get("Location")
    if not session:
        raise PlayError("the bundle upload returned no session URL")
    # Play processes the bundle before it answers; that can take a few minutes
    bundle, _ = call(tok, "PUT", session, data=data, headers={"Content-Type": "application/octet-stream"}, timeout=900)
    local = hashlib.sha256(data).hexdigest()
    if bundle.get("sha256") != local:
        raise PlayError(f"checksum differs: Play {bundle.get('sha256')}, local {local}")
    return bundle


def check(track):
    tok = token()
    edit = call(tok, "POST", f"{API}/edits", body={})[0]["id"]
    try:
        releases = call(tok, "GET", f"{API}/edits/{edit}/tracks/{track}")[0].get("releases", [])
    finally:
        call(tok, "DELETE", f"{API}/edits/{edit}")
    for r in releases:
        print(f"{track}: {r.get('name')} · {r.get('status')} · versionCodes {r.get('versionCodes')} · "
              f"notes in {len(r.get('releaseNotes', []))} languages")
    print("access ok" if releases else f"access ok, no release on {track}")
    return 0


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--aab", type=Path, default=AAB)
    ap.add_argument("--track", default="production")
    ap.add_argument("--rollout", type=float, default=1.0, help="share of users, 0 < x <= 1 (default 1 = everyone)")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--promote-from", metavar="TRACK",
                    help="no upload: put the versionCode from app/build.gradle.kts, already on TRACK (e.g. internal), on --track")
    args = ap.parse_args(argv)
    if args.check:
        return check(args.track)
    if not 0 < args.rollout <= 1:
        raise PlayError("--rollout must be above 0 and at most 1")

    code, name = gradle_version()
    notes = release_notes(name)
    print(f"{args.aab.name}: {args.aab.stat().st_size / 1e6:.1f} MB, version {code} ({name}), "
          f"notes in {len(notes)} languages, track {args.track}, rollout {args.rollout:.0%}")

    tok = token()
    edit = call(tok, "POST", f"{API}/edits", body={})[0]["id"]
    try:
        if args.promote_from:
            # the same bundle that was tested: Play does not take a versionCode twice, so it is not uploaded again
            source = call(tok, "GET", f"{API}/edits/{edit}/tracks/{args.promote_from}")[0].get("releases", [])
            if not any(str(code) in r.get("versionCodes", []) for r in source):
                raise PlayError(f"versionCode {code} is not on the {args.promote_from} track")
            print(f"promoting versionCode {code} from {args.promote_from}")
        else:
            bundle = upload_bundle(tok, edit, args.aab)
            if bundle.get("versionCode") != code:
                raise PlayError(f"the bundle has versionCode {bundle.get('versionCode')}, app/build.gradle.kts says {code}")
            print(f"uploaded: versionCode {bundle['versionCode']}, sha256 {bundle['sha256'][:16]}… matches")

        release = {"name": f"{code} ({name})", "versionCodes": [str(code)], "releaseNotes": notes,
                   "status": "completed" if args.rollout == 1 else "inProgress"}
        if args.rollout < 1:
            release["userFraction"] = args.rollout
        call(tok, "PUT", f"{API}/edits/{edit}/tracks/{args.track}",
             body={"track": args.track, "releases": [release]})
        call(tok, "POST", f"{API}/edits/{edit}:validate")
        print("validated")
        if args.dry_run:
            call(tok, "DELETE", f"{API}/edits/{edit}")
            print("dry run: edit deleted, nothing changed in Play")
            return 0
        # without managed publishing the commit sends the change to Google's review
        call(tok, "POST", f"{API}/edits/{edit}:commit")
        print(f"committed: {name} is with Google for review on {args.track}")
        return 0
    except BaseException:
        try:
            call(tok, "DELETE", f"{API}/edits/{edit}")
        except PlayError:
            pass
        raise


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except (PlayError, subprocess.SubprocessError, urllib.error.URLError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)
