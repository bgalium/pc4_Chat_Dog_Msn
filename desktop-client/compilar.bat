@echo off
echo ================================================
echo  Compilando dog-messenger-desktop (Manual Build)
echo ================================================

if not exist target\classes mkdir target\classes
if not exist target\test-classes mkdir target\test-classes

echo Compilando codigo fuente...
javac -encoding UTF-8 -d target\classes src\main\java\uni\cc4p1\client\model\*.java src\main\java\uni\cc4p1\client\connection\*.java src\main\java\uni\cc4p1\client\gui\*.java src\main\java\uni\cc4p1\client\DesktopApp.java src\main\java\uni\cc4p1\client\DesktopConsoleApp.java
if %errorlevel% neq 0 (
    echo [ERROR] Fallo en la compilacion de fuentes.
    if "%1" neq "nopause" pause
    exit /b %errorlevel%
)

echo Compilando codigo de pruebas...
javac -encoding UTF-8 -cp target\classes -d target\test-classes src\test\java\uni\cc4p1\client\EchoServerStub.java
if %errorlevel% neq 0 (
    echo [ERROR] Fallo en la compilacion de pruebas.
    if "%1" neq "nopause" pause
    exit /b %errorlevel%
)

set JAR_BIN=jar
where jar >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\Program Files\Java\jdk-26\bin\jar.exe" (
        set JAR_BIN="C:\Program Files\Java\jdk-26\bin\jar.exe"
    ) else if exist "%JAVA_HOME%\bin\jar.exe" (
        set JAR_BIN="%JAVA_HOME%\bin\jar.exe"
    )
)

echo Creando JAR ejecutable en target/dog-messenger-desktop.jar con %JAR_BIN%...
%JAR_BIN% --create --file target\dog-messenger-desktop.jar --main-class uni.cc4p1.client.DesktopApp -C target\classes .
if %errorlevel% neq 0 (
    echo [ERROR] Fallo al empaquetar el JAR.
    if "%1" neq "nopause" pause
    exit /b %errorlevel%
)

echo.
echo Compilacion y empaquetado exitosos.
echo Para ejecutar el cliente (GUI):
echo   java -jar target\dog-messenger-desktop.jar
echo.
echo Para ejecutar el cliente (Consola):
echo   java -cp target\classes uni.cc4p1.client.DesktopConsoleApp
echo.
echo Para ejecutar el servidor de pruebas Echo:
echo   java -cp "target\test-classes;target\classes" uni.cc4p1.client.EchoServerStub
echo.
if "%1" neq "nopause" pause
