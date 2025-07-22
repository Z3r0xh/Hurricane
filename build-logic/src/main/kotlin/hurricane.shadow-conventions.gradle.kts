plugins {
    id("hurricane.java-conventions")
    id("com.gradleup.shadow")
    id("xyz.wagyourtail.jvmdowngrader")
}

tasks {
    jar {
        archiveClassifier.set("unshaded")
        archiveVersion.set(project.version.toString())
    }

    shadowJar {
        archiveClassifier.set("shaded")
        archiveVersion.set(project.version.toString())
    }

    downgradeJar {
        mustRunAfter(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
    }

    build {
        dependsOn(shadowJar)
        dependsOn(downgradeJar)
    }
}

jvmdg {
    downgradeTo = JavaVersion.VERSION_1_8
}