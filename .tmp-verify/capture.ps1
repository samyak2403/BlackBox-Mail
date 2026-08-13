$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.samyak.blackboxmailt'
$outDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\fastlane\metadata\android\en-US\images\phoneScreenshots'))
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

function Dump {
    & $adb shell rm -f /sdcard/ui.xml 2>$null | Out-Null
    & $adb shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
    return (& $adb shell cat /sdcard/ui.xml) -join ''
}

function Texts([string]$xml) {
    $t = [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value }
    $d = [regex]::Matches($xml, 'content-desc="([^"]*)"') | ForEach-Object { $_.Groups[1].Value }
    return @($t) + @($d) | Where-Object { $_ -ne '' }
}

function FindCenter([string]$xml, [string]$needle) {
    foreach ($m in [regex]::Matches($xml, '<node[^>]*>')) {
        $node = $m.Value
        if ($node -match [regex]::Escape($needle)) {
            if ($node -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
                $x = ([int]$Matches[1] + [int]$Matches[3]) / 2
                $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
                return @([int]$x, [int]$y)
            }
        }
    }
    return $null
}

function Shot([string]$name) {
    & $adb shell screencap -p /sdcard/shot.png | Out-Null
    $dest = Join-Path $outDir $name
    & $adb pull /sdcard/shot.png $dest 2>$null | Out-Null
    $img = [System.Drawing.Image]::FromFile($dest)
    Write-Output ("  saved {0}  {1}x{2}" -f $name, $img.Width, $img.Height)
    $img.Dispose()
}

function Assert([string]$xml, [string]$needle, [string]$label) {
    if ((Texts $xml) -contains $needle) {
        Write-Output "  verified on screen: '$needle' -> $label"
        return $true
    }
    Write-Output "  MISSING '$needle' for $label. On screen: $((Texts $xml) -join ' | ')"
    return $false
}

Add-Type -AssemblyName System.Drawing

& $adb shell am force-stop $pkg | Out-Null
& $adb shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Output 'Waiting for mailbox provisioning...'
Start-Sleep -Seconds 12

# ---------------------------------------------------------------- 1. inbox
Write-Output '[1] Inbox'
$xml = Dump
if (-not (Assert $xml 'Your address' 'inbox')) { exit 1 }
Shot '1.png'

# ----------------------------------------------------------------- 2. drawer
Write-Output '[2] Navigation drawer'
$menu = FindCenter $xml 'Open navigation drawer'
if (-not $menu) { Write-Output '  could not locate drawer button'; exit 1 }
& $adb shell input tap $menu[0] $menu[1] | Out-Null
Start-Sleep -Seconds 3
$xml = Dump
if (-not (Assert $xml 'Mark all as read' 'drawer')) { exit 1 }
Shot '3.png'

# --------------------------------------------------------------- 3. settings
Write-Output '[3] Settings'
$settings = FindCenter $xml 'Settings'
if (-not $settings) { Write-Output '  could not locate Settings item'; exit 1 }
& $adb shell input tap $settings[0] $settings[1] | Out-Null
Start-Sleep -Seconds 3
$xml = Dump
if (-not (Assert $xml 'Auto refresh' 'settings')) { exit 1 }
Shot '4.png'

# ---------------------------------------------------- 4. account bottom sheet
Write-Output '[4] Account sheet'
& $adb shell input keyevent KEYCODE_BACK | Out-Null
Start-Sleep -Seconds 2
$xml = Dump
$fab = FindCenter $xml 'Create a new address'
if (-not $fab) { $fab = FindCenter $xml 'New address' }
if (-not $fab) { Write-Output '  could not locate the FAB'; exit 1 }
& $adb shell input tap $fab[0] $fab[1] | Out-Null
Start-Sleep -Seconds 3
$xml = Dump
if (-not (Assert $xml 'Copy address' 'account sheet')) { exit 1 }
Shot '2.png'

Write-Output 'DONE'
