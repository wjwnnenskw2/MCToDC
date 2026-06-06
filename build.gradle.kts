import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.RunConfigurationContainer

plugins {
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("eclipse")
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.0"
    // 引入 Shadow 打包插件
    id("com.gradleup.shadow") version "8.3.5"
}

// Project properties
group = "rfg.examplemod"
version = "1.0.0"

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.AZUL)
    }
    withSourcesJar()
    withJavadocJar()
}

minecraft {
    mcVersion.set("1.7.10")
    username.set("Developer")
    injectedTags.put("VERSION", project.version)
    extraRunJvmArguments.add("-ea:${project.group}")
    groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}

tasks.injectTags.configure {
    outputClassName.set("${project.group}.Tags")
}

tasks.processResources.configure {
    val projVersion = project.version.toString()
    inputs.property("version", projVersion)
    filesMatching("mcmod.info") {
        expand(mapOf("modVersion" to projVersion))
    }
}

val runtimeOnlyNonPublishable: Configuration by configurations.creating {
    description = "Runtime only dependencies that are not published alongside the jar"
    isCanBeConsumed = false
    isCanBeResolved = false
}
listOf(configurations.runtimeClasspath, configurations.testRuntimeClasspath).forEach {
    it.configure {
        extendsFrom(runtimeOnlyNonPublishable)
    }
}

repositories {
    maven {
        name = "OvermindDL1 Maven"
        url = uri("https://gregtech.overminddl1.com/")
    }
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "DV8TION Maven"
        url = uri("https://m2.dv8tion.net/releases")
    }
    maven {
        name = "JCenter"
        url = uri("https://jcenter.bintray.com/")
    }
    mavenCentral()
}

dependencies {
    // 🔏 【核心重構】：建立 shadow 關鍵字，強制讓 ShadowPlugin 將它們塞進最終的 Jar 包中
    val shadow by configurations

    shadow("mysql:mysql-connector-java:5.1.49")
    implementation("mysql:mysql-connector-java:5.1.49")

    // 1. JDA (排除 1.7.10 跑不動的語音模組)
    shadow("net.dv8tion:JDA:4.4.0_352") {
        exclude(module = "opus-java")
    }
    implementation("net.dv8tion:JDA:4.4.0_352") {
        exclude(module = "opus-java")
    }

    // 2. Gson
    shadow("com.google.code.gson:gson:2.8.9")
    implementation("com.google.code.gson:gson:2.8.9")

    // 3. Toml4j
    shadow("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://nexus.gtnewhorizons.com/repository/releases/")
            credentials {
                username = System.getenv("MAVEN_USER") ?: "NONE"
                password = System.getenv("MAVEN_PASSWORD") ?: "NONE"
            }
        }
    }
}

eclipse {
    classpath {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
        inheritOutputDirs = true
    }
    project {
        this.withGroovyBuilder {
            "settings" {
                "runConfigurations" {
                    val self = this.delegate as RunConfigurationContainer
                    self.add(Gradle("1. Run Client").apply { setProperty("taskNames", listOf("runClient")) })
                    self.add(Gradle("2. Run Server").apply { setProperty("taskNames", listOf("runServer")) })
                    self.add(Gradle("3. Run Obfuscated Client").apply { setProperty("taskNames", listOf("runObfClient")) })
                    self.add(Gradle("4. Run Obfuscated Server").apply { setProperty("taskNames", listOf("runObfServer")) })
                }
                "compiler" {
                    val self = this.delegate as org.jetbrains.gradle.ext.IdeaCompilerConfiguration
                    afterEvaluate {
                        self.javac.moduleJavacAdditionalOptions = mapOf(
                            (project.name + ".main") to
                                tasks.compileJava.get().options.compilerArgs.map { '"' + it + '"' }.joinToString(" ")
                        )
                    }
                }
            }
        }
    }
}

tasks.processIdeaSettings.configure {
    dependsOn(tasks.injectTags)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet = "UTF-8"
}

// ==========================================================
// ★ ShadowJar 配置與混淆映射連動區塊（最終宿命對齊版） ★
// ==========================================================

// 1. 讓原生 jar 任務保持開啟（確保能生成基礎結構），但我們隨後用 shadowJar 去蓋掉它的產出
tasks.jar {
    enabled = true
}

// 2. 讓 shadowJar 接管輸出，並把自己精準命名為 RFG 混淆器非吃不可的「-dev.jar」完全體！
tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    
    // 執行字串與類別重定向，完美防撞車
    relocate("net.dv8tion.jda", "${project.group}.shadow.jda")
    relocate("okhttp3", "${project.group}.shadow.okhttp3")
    relocate("okio", "${project.group}.shadow.okio")
    relocate("com.moandjiezana.toml", "${project.group}.shadow.toml")

    exclude("META-INF/maven/**")
    exclude("META-INF/org/**")
    
    // 🎯【絕殺對齊】：強迫 shadowJar 的後綴詞變成 "dev"，產出檔名就會是 RFG 渴望的 RFGExampleMod-1.0.0-dev.jar
    archiveClassifier.set("dev") 
    
    manifest {
        attributes(mapOf("FMLCorePluginContainsFMLMod" to "true"))
    }
}

// 3. 讓常規 jar 執行完後，立刻發動 shadowJar，用塞滿 JDA 的大胖包硬生生「覆蓋、頂替」掉那個 33KB 的小空殼！
tasks.jar {
    finalizedBy(tasks.shadowJar)
}

// 4. 讓 RFG 混淆重映射（reobfJar）順理成章地去啃這顆已經被我們偷梁換柱的 dev 完全體胖包
tasks.named("reobfJar") {
    dependsOn(tasks.shadowJar)
}

// 5. 綁定整個生命週期
tasks.build {
    dependsOn(tasks.named("reobfJar"))
}