plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(platform("io.projectreactor:reactor-bom:2024.0.2"))
    implementation("io.projectreactor.netty:reactor-netty-core")
    implementation("io.projectreactor.netty:reactor-netty-http")
    implementation(project(":ratelimiter"))
}

application {
    mainClass = "org.example.server.Server"
    applicationDefaultJvmArgs = listOf("-Xmx256m")
}
