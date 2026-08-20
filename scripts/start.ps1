param([switch]$LocalJar)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskDockerCommand=Get-Command docker -ErrorAction SilentlyContinue
if($taskDockerCommand) {$taskDocker=$taskDockerCommand.Source}
else {
    $taskCandidates=@(
        (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'),
        (Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe')
    )
    $taskDocker=$taskCandidates | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
}
if(-not $taskDocker) {throw '未找到 Docker Desktop，请先安装并启动。'}
if(-not (Test-Path -LiteralPath (Join-Path $taskRoot '.env'))) {throw '请先从 .env.example 创建 .env 并填写配置。'}
$taskArgs=@('compose','--project-directory',$taskRoot,'-f',(Join-Path $taskRoot 'compose.yaml'))
if($LocalJar) {
    if(-not (Test-Path -LiteralPath (Join-Path $taskRoot 'target/interview-lab-1.0.0.jar'))) {throw '请先执行 mvn verify 生成 JAR。'}
    $taskArgs+=@('-f',(Join-Path $taskRoot 'compose.local.yaml'))
}
& $taskDocker @taskArgs up --build -d
if($LASTEXITCODE -ne 0) {throw '容器启动失败，请检查 Docker Desktop 状态和网络。'}
Write-Output '平台地址：http://localhost:8080'
