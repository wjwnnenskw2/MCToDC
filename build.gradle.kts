import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.RunConfigurationContainer

plugins {
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("eclipse")
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "rfg.examplemod"
version = "1.0.0"

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

repositories {
    maven { url = uri("https://gregtech.overminddl1.com/") }
    maven { url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
    mavenCentral()
}

dependencies {
    val shadow by configurations

    // 🔒 同時引入舊版 (5.1.x) 與現代化 (8.x) 驅動，達成動態向下相容
    shadow("mysql:mysql-connector-java:5.1.49")
    implementation("mysql:mysql-connector-java:5.1.49")
    
    shadow("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")

    shadow("com.google.code.gson:gson:2.8.9")
    implementation("com.google.code.gson:gson:2.8.9")

    shadow("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
}

// ... (中間省略 publishing, eclipse, idea 等設定，維持原樣不變) ...

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet = "UTF-8"
}

// ==
// ★ ShadowJar 配置與混淆映射 ★
// ==
tasks.jar {
    enabled = true
}

tasks.shadowJar {
    configurations = listOf(project.configurations.getByName("shadow"))
    
    // 🗑️ 移除了 JDA、okhttp3、okio 的 relocate，大幅加快打包速度
    relocate("com.moandjiezana.toml", "${project.group}.shadow.toml")

    exclude("META-INF/maven/**")
    exclude("META-INF/org/**")
    
    archiveClassifier.set("dev") 
    
    manifest {
        attributes(mapOf("FMLCorePluginContainsFMLMod" to "true"))
    }
}

tasks.jar {
    finalizedBy(tasks.shadowJar)
}

tasks.named("reobfJar") {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.named("reobfJar"))
}