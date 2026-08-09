param(
  [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $root "config.local.json"
$config = $null

if (Test-Path $configPath) {
  try {
    $config = Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json
  } catch {
    Write-Warning "Could not read config.local.json. Falling back to environment variables."
  }
}

$mapsApiKey = $config.yandexMapsApiKey
$suggestApiKey = $config.yandexSuggestApiKey

if ([string]::IsNullOrWhiteSpace($mapsApiKey)) {
  $mapsApiKey = [Environment]::GetEnvironmentVariable("YANDEX_MAPS_API_KEY")
}

if ([string]::IsNullOrWhiteSpace($suggestApiKey)) {
  $suggestApiKey = [Environment]::GetEnvironmentVariable("YANDEX_SUGGEST_API_KEY")
}

$legacyApiKey = [Environment]::GetEnvironmentVariable("YANDEX_API_KEY")

if ([string]::IsNullOrWhiteSpace($mapsApiKey)) {
  $mapsApiKey = $legacyApiKey
}

if ([string]::IsNullOrWhiteSpace($suggestApiKey)) {
  $suggestApiKey = $legacyApiKey
}

if ([string]::IsNullOrWhiteSpace($mapsApiKey)) {
  Write-Warning "Environment variable YANDEX_MAPS_API_KEY is not set. The map won't load."
}

if ([string]::IsNullOrWhiteSpace($suggestApiKey)) {
  Write-Warning "Environment variable YANDEX_SUGGEST_API_KEY is not set. Address search endpoints will return errors."
}

function Get-StatusDescription {
  param(
    [int]$StatusCode
  )

  switch ($StatusCode) {
    200 { "OK" }
    204 { "No Content" }
    400 { "Bad Request" }
    404 { "Not Found" }
    405 { "Method Not Allowed" }
    500 { "Internal Server Error" }
    default { "OK" }
  }
}

function Send-HttpResponse {
  param(
    [Parameter(Mandatory = $true)]$Stream,
    [int]$StatusCode = 200,
    [string]$ContentType = "text/plain; charset=utf-8",
    [byte[]]$Body = @()
  )

  $statusLine = "HTTP/1.1 $StatusCode $(Get-StatusDescription -StatusCode $StatusCode)`r`n"
  $headers = @(
    "Content-Type: $ContentType",
    "Content-Length: $($Body.Length)",
    "Connection: close"
  ) -join "`r`n"

  $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($statusLine + $headers + "`r`n`r`n")
  $Stream.Write($headerBytes, 0, $headerBytes.Length)
  if ($Body.Length -gt 0) {
    $Stream.Write($Body, 0, $Body.Length)
  }
  $Stream.Flush()
}

function Send-JsonResponse {
  param(
    [Parameter(Mandatory = $true)]$Stream,
    [Parameter(Mandatory = $true)]$Payload,
    [int]$StatusCode = 200
  )

  $json = $Payload | ConvertTo-Json -Depth 8
  $body = [System.Text.Encoding]::UTF8.GetBytes($json)
  Send-HttpResponse -Stream $Stream -StatusCode $StatusCode -ContentType "application/json; charset=utf-8" -Body $body
}

function Send-FileResponse {
  param(
    [Parameter(Mandatory = $true)]$Stream,
    [Parameter(Mandatory = $true)][string]$FilePath
  )

  if (-not (Test-Path $FilePath)) {
    Send-JsonResponse -Stream $Stream -Payload @{ error = "not_found" } -StatusCode 404
    return
  }

  $extension = [System.IO.Path]::GetExtension($FilePath).ToLowerInvariant()
  $contentType = switch ($extension) {
    ".html" { "text/html; charset=utf-8" }
    ".css" { "text/css; charset=utf-8" }
    ".js" { "application/javascript; charset=utf-8" }
    ".json" { "application/json; charset=utf-8" }
    default { "application/octet-stream" }
  }

  $bytes = [System.IO.File]::ReadAllBytes($FilePath)
  Send-HttpResponse -Stream $Stream -StatusCode 200 -ContentType $contentType -Body $bytes
}

function Send-IndexResponse {
  param(
    [Parameter(Mandatory = $true)]$Stream
  )

  $templatePath = Join-Path $root "index.html"
  if (-not (Test-Path $templatePath)) {
    Send-JsonResponse -Stream $Stream -Payload @{ error = "not_found" } -StatusCode 404
    return
  }

  $template = [System.IO.File]::ReadAllText($templatePath, [System.Text.Encoding]::UTF8)
  $html = $template.Replace("__YANDEX_MAPS_API_KEY__", $mapsApiKey)
  $body = [System.Text.Encoding]::UTF8.GetBytes($html)
  Send-HttpResponse -Stream $Stream -StatusCode 200 -ContentType "text/html; charset=utf-8" -Body $body
}

function Parse-QueryString {
  param(
    [string]$Query
  )

  $result = @{}
  if ([string]::IsNullOrWhiteSpace($Query)) {
    return $result
  }

  foreach ($pair in $Query.TrimStart("?").Split("&", [System.StringSplitOptions]::RemoveEmptyEntries)) {
    $parts = $pair.Split("=", 2)
    $key = [uri]::UnescapeDataString($parts[0])
    $value = if ($parts.Length -gt 1) { [uri]::UnescapeDataString($parts[1]) } else { "" }
    $result[$key] = $value
  }

  return $result
}

function Invoke-YandexSuggest {
  param(
    [Parameter(Mandatory = $true)][string]$Query
  )

  if ([string]::IsNullOrWhiteSpace($suggestApiKey)) {
    throw "missing_api_key"
  }

  $uri = "https://suggest-maps.yandex.ru/v1/suggest?apikey=$([uri]::EscapeDataString($suggestApiKey))&text=$([uri]::EscapeDataString($Query))&lang=ru_RU&results=6&print_address=1"
  try {
    $response = Invoke-RestMethod -Uri $uri -Method Get
  } catch {
    $message = $_.Exception.Message
    if ($message -match "403") { throw "suggest_access_denied" }
    if ($message -match "401") { throw "suggest_invalid_key" }
    throw "suggest_request_failed"
  }

  $items = @()
  foreach ($result in $response.results) {
    $items += @{
      title = $result.title.text
      subtitle = $result.subtitle.text
    }
  }

  return @{ items = $items }
}

function Invoke-YandexGeocode {
  param(
    [Parameter(Mandatory = $true)][string]$Address
  )

  if ([string]::IsNullOrWhiteSpace($mapsApiKey)) {
    throw "missing_api_key"
  }

  $uri = "https://geocode-maps.yandex.ru/v1/?apikey=$([uri]::EscapeDataString($mapsApiKey))&geocode=$([uri]::EscapeDataString($Address))&lang=ru_RU&format=json&results=1"
  try {
    $response = Invoke-RestMethod -Uri $uri -Method Get
  } catch {
    $message = $_.Exception.Message
    if ($message -match "403") { throw "geocode_access_denied" }
    if ($message -match "401") { throw "geocode_invalid_key" }
    throw "geocode_request_failed"
  }
  $feature = $response.response.GeoObjectCollection.featureMember | Select-Object -First 1

  if ($null -eq $feature) {
    throw "address_not_found"
  }

  $pos = $feature.GeoObject.Point.pos
  $parts = $pos -split " "

  if ($parts.Length -lt 2) {
    throw "invalid_coordinates"
  }

  return @{
    lon = [double]::Parse($parts[0], [System.Globalization.CultureInfo]::InvariantCulture)
    lat = [double]::Parse($parts[1], [System.Globalization.CultureInfo]::InvariantCulture)
  }
}

function Handle-Route {
  param(
    [Parameter(Mandatory = $true)]$Stream,
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$RawTarget
  )

  if ($Method -ne "GET") {
    Send-JsonResponse -Stream $Stream -Payload @{ error = "method_not_allowed" } -StatusCode 405
    return
  }

  $targetUri = [uri]("http://localhost$RawTarget")
  $path = $targetUri.AbsolutePath
  $query = Parse-QueryString -Query $targetUri.Query

  switch ($path) {
    "/" {
      Send-IndexResponse -Stream $Stream
    }
    "/index.html" {
      Send-IndexResponse -Stream $Stream
    }
    "/styles.css" {
      Send-FileResponse -Stream $Stream -FilePath (Join-Path $root "styles.css")
    }
    "/app.js" {
      Send-FileResponse -Stream $Stream -FilePath (Join-Path $root "app.js")
    }
    "/favicon.ico" {
      Send-HttpResponse -Stream $Stream -StatusCode 204 -ContentType "text/plain; charset=utf-8" -Body @()
    }
    "/api/suggest" {
      $search = $query["q"]
      if ([string]::IsNullOrWhiteSpace($search)) {
        Send-JsonResponse -Stream $Stream -Payload @{ items = @() }
      } else {
        Send-JsonResponse -Stream $Stream -Payload (Invoke-YandexSuggest -Query $search)
      }
    }
    "/api/geocode" {
      $address = $query["address"]
      if ([string]::IsNullOrWhiteSpace($address)) {
        Send-JsonResponse -Stream $Stream -Payload @{ error = "address_required" } -StatusCode 400
      } else {
        Send-JsonResponse -Stream $Stream -Payload (Invoke-YandexGeocode -Address $address)
      }
    }
    default {
      Send-JsonResponse -Stream $Stream -Payload @{ error = "not_found" } -StatusCode 404
    }
  }
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)

try {
  $listener.Start()
  Write-Host "VeloPath server is running at http://localhost:$Port/"

  while ($true) {
    $client = $listener.AcceptTcpClient()

    try {
      $stream = $client.GetStream()
      $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
      $requestLine = $reader.ReadLine()

      if ([string]::IsNullOrWhiteSpace($requestLine)) {
        Send-JsonResponse -Stream $stream -Payload @{ error = "bad_request" } -StatusCode 400
        continue
      }

      while ($true) {
        $headerLine = $reader.ReadLine()
        if ([string]::IsNullOrEmpty($headerLine)) {
          break
        }
      }

      $parts = $requestLine.Split(" ")
      if ($parts.Length -lt 2) {
        Send-JsonResponse -Stream $stream -Payload @{ error = "bad_request" } -StatusCode 400
        continue
      }

      Handle-Route -Stream $stream -Method $parts[0] -RawTarget $parts[1]
    } catch {
      if ($stream) {
        Send-JsonResponse -Stream $stream -Payload @{ error = $_.Exception.Message } -StatusCode 500
      }
    } finally {
      if ($reader) {
        $reader.Dispose()
      }
      if ($stream) {
        $stream.Dispose()
      }
      $client.Dispose()
    }
  }
} finally {
  $listener.Stop()
}
