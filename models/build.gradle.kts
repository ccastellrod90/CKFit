import java.security.MessageDigest
import java.util.UUID

// Módulo que empaqueta el modelo de Vosk como assets, igual que la demo oficial
// (https://github.com/alphacep/vosk-android-demo). El modelo (vosk-model-small-es-0.42) no se
// versiona: ver README / sección 8 de CLAUDE.md para descargarlo en src/main/assets/model-es/.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.vosk.models"
    compileSdk = 37
    enableKotlin = false

    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        buildConfig = false
    }
}

/**
 * StorageService.unpack compara este uuid con el de la copia ya descomprimida en el teléfono
 * para decidir si vuelve a copiar el modelo. Se deriva de los archivos del modelo (ruta + tamaño),
 * así solo cambia cuando cambia el modelo (la demo genera uno aleatorio en cada build).
 */
abstract class GenUuidTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modelDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val model = modelDir.orNull?.asFile
        val fingerprint = model?.walkTopDown()?.filter { it.isFile }
            ?.map { it.relativeTo(model).invariantSeparatorsPath + ":" + it.length() }
            ?.sorted()?.joinToString("\n")
            .orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray())
        val dir = outputDir.get().dir("model-es").asFile
        dir.mkdirs()
        dir.resolve("uuid").writeText(UUID.nameUUIDFromBytes(digest).toString())
    }
}

val genUuid = tasks.register<GenUuidTask>("genUUID") {
    val model = layout.projectDirectory.dir("src/main/assets/model-es")
    if (model.asFile.exists()) modelDir.set(model)
    outputDir.set(layout.buildDirectory.dir("generated/assets/uuid"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(genUuid, GenUuidTask::outputDir)
    }
}
