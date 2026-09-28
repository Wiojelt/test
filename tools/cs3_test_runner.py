#!/usr/bin/env python3
"""Static/package and HTTP artifact checks for CloudStream repository manifests.

This runner deliberately does not claim that catalogs, search, playback, or
subtitle callbacks work: exercising those requires the CloudStream Android/JVM
runtime. Such unverified providers are reported as CS2004_RISK, not ACTIVE.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

USER_AGENT = "CS3-Test-Runner/1.0 (+repository artifact verification)"


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def fetch(url: str, timeout: float, max_bytes: int) -> tuple[bytes | None, int | None, str | None]:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            content = response.read(max_bytes + 1)
            if len(content) > max_bytes:
                return None, status, f"artifact exceeds configured size limit ({max_bytes} bytes)"
            return content, status, None
    except urllib.error.HTTPError as exc:
        return None, exc.code, f"HTTP {exc.code}"
    except Exception as exc:  # network/TLS/DNS failures are report data
        return None, None, f"{type(exc).__name__}: {exc}"


def verify_artifact(data: bytes, item: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    expected_size = item.get("fileSize")
    if isinstance(expected_size, int) and expected_size != len(data):
        failures.append(f"fileSize mismatch: catalog={expected_size}, downloaded={len(data)}")

    digest = hashlib.sha256(data).hexdigest()
    expected_hash = str(item.get("fileHash") or item.get("hash") or "")
    expected_hash = expected_hash.removeprefix("sha256-").lower()
    if expected_hash and expected_hash != digest:
        failures.append(f"SHA-256 mismatch: catalog={expected_hash}, downloaded={digest}")

    try:
        import io

        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            bad_member = archive.testzip()
            if bad_member:
                failures.append(f"corrupt ZIP member: {bad_member}")
            manifest = json.loads(archive.read("manifest.json"))
        if not manifest.get("pluginClassName"):
            failures.append("manifest.json is missing pluginClassName")
        if manifest.get("version") != item.get("version"):
            failures.append(
                f"version mismatch: catalog={item.get('version')}, package={manifest.get('version')}"
            )
    except Exception as exc:
        failures.append(f"invalid .cs3/manifest.json: {type(exc).__name__}: {exc}")

    return failures


def check_site(url: str, timeout: float) -> tuple[int | None, str | None]:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT}, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response.read(256)
            return response.status, None
    except urllib.error.HTTPError as exc:
        if exc.code in (403, 429, 503):
            return exc.code, "CLOUDFLARE_BLOCK" if exc.code in (403, 503) else "HTTP_ERROR"
        return exc.code, "HTTP_ERROR"
    except Exception as exc:
        return None, f"{type(exc).__name__}: {exc}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", nargs="*", type=Path, help="repository folder(s) or plugins.json files")
    parser.add_argument("--catalog-url", action="append", default=[], help="remote plugins.json URL; repeat per repository")
    parser.add_argument("--site-map", type=Path, help="optional JSON map: internalName -> site URL")
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--max-mb", type=int, default=120)
    parser.add_argument("--report", type=Path, default=Path("tools/provider-test-report.json"))
    parser.add_argument(
        "--markdown-report",
        type=Path,
        default=Path("tools/provider-test-report.md"),
        help="write a human-readable companion report",
    )
    parser.add_argument("--render-existing", action="store_true", help="render Markdown from the existing JSON report without network requests")
    parser.add_argument("--offline", action="store_true", help="use local .cs3 files only; never fetch URLs")
    args = parser.parse_args()

    if not args.render_existing and not args.repo and not args.catalog_url:
        parser.error("provide at least one repo path or --catalog-url")
    if args.render_existing:
        report = load_json(args.report)
        results = report.get("repositories", [])
    else:
        results = []
    site_map = load_json(args.site_map) if args.site_map else {}
    max_bytes = args.max_mb * 1024 * 1024

    sources: list[tuple[str, Path | str]] = [("path", repo) for repo in args.repo]
    sources.extend(("url", url) for url in args.catalog_url)
    for source_type, source in ([] if args.render_existing else sources):
        if source_type == "path":
            source_path = Path(source).resolve()
            repo = source_path if source_path.is_dir() else source_path.parent
            catalog_path = source_path / "plugins.json" if source_path.is_dir() else source_path
            repo_result: dict[str, Any] = {"repository": repo.name, "catalog": str(catalog_path), "providers": []}
            try:
                catalog = load_json(catalog_path)
            except Exception as exc:
                repo_result["error"] = f"{type(exc).__name__}: {exc}"
                results.append(repo_result)
                continue
        else:
            catalog_url = str(source)
            parsed = urllib.parse.urlparse(catalog_url)
            label = Path(parsed.path).parent.name or parsed.netloc
            repo = None
            repo_result = {"repository": label, "catalogUrl": catalog_url, "providers": []}
            body, status, error = fetch(catalog_url, args.timeout, min(max_bytes, 5 * 1024 * 1024))
            repo_result["catalogHttpStatus"] = status
            if body is None:
                repo_result["error"] = error or "catalog download failed"
                results.append(repo_result)
                continue
            try:
                catalog = json.loads(body.decode("utf-8-sig"))
            except Exception as exc:
                repo_result["error"] = f"invalid catalog JSON: {type(exc).__name__}: {exc}"
                results.append(repo_result)
                continue
        try:
            if not isinstance(catalog, list):
                raise ValueError("plugins.json root must be an array")
        except Exception as exc:
            repo_result["error"] = f"{type(exc).__name__}: {exc}"
            results.append(repo_result)
            continue

        seen: set[str] = set()
        for item in catalog:
            internal_name = str(item.get("internalName") or item.get("name") or "").strip()
            provider: dict[str, Any] = {
                "name": item.get("name"),
                "internalName": internal_name,
                "stages": {
                    "catalog_metadata": "PASS",
                    "build": "NOT_RUN_SOURCE_BUILD_NOT_CONFIGURED",
                    "site_api": "NOT_MAPPED",
                    "main_page": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                    "search": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                    "load": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                    "loadLinks": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                    "subtitles": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                    "live_hls_segments": "NOT_RUN_CLOUDSTREAM_RUNTIME_REQUIRED",
                },
                "issues": [],
            }

            if not internal_name:
                provider["issues"].append("missing internalName/name")
                provider["stages"]["catalog_metadata"] = "FAIL"
            elif internal_name in seen:
                provider["issues"].append(f"duplicate internalName: {internal_name}")
                provider["stages"]["catalog_metadata"] = "FAIL"
            else:
                seen.add(internal_name)

            url = str(item.get("url") or "")
            data: bytes | None = None
            http_status: int | None = None
            if not url.startswith(("https://", "http://")):
                provider["issues"].append("invalid artifact URL")
            elif args.offline:
                local = (repo / Path(url.split("?", 1)[0]).name) if repo else None
                if local and local.is_file():
                    data = local.read_bytes()
                    provider["stages"]["artifact_http"] = "SKIPPED_OFFLINE"
                else:
                    provider["stages"]["artifact_http"] = "NOT_AVAILABLE_LOCALLY"
            else:
                data, http_status, error = fetch(url, args.timeout, max_bytes)
                provider["stages"]["artifact_http"] = "PASS" if http_status == 200 and data is not None else "FAIL"
                provider["artifact_http_status"] = http_status
                if error:
                    provider["issues"].append(error)

            if data is not None:
                provider["artifact_bytes"] = len(data)
                provider["sha256"] = hashlib.sha256(data).hexdigest()
                package_failures = verify_artifact(data, item)
                provider["stages"]["package_integrity"] = "PASS" if not package_failures else "FAIL"
                provider["issues"].extend(package_failures)
            else:
                provider["stages"]["package_integrity"] = "NOT_VERIFIED"

            site = site_map.get(internal_name)
            if site:
                status, error = check_site(str(site), args.timeout)
                provider["site_url"] = str(site)
                provider["site_http_status"] = status
                provider["stages"]["site_api"] = "PASS" if status and 200 <= status < 400 else "FAIL"
                if error:
                    provider["issues"].append(error)

            if provider["stages"].get("artifact_http") == "FAIL":
                provider["status"] = "HTTP_ERROR"
            elif provider["stages"].get("package_integrity") == "FAIL" or provider["stages"].get("catalog_metadata") == "FAIL":
                provider["status"] = "BUILD_FAIL"
            elif provider["stages"].get("site_api") == "FAIL":
                issue_text = " ".join(provider["issues"])
                if "CLOUDFLARE_BLOCK" in issue_text:
                    provider["status"] = "CLOUDFLARE_BLOCK"
                elif provider.get("site_http_status") is None:
                    provider["status"] = "SITE_DOWN"
                else:
                    provider["status"] = "HTTP_ERROR"
            else:
                provider["status"] = "CS2004_RISK"
                provider["issues"].append(
                    "CloudStream runtime catalog/search/load/loadLinks/subtitle/playback flow not executed; do not infer ACTIVE."
                )
            repo_result["providers"].append(provider)
        results.append(repo_result)

    if not args.render_existing:
        report = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "runner": "cs3_test_runner.py",
            "scope": "repository metadata, .cs3 package/hash/version, artifact HTTP, optional base-site HTTP smoke only",
            "runtimeLimitation": "CloudStream runtime callbacks and real playback are not simulated; unresolved entries remain CS2004_RISK.",
            "repositories": results,
        }
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    markdown = [
        "# CloudStream provider verification report",
        "",
        f"Generated: {report['generatedAt']}",
        "",
        f"Scope: {report['scope']}.",
        "",
        f"> Runtime limitation: {report['runtimeLimitation']}",
        "",
        "| Repository/catalog | Catalog HTTP | Providers | Status summary |",
        "|---|---:|---:|---|",
    ]
    status_totals: dict[str, int] = {}
    provider_total = 0
    for repository in results:
        providers = repository.get("providers", [])
        provider_total += len(providers)
        statuses: dict[str, int] = {}
        for provider in providers:
            status = str(provider.get("status", "UNKNOWN"))
            statuses[status] = statuses.get(status, 0) + 1
            status_totals[status] = status_totals.get(status, 0) + 1
        label = repository.get("repository") or repository.get("catalogUrl") or repository.get("catalog", "unknown")
        status_text = ", ".join(f"{key}: {value}" for key, value in sorted(statuses.items())) or "no provider entries"
        http_status = repository.get("catalogHttpStatus", "n/a")
        markdown.append(f"| `{label}` | {http_status} | {len(providers)} | {status_text} |")

    markdown.extend(["", f"Total catalog entries: **{provider_total}**.", ""])
    markdown.append("## Provider results")
    markdown.append("")
    for repository in results:
        label = repository.get("repository") or repository.get("catalogUrl") or repository.get("catalog", "unknown")
        markdown.extend([f"### {label}", ""])
        if repository.get("error"):
            markdown.extend([f"Catalog error: `{repository['error']}`", ""])
            continue
        markdown.extend(["| Provider | internalName | Status | Artifact | SHA-256 |", "|---|---|---|---:|---|"])
        for provider in repository.get("providers", []):
            stages = provider.get("stages", {})
            artifact_ok = stages.get("artifact_http", "n/a")
            package_ok = stages.get("package_integrity", "n/a")
            integrity = f"{artifact_ok}/{package_ok}; {provider.get('artifact_bytes', 'n/a')} bytes"
            digest = provider.get("sha256", "n/a")
            markdown.append(
                f"| {provider.get('name', 'n/a')} | `{provider.get('internalName', 'n/a')}` | "
                f"`{provider.get('status', 'UNKNOWN')}` | {integrity} | `{digest}` |"
            )
        markdown.append("")
    markdown.extend(["## Status totals", ""])
    markdown.extend(f"- `{status}`: {count}" for status, count in sorted(status_totals.items()))
    markdown.append("")
    args.markdown_report.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_report.write_text("\n".join(markdown), encoding="utf-8")

    counts: dict[str, int] = {}
    total = 0
    for repo in results:
        for provider in repo.get("providers", []):
            total += 1
            counts[provider["status"]] = counts.get(provider["status"], 0) + 1
    print(json.dumps({"providers": total, "statuses": counts, "report": str(args.report.resolve()), "markdownReport": str(args.markdown_report.resolve())}, ensure_ascii=False))
    return 1 if any(status in counts for status in ("BUILD_FAIL", "HTTP_ERROR")) else 0


if __name__ == "__main__":
    sys.exit(main())
