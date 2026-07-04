@echo off
rem Starts the Python inference microservice on port 8000.
set U2NET_HOME=E:\AIModels\u2net
set HF_HOME=E:\AIModels\huggingface
set TORCH_HOME=E:\AIModels\torch
set PIP_CACHE_DIR=E:\pip-cache

cd /d E:\Projects\BGRemover\inference
.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
