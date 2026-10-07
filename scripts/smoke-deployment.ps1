param([string]$BaseUrl = 'http://localhost:8090')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(15)

function Get-Response([string]$Path) {
    $response = $client.GetAsync($BaseUrl.TrimEnd('/') + $Path).GetAwaiter().GetResult()
    try {
        [PSCustomObject]@{
            Status = [int]$response.StatusCode
            ContentType = [string]$response.Content.Headers.ContentType
            Body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }
    } finally {
        $response.Dispose()
    }
}

try {
    $homepage = Get-Response '/'
    if ($homepage.Status -ne 200 -or $homepage.Body -notmatch 'src="(/assets/[^" ]+\.js)"') {
        throw 'The homepage must serve the compiled frontend.'
    }
    $assetPath = $Matches[1]
    foreach ($path in @('/top/invalid-snapshot', ('/top/' + ('a' * 8000)))) {
        $page = Get-Response $path
        if ($page.Status -ne 200 -or $page.Body -ne $homepage.Body) {
            throw 'Shared links, including the maximum payload length, must serve index.html.'
        }
    }
    $asset = Get-Response $assetPath
    if ($asset.Status -ne 200 -or $asset.ContentType -notmatch 'javascript') {
        throw 'The compiled JavaScript asset is unavailable.'
    }
    if ((Get-Response '/assets/missing-smoke-test.js').Status -ne 404) {
        throw 'Missing assets must return 404 instead of index.html.'
    }
    $api = Get-Response '/api/v1/releases/search?q=x'
    if ($api.Status -ne 400 -or $api.ContentType -notmatch 'application/json' -or
        -not ($api.Body | ConvertFrom-Json).code) {
        throw 'API requests must reach the backend and return its JSON validation error.'
    }
    Write-Output 'Deployment smoke check passed: homepage, shared routes, assets and API proxy.'
} finally {
    $client.Dispose()
}
