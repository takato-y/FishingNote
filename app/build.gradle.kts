plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.fishingnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fishingnote"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.7.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

/**
 * プライバシーポリシーの本文を、docs/プライバシーポリシー.md から作ってアプリに入れる（先頭の <!-- --> のヘッダーは除く）。
 * 写す作業をなくし、公開する原稿とアプリの中の版がずれないようにするため。
 */
abstract class GeneratePrivacyPolicy : DefaultTask() {
    @get:InputFile abstract val draft: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val text = draft.get().asFile.readText(Charsets.UTF_8)
        check(text.trimStart().startsWith("<!--") && text.contains("-->")) { "プライバシーポリシーの原稿の先頭にヘッダーがありません" }
        val body = text.substringAfter("-->").trim() + "\n"
        val raw = outputDir.get().dir("raw").asFile
        raw.mkdirs()
        raw.resolve("privacy_policy.md").writeText(body, Charsets.UTF_8)
    }
}

val generatePrivacyPolicy = tasks.register<GeneratePrivacyPolicy>("generatePrivacyPolicy") {
    draft.set(rootProject.layout.projectDirectory.file("docs/プライバシーポリシー.md"))
}

// 単体テスト（PrivacyPolicyTest）は原稿を直接読むので、原稿だけを直したときもテストをやり直させる
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("docs/プライバシーポリシー.md")).withPropertyName("privacyPolicyDraft")
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generatePrivacyPolicy, GeneratePrivacyPolicy::outputDir)
    }
}

/**
 * 配布用のビルドの前に、下書きのまま出していけない物が残っていないかを確かめる。
 * 残っている間は、配布用のビルド（assembleRelease・bundleRelease）を止める。
 */
val checkReleaseReady = tasks.register("checkReleaseReady") {
    val draft = rootProject.layout.projectDirectory.file("docs/プライバシーポリシー.md").asFile
    val appId = android.defaultConfig.applicationId.orEmpty()
    inputs.file(draft)
    doLast {
        val problems = buildList {
            Regex("【[^】]+】").findAll(draft.readText(Charsets.UTF_8)).map { it.value }.distinct().forEach {
                add("プライバシーポリシーに未記入の欄があります：$it")
            }
            if (appId.startsWith("com.example")) add("パッケージ名が仮のままです：$appId")
        }
        check(problems.isEmpty()) { "配布用のビルドを止めました。\n" + problems.joinToString("\n") }
    }
}
// 配布用を名指しで作るとき（assembleRelease・bundleRelease・packageRelease・installRelease など）だけ確かめる。
// いつもの build・assemble は中で assembleRelease も作るが、そこまで止めると下書きの間ずっと build が通らないため、対象にしない
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release") || it.substringAfterLast(':').startsWith("bundle") }
checkReleaseReady.configure { onlyIf { releaseRequested } }
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "installRelease") }.configureEach { dependsOn(checkReleaseReady) }

ksp {
    // DB の構造を書き出して、版を上げるときの差分を確かめられるようにする
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // パスワードの強さの推定（辞書・名前・日付・キーボードの並びなどから、推測に要る回数を見積もる。通信はしない）
    implementation(libs.zxcvbn)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
