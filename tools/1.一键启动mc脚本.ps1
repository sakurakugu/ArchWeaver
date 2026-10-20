param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

# 必须带项目前缀：内容版模块也声明了 runs，裸 runClient 会同时启动两个客户端。
# 从内容版模块启动会把 core 一起带上，是日常开发想要的那个。
$task = ":artifice:neoforge:runClient"
$launchBoth = $false
$neoforgeRequested = $false
$fabricRequested = $false
$javaPath = $null
$releasePath = "D:\elric\Code\Repos\Minecraft\test-mc"
$releaseRequested = $false
$buildRequested = $false

for ($index = 0; $index -lt $Arguments.Count; $index++) {
    switch ($Arguments[$index]) {
        "--build" {
            $task = "build"
            $buildRequested = $true
        }
        "--release" {
            $task = "release"
            $releaseRequested = $true
        }
        "--neoforge" {
            $task = ":artifice:neoforge:runClient"
            $neoforgeRequested = $true
        }
        "--fabric" {
            $task = ":artifice:fabric:runClient"
            $fabricRequested = $true
        }
        "--both" { $launchBoth = $true }
        "--path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --path 缺少 Java 路径。"
            }

            $index++
            $javaPath = $Arguments[$index]
        }
        "--release-path" {
            if ($index + 1 -ge $Arguments.Count) {
                throw "参数 --release-path 缺少 PCL 测试端路径。"
            }

            $index++
            $releasePath = $Arguments[$index]
        }
        default {
            throw "不支持的参数 '$($Arguments[$index])'。请使用 --build、--release、--neoforge、--fabric、--both、--path 或 --release-path。"
        }
    }
}

if ($fabricRequested -and ($neoforgeRequested -or $launchBoth -or $releaseRequested -or $buildRequested)) {
    throw "参数 --fabric 用于启动单个 Fabric 开发客户端，不能与 --neoforge、--both、--release 或 --build 同用。"
}

if ($releaseRequested -and ($buildRequested -or $neoforgeRequested -or $launchBoth)) {
    throw "参数 --release 不能与 --build、--neoforge 或 --both 一起使用。"
}

if ($launchBoth -and -not $neoforgeRequested) {
    throw "参数 --both 必须与 --neoforge 一起使用。示例：--neoforge --both"
}

Set-Location "$PSScriptRoot\.."

function Set-DevelopmentServerProperties {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PropertiesPath
    )

    # 开发客户端使用离线身份，LAN 服务端不能强制校验 Mojang 会话或安全档案。
    $propertyLines = if (Test-Path -LiteralPath $PropertiesPath -PathType Leaf) {
        @(Get-Content -LiteralPath $PropertiesPath)
    } else {
        @()
    }

    $updatedLines = [System.Collections.Generic.List[string]]::new()
    $seenOnlineMode = $false
    $seenSecureProfile = $false
    $seenAllowCheats = $false
    foreach ($line in $propertyLines) {
        if ($line -match '^online-mode=') {
            $updatedLines.Add('online-mode=false')
            $seenOnlineMode = $true
        } elseif ($line -match '^enforce-secure-profile=') {
            $updatedLines.Add('enforce-secure-profile=false')
            $seenSecureProfile = $true
        } elseif ($line -match '^allow-cheats=') {
            $updatedLines.Add('allow-cheats=true')
            $seenAllowCheats = $true
        } else {
            $updatedLines.Add($line)
        }
    }

    if (-not $seenOnlineMode) {
        $updatedLines.Add('online-mode=false')
    }
    if (-not $seenSecureProfile) {
        $updatedLines.Add('enforce-secure-profile=false')
    }
    if (-not $seenAllowCheats) {
        $updatedLines.Add('allow-cheats=true')
    }

    Set-Content -LiteralPath $PropertiesPath -Value $updatedLines -Encoding UTF8
}

