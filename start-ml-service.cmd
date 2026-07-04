@echo off
rem Starts the Python inference microservice on port 8000.
set U2NET_HOME=E:\AIModels\u2net
set HF_HOME=E:\AIModels\huggingface
set TORCH_HOME=E:\AIModels\torch
set PIP_CACHE_DIR=E:\pip-cache
rem Change the default model here if desired (see GET /models for options)
if "%BGR_DEFAULT_MODEL%"=="" set BGR_DEFAULT_MODEL=isnet-general-use

cd /d E:\Projects\BGRemover\ml-service
.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
