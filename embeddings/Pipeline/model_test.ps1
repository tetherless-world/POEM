# model_test.ps1 — for each of 3 embedding models:
#   Step 1: confirm the model responds and print embedding dimension
#   Step 2: regenerate all .npy vectors with that model
#   Step 3: run section-scoped evaluation (default)
#   Step 4: run all-sections evaluation (--no-scope)
#   Step 5: extract both summaries into model_comparison.txt
#
# Usage (from project root):
#   .\embeddings\model_test.ps1

$baseUrl   = $env:EMBED_BASE_URL
if (-not $baseUrl) { $baseUrl = "http://idea-llm-01.idea.rpi.edu:11435/v1" }

# ---------------------------------------------------------------------------
# Discover models available on the embedding server.
# Uses the OpenAI-compatible GET /v1/models endpoint so the script stays
# accurate as the server's loaded model lineup changes.
# ---------------------------------------------------------------------------
Write-Host "Discovering models on $baseUrl ..." -ForegroundColor Yellow
$discovery = python -c "
from openai import OpenAI
try:
    client = OpenAI(base_url='$baseUrl', api_key='not-needed')
    for m in client.models.list().data:
        print(m.id)
except Exception as e:
    print('DISCOVERY_FAIL ' + str(e))
"

if ($discovery -match '^DISCOVERY_FAIL') {
    Write-Host "ERROR: could not list models from $baseUrl" -ForegroundColor Red
    Write-Host "  $discovery" -ForegroundColor Red
    Write-Host "  (Check that the server is reachable -- e.g. VPN, EMBED_BASE_URL.)" -ForegroundColor Red
    exit 1
}

$models = @($discovery | ForEach-Object { $_.Trim() } | Where-Object { $_ })

if ($models.Count -eq 0) {
    Write-Host "ERROR: server at $baseUrl returned an empty model list." -ForegroundColor Red
    exit 1
}

Write-Host "Discovered $($models.Count) model(s): $($models -join ', ')" -ForegroundColor Green

$comparisonFile = "embeddings/evaluation_results/model_comparison.txt"
New-Item -ItemType Directory -Force -Path "embeddings/evaluation_results" | Out-Null
"POEM Model Comparison -- $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Out-File $comparisonFile -Encoding utf8
"" | Add-Content $comparisonFile -Encoding utf8

function Get-Summary($file) {
    $content = Get-Content $file -Raw -Encoding utf8
    if ($content -match '(?s)(={10,}\r?\nSUMMARY\r?\n.*)') {
        return $Matches[1].TrimEnd()
    }
    return "(summary section not found)"
}

foreach ($model in $models) {
    Write-Host "`n$(('=' * 72))" -ForegroundColor Cyan
    Write-Host "MODEL: $model" -ForegroundColor Cyan
    Write-Host "$(('=' * 72))" -ForegroundColor Cyan

    $env:EMBED_MODEL = $model

    # ------------------------------------------------------------------
    # Step 1 — confirm model responds and print embedding dimension
    # ------------------------------------------------------------------
    Write-Host "`n[Step 1] Testing model endpoint..." -ForegroundColor Yellow
    $dimResult = python -c "
from openai import OpenAI
client = OpenAI(base_url='$baseUrl', api_key='not-needed')
try:
    r = client.embeddings.create(model='$model', input=['test'])
    print('OK dim=' + str(len(r.data[0].embedding)))
except Exception as e:
    print('FAIL ' + str(e))
"
    Write-Host "  $dimResult"
    if ($dimResult -notlike "OK*") {
        Write-Host "  Skipping model (endpoint not responding)." -ForegroundColor Red
        "MODEL: $model -- SKIPPED (endpoint test failed: $dimResult)" | Add-Content $comparisonFile -Encoding utf8
        ("=" * 72) | Add-Content $comparisonFile -Encoding utf8
        "" | Add-Content $comparisonFile -Encoding utf8
        continue
    }

    # ------------------------------------------------------------------
    # Step 2 — regenerate all .npy vectors with this model
    # ------------------------------------------------------------------
    Write-Host "`n[Step 2] Regenerating embeddings..." -ForegroundColor Yellow
    python embeddings/generate_embeddings.py

    # ------------------------------------------------------------------
    # Step 3 — section-scoped evaluation (each query searches its own section)
    # ------------------------------------------------------------------
    Write-Host "`n[Step 3] Running section-scoped evaluation..." -ForegroundColor Yellow
    python embeddings/evaluate_search.py

    $scopedFile = Get-ChildItem "embeddings/evaluation_results/evaluation_*.txt" |
                  Sort-Object LastWriteTime -Descending |
                  Select-Object -First 1

    # ------------------------------------------------------------------
    # Step 4 — all-sections evaluation (original behaviour, --no-scope)
    # ------------------------------------------------------------------
    Write-Host "`n[Step 4] Running all-sections evaluation (--no-scope)..." -ForegroundColor Yellow
    python embeddings/evaluate_search.py --no-scope

    $unscopedFile = Get-ChildItem "embeddings/evaluation_results/evaluation_*.txt" |
                    Sort-Object LastWriteTime -Descending |
                    Select-Object -First 1

    # ------------------------------------------------------------------
    # Step 5 — append both summaries to model_comparison.txt
    # ------------------------------------------------------------------
    Write-Host "`n[Step 5] Writing summaries to $comparisonFile..." -ForegroundColor Yellow

    "MODEL: $model  |  dim: $($dimResult -replace 'OK dim=','')" | Add-Content $comparisonFile -Encoding utf8
    "" | Add-Content $comparisonFile -Encoding utf8

    "--- SCOPED (each query searched within its expected section) ---" | Add-Content $comparisonFile -Encoding utf8
    if ($scopedFile) {
        (Get-Summary $scopedFile.FullName) | Add-Content $comparisonFile -Encoding utf8
    } else {
        "(no scoped output file found)" | Add-Content $comparisonFile -Encoding utf8
    }
    "" | Add-Content $comparisonFile -Encoding utf8

    "--- UNSCOPED (all sections searched together) ---" | Add-Content $comparisonFile -Encoding utf8
    if ($unscopedFile) {
        (Get-Summary $unscopedFile.FullName) | Add-Content $comparisonFile -Encoding utf8
    } else {
        "(no unscoped output file found)" | Add-Content $comparisonFile -Encoding utf8
    }
    "" | Add-Content $comparisonFile -Encoding utf8
    ("=" * 72) | Add-Content $comparisonFile -Encoding utf8
    "" | Add-Content $comparisonFile -Encoding utf8
}

Write-Host "`nDone. Comparison saved to: $comparisonFile" -ForegroundColor Green
