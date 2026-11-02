# Independent Subscription Groups additions, 2026-11-01. GPL-3.0-only; see LICENSE/NOTICE.
# Runs the same core JUnit tests without configuring Android or the Morphe Gradle plugin.
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$target = Join-Path $repo 'build/group-core-tests'
$classes = Join-Path $target 'classes'
$deps = Join-Path $target 'deps'
New-Item -ItemType Directory -Force $classes, $deps | Out-Null
$artifacts = @(
    @{ Name = 'junit-4.13.2.jar'; Url = 'https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar'; Hash = '8E495B634469D64FB8ACFA3495A065CBACC8A0FFF55CE1E31007BE4C16DC57D3' },
    @{ Name = 'hamcrest-core-1.3.jar'; Url = 'https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar'; Hash = '66FDEF91E9739348DF7A096AA384A5685F4E875584CCE89386A7A47251C4D8E9' },
    @{ Name = 'json-20240303.jar'; Url = 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'; Hash = '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED' }
)
foreach ($artifact in $artifacts) {
    $path = Join-Path $deps $artifact.Name
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest $artifact.Url -OutFile $path }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $artifact.Hash) {
        throw "Dependency checksum mismatch: $($artifact.Name)"
    }
}
if ($JavaHome) {
    $javac = Join-Path $JavaHome 'bin/javac.exe'
    $java = Join-Path $JavaHome 'bin/java.exe'
} else {
    $javac = (Get-Command javac -ErrorAction Stop).Source
    $java = (Get-Command java -ErrorAction Stop).Source
}
$source = Join-Path $repo 'extensions/youtube/src'
$core = @('GroupBackup', 'GroupStore', 'GroupPreferences', 'GroupLibrary', 'GroupLibraryCodec',
    'GroupLibraryStore', 'GroupSelection', 'GroupVideo', 'FeedSource', 'YouTubeAtomFeed', 'RecentFeedSession', 'ChannelFeedCache', 'VideoMetadata', 'ChannelPageMetadata', 'GroupContentFilter', 'ContentFilterSelection', 'ThumbnailVariant', 'WatchedHistory', 'WatchCompletionTracker', 'VideoActionPlan')
$testNames = @('GroupBackupTest', 'GroupStoreTest', 'GroupLibraryTest', 'GroupFeedTest', 'GroupCoverageTest', 'GroupCacheTest', 'GroupMetadataTest', 'GroupsV6CoreTest')
$sources = @($core | ForEach-Object { Join-Path $source "main/java/app/morphe/extension/youtube/groups/$_.java" }) +
    @($testNames | ForEach-Object { Join-Path $source "test/java/app/morphe/extension/youtube/groups/$_.java" })
$classpath = ($artifacts | ForEach-Object { Join-Path $deps $_.Name }) -join [IO.Path]::PathSeparator
& $javac --release 17 -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
$runtime = @($classes, $classpath, (Join-Path $source 'test/resources')) -join [IO.Path]::PathSeparator
& $java -cp $runtime org.junit.runner.JUnitCore ($testNames | ForEach-Object { "app.morphe.extension.youtube.groups.$_" })
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
