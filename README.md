# CKFit

App Android offline para planificar rutinas de gimnasio, entrenar manos libres (voz + audio) y ver la progresión.
La especificación completa está en [CLAUDE.md](CLAUDE.md).

## Requisitos

- Android Studio (su JDK integrado, `jbr`) y el SDK de Android; Gradle descarga lo que falte.

## Modelo de voz (no está en el repositorio)

Los comandos de voz usan Vosk con el modelo `vosk-model-small-es-0.42` (~39 MB, Apache 2.0,
https://alphacephei.com/vosk/models). Hay que descargarlo una vez en `models/src/main/assets/model-es/`
(la carpeta está en `.gitignore`). Desde la raíz del proyecto, en PowerShell:

```powershell
Invoke-WebRequest https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip -OutFile $env:TEMP\vosk-es.zip
Expand-Archive $env:TEMP\vosk-es.zip -DestinationPath $env:TEMP\vosk-es -Force
New-Item -ItemType Directory -Force models\src\main\assets\model-es | Out-Null
Copy-Item $env:TEMP\vosk-es\vosk-model-small-es-0.42\* models\src\main\assets\model-es -Recurse -Force
```

Debe quedar `models/src/main/assets/model-es/{am, conf, graph, ivector, README}`. El `uuid` que usa
`StorageService` se genera al compilar a partir de los archivos del modelo.

## Compilar, probar e instalar

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```
