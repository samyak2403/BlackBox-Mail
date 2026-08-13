$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$size = 512
$out = Join-Path $PSScriptRoot '..\fastlane\metadata\android\en-US\images\icon.png'
$outDir = Split-Path $out -Parent
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Background: same diagonal ramp as the adaptive icon layer.
$rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(40, 24)),
    (New-Object System.Drawing.Point(480, 500)),
    [System.Drawing.Color]::FromArgb(255, 43, 114, 224),
    [System.Drawing.Color]::FromArgb(255, 7, 27, 51))
$blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
$blend.Colors = @(
    [System.Drawing.Color]::FromArgb(255, 43, 114, 224),
    [System.Drawing.Color]::FromArgb(255, 11, 87, 208),
    [System.Drawing.Color]::FromArgb(255, 7, 27, 51))
$blend.Positions = @(0.0, 0.45, 1.0)
$brush.InterpolationColors = $blend
$g.FillRectangle($brush, $rect)

# Envelope geometry, matching the 24dp glyph proportions (24 x 16 body).
$bw = 268.0
$bh = 190.0
$bx = ($size - $bw) / 2.0
$by = ($size - $bh) / 2.0
$r = 26.0

$body = New-Object System.Drawing.Drawing2D.GraphicsPath
$body.AddArc($bx, $by, $r, $r, 180, 90)
$body.AddArc($bx + $bw - $r, $by, $r, $r, 270, 90)
$body.AddArc($bx + $bw - $r, $by + $bh - $r, $r, $r, 0, 90)
$body.AddArc($bx, $by + $bh - $r, $r, $r, 90, 90)
$body.CloseFigure()

$white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$g.FillPath($white, $body)

# Flap: a chevron cut in the background colour so it reads as an open envelope.
$inset = 26.0
$flap = New-Object System.Drawing.Drawing2D.GraphicsPath
$flap.AddLines(@(
    (New-Object System.Drawing.PointF([single]($bx + $inset), [single]($by + $inset * 0.72))),
    (New-Object System.Drawing.PointF([single]($bx + $bw / 2.0), [single]($by + $bh * 0.62))),
    (New-Object System.Drawing.PointF([single]($bx + $bw - $inset), [single]($by + $inset * 0.72)))
))
$pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 11, 87, 208), [single]30)
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$g.DrawPath($pen, $flap)

$g.Dispose()
$resolved = [System.IO.Path]::GetFullPath($out)
$bmp.Save($resolved, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

$info = Get-Item $resolved
Write-Output "WROTE: $($info.FullName)"
Write-Output "BYTES: $($info.Length)"
$check = [System.Drawing.Image]::FromFile($info.FullName)
Write-Output "DIMS: $($check.Width)x$($check.Height)  FORMAT: $($check.PixelFormat)"
$check.Dispose()
