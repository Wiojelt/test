# CS3 test runner

Run the repository/artifact checks against local repository folders or remote `plugins.json` catalogs:

```powershell
python tools/cs3_test_runner.py .\repo-folder --catalog-url https://raw.githubusercontent.com/OWNER/REPO/builds/plugins.json
```

Repeat `--catalog-url` for each additional catalog. Use `--site-map tools/provider-sites.json` only for manually reviewed `internalName` → home URL mappings. The default report path is `tools/provider-test-report.json`.

The runner verifies catalog JSON, duplicate `internalName` values, artifact HTTP status, declared byte size and SHA-256, ZIP integrity, and package manifest version/class metadata. It does **not** load provider callbacks in the CloudStream runtime. Catalog/search/detail/loadLinks/subtitle/live-playback stages therefore remain `NOT_RUN` and the aggregate provider state is `CS2004_RISK` until a real runtime test is performed. HTTP success is not playback proof.
