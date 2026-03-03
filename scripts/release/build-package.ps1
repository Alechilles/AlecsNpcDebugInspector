param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ConfigPath = ".release/publish-config.json",
    [string]$OutputDir = "artifacts",
    [string]$ArtifactPathOutputFile = "",
    [bool]$SkipTests = $false
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-NormalizedVersion {
    param([string]$RawVersion)
    return (($RawVersion.Trim()) -replace "^v", "")
}

function Get-ArtifactName {
    param(
        [object]$Config,
        [string]$NormalizedVersion
    )

    return $Config.artifactNameTemplate.Replace("{version}", $NormalizedVersion)
}

function Try-DownloadReleaseAsset {
    param(
        [object]$Config,
        [string]$NormalizedVersion,
        [string]$ArtifactPath
    )

    if ($env:GITHUB_ACTIONS -ne "true") {
        return $false
    }

    if ([string]::IsNullOrWhiteSpace($env:GITHUB_TOKEN)) {
        Write-Warning "GITHUB_TOKEN was not available; cannot download prebuilt release asset fallback."
        return $false
    }

    $repository = if ([string]::IsNullOrWhiteSpace($Config.repository)) { $env:GITHUB_REPOSITORY } else { $Config.repository }
    if ([string]::IsNullOrWhiteSpace($repository) -or ($repository -notmatch "/")) {
        Write-Warning "Repository value '$repository' is invalid for release asset fallback."
        return $false
    }

    $parts = $repository.Split("/", 2)
    $owner = $parts[0]
    $repo = $parts[1]
    $tag = "v$NormalizedVersion"
    $displayName = Get-ArtifactName -Config $Config -NormalizedVersion $NormalizedVersion

    $headers = @{
        Authorization = "Bearer $env:GITHUB_TOKEN"
        Accept = "application/vnd.github+json"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    try {
        $release = Invoke-RestMethod -Method Get -Headers $headers -Uri "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
    } catch {
        Write-Warning "Unable to load GitHub release '$tag' for fallback artifact: $($_.Exception.Message)"
        return $false
    }

    if (-not $release.assets -or $release.assets.Count -eq 0) {
        Write-Warning "Release '$tag' has no assets to use for fallback build."
        return $false
    }

    $asset = $release.assets |
        Where-Object { $_.label -eq $displayName -or $_.name -eq $displayName } |
        Select-Object -First 1

    if (-not $asset) {
        Write-Warning "Release '$tag' does not contain expected asset '$displayName'."
        return $false
    }

    $downloadHeaders = @{
        Authorization = "Bearer $env:GITHUB_TOKEN"
        Accept = "application/octet-stream"
        "X-GitHub-Api-Version" = "2022-11-28"
    }

    try {
        Invoke-WebRequest -Method Get -Headers $downloadHeaders -Uri "https://api.github.com/repos/$owner/$repo/releases/assets/$($asset.id)" -OutFile $ArtifactPath
    } catch {
        Write-Warning "Failed to download fallback release asset '$displayName': $($_.Exception.Message)"
        return $false
    }

    if (-not (Test-Path -Path $ArtifactPath)) {
        return $false
    }

    $downloaded = Get-Item -Path $ArtifactPath
    if ($downloaded.Length -le 0) {
        return $false
    }

    Write-Warning "Using prebuilt release asset fallback '$displayName' from tag '$tag'."
    return $true
}

if (-not (Test-Path -Path $ConfigPath)) {
    throw "Release config '$ConfigPath' was not found."
}

$config = Get-Content -Path $ConfigPath -Raw | ConvertFrom-Json
$normalizedVersion = Get-NormalizedVersion -RawVersion $Version

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$artifactName = Get-ArtifactName -Config $config -NormalizedVersion $normalizedVersion
$artifactPath = Join-Path $OutputDir $artifactName

switch ($config.packaging) {
    "jar" {
        $mvnArgs = @("-B", "clean", "test", "package")
        if ($SkipTests) {
            $mvnArgs = @("-B", "clean", "package", "-DskipTests")
        }

        $mavenCommands = @()
        if (Test-Path -Path ".\mvnw.cmd") {
            $mavenCommands += ".\mvnw.cmd"
        }

        $mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
        if ($null -ne $mvnCommand) {
            $mavenCommands += "mvn"
        }

        if ($mavenCommands.Count -eq 0) {
            throw "No Maven command was found. Expected '.\\mvnw.cmd' or 'mvn' on PATH."
        }

        $buildSucceeded = $false
        $lastExitCode = 1
        foreach ($mavenCommand in $mavenCommands) {
            $commandStart = Get-Date
            & $mavenCommand @mvnArgs
            $lastExitCode = $LASTEXITCODE

            $freshBuiltJar = Get-ChildItem -Path "target" -Filter "*.jar" -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -notlike "original-*" -and $_.LastWriteTime -ge $commandStart } |
                Sort-Object -Property LastWriteTime -Descending |
                Select-Object -First 1

            if ($lastExitCode -eq 0 -or $null -ne $freshBuiltJar) {
                if ($lastExitCode -ne 0 -and $null -ne $freshBuiltJar) {
                    Write-Warning "Maven command '$mavenCommand' returned exit code $lastExitCode, but produced a fresh jar '$($freshBuiltJar.Name)'. Continuing."
                }
                $buildSucceeded = $true
                break
            }

            Write-Warning "Maven command '$mavenCommand' failed with exit code $lastExitCode."
        }

        if (-not $buildSucceeded) {
            $buildSucceeded = Try-DownloadReleaseAsset -Config $config -NormalizedVersion $normalizedVersion -ArtifactPath $artifactPath
        }

        if (-not $buildSucceeded) {
            throw "Maven build failed with exit code $lastExitCode."
        }

        $builtJar = Get-ChildItem -Path "target" -Filter "*.jar" |
            Where-Object { $_.Name -notlike "original-*" } |
            Sort-Object -Property LastWriteTime -Descending |
            Select-Object -First 1

        if ($null -eq $builtJar -and -not (Test-Path -Path $artifactPath)) {
            throw "No built jar was found in target/."
        }

        if ($null -ne $builtJar) {
            Copy-Item -Path $builtJar.FullName -Destination $artifactPath -Force
        }
    }
    "zip" {
        $stagingDir = Join-Path $OutputDir "staging"
        if (Test-Path -Path $stagingDir) {
            Remove-Item -Path $stagingDir -Recurse -Force
        }
        New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null

        foreach ($path in @($config.zipIncludes)) {
            if (-not (Test-Path -Path $path)) {
                throw "Zip include '$path' was not found."
            }

            $item = Get-Item -Path $path
            $destination = Join-Path $stagingDir $path
            $destinationParent = Split-Path -Path $destination -Parent
            if (-not [string]::IsNullOrWhiteSpace($destinationParent)) {
                New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
            }

            if ($item.PSIsContainer) {
                Copy-Item -Path $item.FullName -Destination $destination -Recurse -Force
            } else {
                Copy-Item -Path $item.FullName -Destination $destination -Force
            }
        }

        if (Test-Path -Path $artifactPath) {
            Remove-Item -Path $artifactPath -Force
        }

        $zipSource = Join-Path $stagingDir "*"
        Compress-Archive -Path $zipSource -DestinationPath $artifactPath -CompressionLevel Optimal
    }
    default {
        throw "Unsupported packaging '$($config.packaging)' in $ConfigPath."
    }
}

if (-not (Test-Path -Path $artifactPath)) {
    throw "Artifact was not created: '$artifactPath'."
}

$artifactItem = Get-Item -Path $artifactPath
if ($artifactItem.Length -le 0) {
    throw "Artifact is empty: '$artifactPath'."
}

$resolvedArtifactPath = (Resolve-Path -Path $artifactPath).Path
Write-Host "Built artifact: $resolvedArtifactPath"

if (-not [string]::IsNullOrWhiteSpace($ArtifactPathOutputFile)) {
    Set-Content -Path $ArtifactPathOutputFile -Value $resolvedArtifactPath
}

if ($env:GITHUB_OUTPUT) {
    "artifact_path=$resolvedArtifactPath" | Out-File -FilePath $env:GITHUB_OUTPUT -Append -Encoding utf8
}

Write-Output $resolvedArtifactPath
