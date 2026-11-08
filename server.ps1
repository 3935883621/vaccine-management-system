<#
.SYNOPSIS
    疫苗管理系统前后端服务指令脚本

.DESCRIPTION
    统一管理后端（Spring Boot）与前端（Vite）服务的启动、停止、重启与状态查看。
    端口等服务器设置统一在根目录 server.config.json 中调整，改配置后重启对应服务即生效。

.EXAMPLE
    .\server.ps1 start all        启动前后端
    .\server.ps1 start backend    只启动后端
    .\server.ps1 stop frontend    停止前端
    .\server.ps1 restart all      重启前后端
    .\server.ps1 status           查看运行状态
    .\server.ps1 config           查看服务器配置
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'config')]
    [string]$Command = 'status',

    [Parameter(Position = 1)]
    [ValidateSet('backend', 'frontend', 'all')]
    [string]$Target = 'all'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logsDir = Join-Path $root '.logs'

# ---------- 读取服务器配置 ----------
$configPath = Join-Path $root 'server.config.json'
if (-not (Test-Path $configPath)) {
    Write-Host "配置文件不存在：$configPath" -ForegroundColor Red
    exit 1
}
$cfg = Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json
$backendPort = [int]$cfg.backend.port
$frontendPort = [int]$cfg.frontend.port
$mysqlPort = [int]$cfg.database.port

# ---------- 工具函数 ----------
function Save-PidFile([string]$name, [int]$port) {
    # 记录当前端口监听的 PID，供 stop 在端口变更后仍能定位进程
    $pids = @(Get-PortPids $port)
    if ($pids.Count -gt 0) {
        Set-Content -Path (Join-Path $logsDir "$name.pid") -Value $pids
    }
}

function Get-Listener([int]$port) {
    return Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
}

