@echo off
rem Starts the Spring Boot backend on port 8080 (serves the built frontend too).
set JAVA_HOME=E:\Java\jdk-21.0.11+10
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d E:\Projects\BGRemover\backend
E:\Maven\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
