import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

group = "dev.namn.steady-fetch"
val defaultVersion = "0.1.0-SNAPSHOT"
version = System.getenv("RELEASE_VERSION") ?: defaultVersion

val publishingArtifactId = "steady-fetch"
val publishingName = "SteadyFetch"
val publishingDescription = "Resumable multi-connection downloader SDK for Android."
val publishingUrl = "https://github.com/void-memories/SteadyFetch"
val publishingScmConnection = "scm:git:git://github.com/void-memories/SteadyFetch.git"
val publishingScmDevConnection = "scm:git:ssh://git@github.com/void-memories/SteadyFetch.git"

android {
    namespace = "dev.namn.steady_fetch"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = group.toString()
                artifactId = publishingArtifactId
                version = version.toString()

                pom {
                    name.set(publishingName)
                    description.set(publishingDescription)
                    url.set(publishingUrl)
                    scm {
                        url.set(publishingUrl)
                        connection.set(publishingScmConnection)
                        developerConnection.set(publishingScmDevConnection)
                    }
                }
            }
        }
    }
}