function Get-GradleProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PropertiesPath,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $line = Get-Content -LiteralPath $PropertiesPath | Where-Object { $_ -match "^$([regex]::Escape($Name))=(.*)$" } | Select-Object -First 1
    if ($null -eq $line) {
        throw "未在 '$PropertiesPath' 中找到 Gradle 属性 '$Name'。"
    }
    return ($line -replace "^$([regex]::Escape($Name))=", "")
}

function Find-MinecraftProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$InstancePath
    )

    $normalized = (Resolve-Path -LiteralPath $InstancePath).Path.TrimEnd('\')
    $process = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'javaw.exe') -and
        $null -ne $_.CommandLine -and
        $_.CommandLine.IndexOf($normalized, [StringComparison]::OrdinalIgnoreCase) -ge 0
    } | Select-Object -First 1
    if ($null -eq $process) { return $null }
    return [int]$process.ProcessId
}

function Start-BuiltClient {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PclRoot
    )

    $propertiesPath = Join-Path (Get-Location) 'gradle.properties'
    $minecraftVersion = Get-GradleProperty -PropertiesPath $propertiesPath -Name 'minecraft_version'
    $artifactVersion = Get-GradleProperty -PropertiesPath $propertiesPath -Name 'mod_version'
    $artifactDirectory = Join-Path (Get-Location) "build\v$artifactVersion"
    $coreArtifact = Get-ChildItem -LiteralPath $artifactDirectory -Filter "archweaver-v*-mc$minecraftVersion-neoforge.jar" -File | Select-Object -First 1
    $artificeArtifact = Get-ChildItem -LiteralPath $artifactDirectory -Filter "archweaver_artifice-v*-mc$minecraftVersion-neoforge.jar" -File | Select-Object -First 1
    if ($null -eq $coreArtifact -or $null -eq $artificeArtifact) {
        throw "未找到构建产物。预期目录：'$artifactDirectory'。"
    }

    $pclExecutable = Join-Path $PclRoot 'PCL2_Release.exe'
    $minecraftRoot = Join-Path $PclRoot '.minecraft'
    $versionsRoot = Join-Path $minecraftRoot 'versions'
    if (-not (Test-Path -LiteralPath $pclExecutable -PathType Leaf)) {
        throw "PCL 测试端路径无效：'$PclRoot'。未找到 PCL2_Release.exe。"
    }
    if (-not (Test-Path -LiteralPath $versionsRoot -PathType Container)) {
        throw "PCL 测试端缺少版本目录：'$versionsRoot'。"
    }

    $versionPattern = "^$([regex]::Escape($minecraftVersion))-NeoForge(?:[_ ].*)?$"
    $instances = @(Get-ChildItem -LiteralPath $versionsRoot -Directory | Where-Object { $_.Name -match $versionPattern })
    if ($instances.Count -eq 0) {
        throw "未安装 Minecraft $minecraftVersion NeoForge 测试实例；请先在 PCL 中安装对应版本。"
    }
    if ($instances.Count -gt 1) {
        throw "Minecraft $minecraftVersion NeoForge 匹配到多个测试实例：$($instances.Name -join '、')"
    }

    $instance = $instances[0]
    $modsDirectory = Join-Path $instance.FullName 'mods'
    New-Item -ItemType Directory -Path $modsDirectory -Force | Out-Null
    Get-ChildItem -LiteralPath $modsDirectory -File -Filter 'archweaver-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem -LiteralPath $modsDirectory -File -Filter 'archweaver_artifice-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item -LiteralPath $coreArtifact.FullName -Destination $modsDirectory -Force
    Copy-Item -LiteralPath $artificeArtifact.FullName -Destination $modsDirectory -Force
    Write-Host "已部署构建产物到 [$($instance.Name)]：$($coreArtifact.Name)、$($artificeArtifact.Name)"

    $optionsPath = Join-Path $instance.FullName 'options.txt'
    $optionLines = if (Test-Path -LiteralPath $optionsPath -PathType Leaf) { @(Get-Content -LiteralPath $optionsPath) } else { @() }
    $languageLine = $false
    $updatedOptions = foreach ($line in $optionLines) {
        if ($line -match '^lang:') {
            if (-not $languageLine) {
                $languageLine = $true
                'lang:zh_cn'
            }
        } else {
            $line
        }
    }
    if (-not $languageLine) { $updatedOptions += 'lang:zh_cn' }
    Set-Content -LiteralPath $optionsPath -Value $updatedOptions -Encoding UTF8

    Write-Host "启动生产测试客户端 [$($instance.Name)]；关闭游戏后脚本退出。"
    $launcher = Start-Process -FilePath $pclExecutable -ArgumentList @(
        'launch', '--instance', $instance.FullName, '--folder', $minecraftRoot
    ) -WorkingDirectory $PclRoot -PassThru -WindowStyle Hidden
    $minecraftProcessId = $null
    $deadline = (Get-Date).AddSeconds(180)
    try {
        while ((Get-Date) -lt $deadline) {
            $minecraftProcessId = Find-MinecraftProcessId -InstancePath $instance.FullName
            if ($null -ne $minecraftProcessId) { break }
            Start-Sleep -Milliseconds 500
        }
        if ($null -eq $minecraftProcessId) {
            throw "等待 Minecraft 进程启动超时。"
        }
        Write-Host "Minecraft 已启动。"
        Wait-Process -Id $minecraftProcessId
    } finally {
        if ($null -ne $launcher -and -not $launcher.HasExited) {
            Stop-Process -Id $launcher.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

if ($null -ne $javaPath) {
    $javaHome = [System.IO.Path]::GetFullPath($javaPath)
    if (-not (Test-Path -LiteralPath "$javaHome\bin\java.exe" -PathType Leaf)) {
        throw "指定的 Java 路径无效：'$javaPath'。未找到 bin\java.exe。"
    }
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

# 启用仓库内的 Git hooks。
git config core.hooksPath .githooks

# 设置环境变量。
Write-Host "Gradle 将通过 Toolchain 自动检测或下载所需 Java。"

if ($task -eq "build") {
    # 清理旧输出后执行测试和构建。
    .\gradlew.bat clean build
} elseif ($task -eq "release") {
    # 构建发布包，部署到 PCL 测试实例后启动。
    .\gradlew.bat clean build
    if ($LASTEXITCODE -eq 0) {
        Start-BuiltClient -PclRoot ([System.IO.Path]::GetFullPath($releasePath))
    }
} else {
    if ($launchBoth) {
        # 启动离线专用服务端，再启动两个自动连接的客户端。
        $rootPath = (Get-Location).Path
        Set-DevelopmentServerProperties -PropertiesPath (Join-Path $rootPath 'run\server.properties')
        $serverArguments = "/c `"$rootPath\gradlew.bat`" :artifice:neoforge:runServer"
        Start-Process -FilePath $env:ComSpec -ArgumentList $serverArguments -WorkingDirectory $rootPath
        # 首次启动需要解析 NeoForge 依赖，给服务端足够时间监听 25565。
        Start-Sleep -Seconds 20
        # 注意：runClientServer1/2 在任何 build 脚本里都不存在，是历史遗留的失效引用。
        $clientArguments = "/c `"$rootPath\gradlew.bat`" runClientServer1"
        $client2Arguments = "/c `"$rootPath\gradlew.bat`" runClientServer2"
        Start-Process -FilePath $env:ComSpec -ArgumentList $clientArguments -WorkingDirectory $rootPath
        Start-Process -FilePath $env:ComSpec -ArgumentList $client2Arguments -WorkingDirectory $rootPath
        Write-Host "已启动离线服务端和两个自动连接的 NeoForge 开发客户端。"
    } else {
        # 启动所选加载器的单个开发客户端。
        .\gradlew.bat $task
    }
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
