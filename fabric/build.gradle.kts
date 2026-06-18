plugins {
    id("multiloader-loader")
    alias(libs.plugins.neoforged.moddev)
}

val modId = project.property("mod_id").toString()
val mainSourceSet = sourceSets.main.get()
val fabricClasspathGroup = files(
    mainSourceSet.output.classesDirs,
    mainSourceSet.output.resourcesDir
)

dependencies {
    implementation(libs.fabric.loader)
    implementation(libs.fabric.resource.loader.v1)
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    compileOnly(libs.asm)
    compileOnly(libs.asm.tree)
    compileOnly(libs.asm.commons)
    compileOnly(libs.sodium.fabric)
    compileOnly(libs.jsr305)
    runtimeOnly(libs.fabric.sponge.mixin)
    runtimeOnly(libs.fabric.log4j.util)
    runtimeOnly(libs.asm.commons)
}

neoForge {
    neoFormVersion = project.property("neo_form_version").toString()
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    runs {
        register("client") {
            client()
            ideName = "Fabric Vanilla Client (${project.path})"
            gameDirectory = file("runs/client").also { it.mkdirs() }
            mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient"
            systemProperty("fabric.development", "true")
            systemProperty("fabric.runtimeMappingNamespace", "official")
            systemProperty("fabric.defaultModDistributionNamespace", "official")
            systemProperty("fabric.classPathGroups", fabricClasspathGroup.asPath)
            loggingConfigFile.set(layout.projectDirectory.file("src/main/resources/log4j2-fabric.xml"))
        }
    }
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
            }
        }
    }
}

/*
tasks.register<Copy>("extractRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into("$projectDir/build/runtimeClasspath")
    doFirst {
        file("$projectDir/build/runtimeClasspath").mkdirs()
    }
}
*/
