import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.detekt)
    alias(libs.plugins.mavenPublish)
}

group = "lol.simeon"
// Release version comes from the publish tag (v1.2.3 -> 1.2.3); the Central Portal rejects SNAPSHOTs.
version = providers.gradleProperty("mycelium.version").orElse("1.0.2").get()

repositories {
    mavenCentral()
}

dependencies {
    // api: these types appear in ByteMessage's public signatures (ByteBuf, CompoundBinaryTag,
    // and adventure-api's Component via adventure-nbt), so consumers need them transitively.
    api(libs.netty.buffer)
    api(libs.adventure.nbt)
    // implementation: used only internally (gson bridge, plain serializer).
    implementation(libs.adventure.text)
    implementation(libs.adventure.gson)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

// Stable module name for consumers on the module path.
tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Automatic-Module-Name" to "lol.simeon.mycelium")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
}

// detekt 1.23.x bundles a Kotlin compiler that rejects jvm-target 25; lint against 21.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()
    coordinates("lol.simeon", "mycelium", version.toString())
    // Empty javadoc jar: Dokka's pipeline does not yet run on the JDK 25 toolchain.
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    pom {
        name.set("Mycelium")
        description.set(
            "Mycelium is a Kotlin library for reading and writing Minecraft protocol types over " +
                "Netty buffers, with version-aware serialization of VarInts, strings, NBT, and text " +
                "components across Minecraft versions from 1.8 onward.",
        )
        url.set("https://github.com/DerSimeon/Mycelium")
        licenses {
            license {
                name.set("BSD 3-Clause License")
                url.set("https://opensource.org/licenses/BSD-3-Clause")
            }
        }
        developers {
            developer {
                id.set("DerSimeon")
                name.set("Simeon L")
            }
        }
        scm {
            url.set("https://github.com/DerSimeon/Mycelium")
            connection.set("scm:git:https://github.com/DerSimeon/Mycelium.git")
            developerConnection.set("scm:git:ssh://git@github.com/DerSimeon/Mycelium.git")
        }
    }
}