function Get-PortPids([int]$port) {
    $conns = Get-Listener $port
    if (-not $conns) { return @() }
    return @($conns | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Stop-Service([int]$port, [string]$expectProcess, [string]$name) {
    $stoppedAny = $false
    # 1) 优先按启动时记录的 PID 停止（端口变更后仍能定位到旧进程）
    $pidFile = Join-Path $logsDir "$name.pid"
    if (Test-Path $pidFile) {
        foreach ($savedPid in @(Get-Content $pidFile -ErrorAction SilentlyContinue)) {
            $proc = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -eq $expectProcess) {
                Stop-Process -Id $savedPid -Force
                Write-Host "[$name] 已停止（PID $savedPid，来自启动记录）"
                $stoppedAny = $true
            }
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    # 2) 再按当前配置端口清理监听进程（兜底）
    foreach ($procId in @(Get-PortPids $port)) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        if ($expectProcess -and $proc.ProcessName -ne $expectProcess) {
            Write-Host "[$name] 端口 $port 被其他程序占用（$($proc.ProcessName) PID=$procId），跳过停止，请手动处理" -ForegroundColor Yellow
            continue
        }
        Stop-Process -Id $procId -Force
        Write-Host "[$name] 已停止（PID $procId）"
        $stoppedAny = $true
    }
    if (-not $stoppedAny) {
        Write-Host "[$name] 未运行（端口 $port 无监听）" -ForegroundColor DarkGray
    }
}

function Wait-Port([int]$port, [string]$name, [int]$timeoutSec = 90) {
    for ($i = 0; $i -lt $timeoutSec; $i++) {
        Start-Sleep -Seconds 1
        if (Get-Listener $port) {
            $pids = Get-PortPids $port
            Write-Host "[$name] 启动成功：http://localhost:$port (PID $($pids -join ','))" -ForegroundColor Green
            return $true
        }
    }
    Write-Host "[$name] 启动超时（$timeoutSec 秒内端口 $port 未监听），请查看日志 $logsDir\$name.log" -ForegroundColor Red
    return $false
}

function Start-Backend {
    if (Get-Listener $backendPort) {
        Write-Host "[backend] 已在运行（端口 $backendPort 有监听），如需重启请先执行 restart" -ForegroundColor DarkGray
        Save-PidFile 'backend' $backendPort
        return
    }
    # 端口与数据库连接参数经环境变量注入 Spring Boot（application.yml 占位符）
    $env:JAVA_HOME = $cfg.backend.javaHome
    $env:SERVER_PORT = [string]$backendPort
    $env:MYSQL_HOST = $cfg.database.host
    $env:MYSQL_PORT = [string]$mysqlPort
    $env:MYSQL_DB = $cfg.database.name
    if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
    $log = Join-Path $logsDir 'backend.log'
    Write-Host "[backend] 正在启动（端口 $backendPort，日志：$log）..."
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = '/c mvn spring-boot:run > "' + $log + '" 2>&1'
    $psi.WorkingDirectory = Join-Path $root 'backend'
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    [System.Diagnostics.Process]::Start($psi) | Out-Null
    Wait-Port $backendPort 'backend' | Out-Null
    Save-PidFile 'backend' $backendPort
}

function Start-Frontend {
    if (Get-Listener $frontendPort) {
        Write-Host "[frontend] 已在运行（端口 $frontendPort 有监听），如需重启请先执行 restart" -ForegroundColor DarkGray
        Save-PidFile 'frontend' $frontendPort
        return
    }
    $nodeHome = $cfg.frontend.nodeHome
    $npm = Join-Path $nodeHome 'npm.cmd'
    if (-not (Test-Path $npm)) {
        Write-Host "[frontend] 未找到 npm.cmd：$npm，请在 server.config.json 中修正 nodeHome" -ForegroundColor Red
        return
    }
    $env:Path = "$nodeHome;$env:Path"
    if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }
    $log = Join-Path $logsDir 'frontend.log'
    Write-Host "[frontend] 正在启动（端口 $frontendPort，日志：$log）..."
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    # npm 通过上面注入的 PATH 定位；日志路径无空格不加引号，避开 cmd 嵌套引号解析问题
    $psi.Arguments = '/c npm run dev > ' + $log + ' 2>&1'
    $psi.WorkingDirectory = Join-Path $root 'frontend'
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    [System.Diagnostics.Process]::Start($psi) | Out-Null
    Wait-Port $frontendPort 'frontend' | Out-Null
    Save-PidFile 'frontend' $frontendPort
}

function Restart-Service([string]$name) {
    if ($name -in @('backend', 'all')) {
        Write-Host '--- 重启 backend ---'
        Stop-Service $backendPort 'java' 'backend'
        Start-Sleep -Seconds 2
        Start-Backend
    }
    if ($name -in @('frontend', 'all')) {
        Write-Host '--- 重启 frontend ---'
        Stop-Service $frontendPort 'node' 'frontend'
        Start-Sleep -Seconds 2
        Start-Frontend
    }
}

function Show-Status {
    Write-Host ''
    Write-Host '================ 服务状态 ================'
    foreach ($item in @(@('backend', $backendPort), @('frontend', $frontendPort))) {
        $name = $item[0]
        $port = $item[1]
        $pids = Get-PortPids $port
        if ($pids.Count -gt 0) {
            $procName = (Get-Process -Id $pids[0] -ErrorAction SilentlyContinue).ProcessName
            Write-Host ("[{0,-8}] 运行中  http://localhost:{1}  (PID {2}, {3})" -f $name, $port, ($pids -join ','), $procName) -ForegroundColor Green
        }
        else {
            Write-Host ("[{0,-8}] 已停止  (端口 {1} 无监听)" -f $name, $port) -ForegroundColor DarkGray
        }
    }
    $mysqlPids = Get-PortPids $mysqlPort
    if ($mysqlPids.Count -gt 0) {
        Write-Host ("[{0,-8}] 监听中  端口 {1} (PID {2})" -f 'mysql', $mysqlPort, ($mysqlPids -join ',')) -ForegroundColor Green
    }
    else {
        Write-Host ("[{0,-8}] 未监听  端口 {1}（后端启动需要 MySQL 服务）" -f 'mysql', $mysqlPort) -ForegroundColor Yellow
    }
    Write-Host '========================================='
}

function Show-Config {
    Write-Host ''
    Write-Host '================ 服务器配置 ================'
    Write-Host ('后端端口  : {0}' -f $backendPort)
    Write-Host ('前端端口  : {0}' -f $frontendPort)
    Write-Host ('JDK 路径  : {0}' -f $cfg.backend.javaHome)
    Write-Host ('Node 路径 : {0}' -f $cfg.frontend.nodeHome)
    Write-Host ('数据库    : {0}:{1}/{2}' -f $cfg.database.host, $mysqlPort, $cfg.database.name)
    Write-Host ('配置文件  : {0}' -f $configPath)
    Write-Host '========================================='
    Write-Host '修改端口后执行 server.ps1 restart <服务名> 即生效'
}

# ---------- 指令分发 ----------
switch ($Command) {
    'start' {
        if ($Target -in @('backend', 'all')) { Start-Backend }
        if ($Target -in @('frontend', 'all')) { Start-Frontend }
    }
    'stop' {
        if ($Target -in @('backend', 'all')) { Stop-Service $backendPort 'java' 'backend' }
        if ($Target -in @('frontend', 'all')) { Stop-Service $frontendPort 'node' 'frontend' }
    }
    'restart' { Restart-Service $Target }
    'status' { Show-Status }
    'config' { Show-Config }
}
