plugins {
    `java-gradle-plugin`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation("org.paramixel:core:3.0.1-POST")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly(gradleTestKit())
}

gradlePlugin {
plugins {
        create("paramixel") {
            id = "org.paramixel"
            implementationClass = "org.paramixel.gradle.ParamixelPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as org.gradle.external.javadoc.CoreJavadocOptions).addBooleanOption("Xdoclint:all", true)
    (options as org.gradle.external.javadoc.CoreJavadocOptions).addBooleanOption("Werror", true)
}

publishing {
    publications {
        create<MavenPublication>("mavenPublication") {
            from(components["java"])
            artifactId = "gradle-plugin"
            pom {
                name.set("Paramixel Gradle Plugin")
                description.set("Paramixel Gradle Plugin")
                url.set("https://github.com/paramixel/paramixel")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("dhoard")
                        name.set("Douglas Hoard")
                        email.set("doug.hoard@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/paramixel/paramixel.git")
                    developerConnection.set("scm:git:ssh://git@github.com/paramixel/paramixel.git")
                    url.set("https://github.com/paramixel/paramixel")
                }
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
