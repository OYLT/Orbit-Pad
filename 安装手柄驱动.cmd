@echo off
chcp 65001 >nul
setlocal
set "ORBIT_DRIVER=%~dp0drivers\ViGEmBus_1.22.0_x64_x86_arm64.exe"
if not exist "%ORBIT_DRIVER%" set "ORBIT_DRIVER=%~dp0native\bin\ViGEmBus_1.22.0_x64_x86_arm64.exe"
if not exist "%ORBIT_DRIVER%" (
  echo 未找到驱动安装包。源码目录请先运行 npm run setup:gamepad，便携包请完整解压。
  pause
  exit /b 1
)
echo 安装 Xbox 360 虚拟手柄驱动。请完成安装向导，然后重启 Orbit Pad 接收端。
start "" /wait "%ORBIT_DRIVER%"
