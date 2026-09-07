package com.enderstorage.overseer.fleet

import com.enderstorage.overseer.security.Secrets
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@Service
class CredentialProvider(
    private val secrets: Secrets, private val json: ObjectMapper,
    @Value("\${caenis.vault-url}") private val vaultUrl: String,
    @Value("\${caenis.vault-token-file}") private val tokenFile: String
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    fun password(node: RegisteredInstance): String {
        if (node.vaultPath == null) return node.rconPassword?.let(secrets::decrypt) ?: error("RCON credentials are not configured")
        require(vaultUrl.startsWith("https://") && tokenFile.isNotBlank()) { "Vault requires HTTPS and a token file" }
        val token = Files.readString(Path.of(tokenFile)).trim()
        val request = HttpRequest.newBuilder(URI.create(vaultUrl.trimEnd('/') + "/v1/" + node.vaultPath))
            .timeout(Duration.ofSeconds(5)).header("X-Vault-Token",token).GET().build()
        val result = http.send(request,HttpResponse.BodyHandlers.ofString())
        check(result.statusCode() == 200) { "Vault credential lookup failed" }
        val data = json.readTree(result.body()).path("data").path("data")
        return data.path("password").asText().also { require(it.isNotBlank()) { "Vault password is missing" } }
    }
}
