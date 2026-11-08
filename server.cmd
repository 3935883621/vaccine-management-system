@echo off
rem Vaccine management server command wrapper (bypasses PowerShell execution policy)
rem Usage: server.cmd start|stop|restart [backend|frontend|all]  or  status / config
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server.ps1" %*
