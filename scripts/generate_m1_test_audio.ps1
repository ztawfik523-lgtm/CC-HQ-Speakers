param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\build\m1-test-audio'),
    [double]$DurationSeconds = 8.0
)

$ErrorActionPreference = 'Stop'

if ([double]::IsNaN($DurationSeconds) -or
        [double]::IsInfinity($DurationSeconds) -or
        $DurationSeconds -le 1.0) {
    throw 'DurationSeconds must be a finite value greater than one second.'
}

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if ($null -eq $ffmpeg) {
    throw @'
FFmpeg was not found on PATH.
Install it on Windows with:
  winget install --id Gyan.FFmpeg -e
Open a new terminal, then run this script again.
'@
}

$output = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $output | Out-Null
$duration = $DurationSeconds.ToString([Globalization.CultureInfo]::InvariantCulture)

function New-TestTone {
    param(
        [string]$Name,
        [int]$Frequency,
        [int]$SampleRate,
        [string[]]$CodecArguments
    )

    $destination = Join-Path $output $Name
    $source = "sine=frequency=${Frequency}:sample_rate=${SampleRate}:duration=${duration}"
    $fadeOutStart = [Math]::Max(0.0, $DurationSeconds - 0.05).ToString(
        [Globalization.CultureInfo]::InvariantCulture)
    $filter = "afade=t=in:st=0:d=0.02,afade=t=out:st=${fadeOutStart}:d=0.05"
    $arguments = @(
        '-hide_banner', '-loglevel', 'error', '-y',
        '-f', 'lavfi', '-i', $source,
        '-af', $filter, '-ac', '1', '-ar', $SampleRate
    ) + $CodecArguments + @($destination)

    & $ffmpeg.Source @arguments
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg failed while creating $Name." }
    $item = Get-Item -LiteralPath $destination
    Write-Host ("Created {0} ({1} Hz, {2:N0} bytes)" -f $item.FullName, $SampleRate, $item.Length)
}

New-TestTone 'm1.mp3' 440 44100 @('-codec:a', 'libmp3lame', '-b:a', '128k')
New-TestTone 'm1.ogg' 660 48000 @('-codec:a', 'libvorbis', '-q:a', '5')
New-TestTone 'm1.wav' 880 32000 @('-codec:a', 'pcm_s16le')

Write-Host ''
Write-Host 'Copy these three files to the ComputerCraft computer:'
Get-ChildItem -LiteralPath $output -File | Sort-Object Name | Select-Object Name, Length
