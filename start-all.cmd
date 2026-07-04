@echo off
rem Opens both services in their own windows, then the app is at http://localhost:8080
start "BGRemover inference" cmd /k E:\Projects\BGRemover\start-inference.cmd
start "BGRemover backend" cmd /k E:\Projects\BGRemover\start-backend.cmd
echo Both services starting. Open http://localhost:8080 once they are up.
