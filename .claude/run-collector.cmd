@echo off
rem Sobe o coletor Spring Boot com o JDK 25 do workspace e importa o .env (RIOT_API_KEY etc.) só neste processo.
set "JAVA_HOME=D:\Workspace\Languages-Frameworks\jdk-25.0.4.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0.."
mvn -q spring-boot:run "-Dspring-boot.run.arguments=--spring.config.import=optional:file:.env[.properties] %*"
