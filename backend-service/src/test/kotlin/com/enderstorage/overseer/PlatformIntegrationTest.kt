package com.enderstorage.overseer

import com.enderstorage.overseer.entity.AlertType
import com.enderstorage.overseer.entity.Severity
import com.enderstorage.overseer.repository.AnomalyAlertRepository
import com.enderstorage.overseer.repository.MiningEventRepository
import com.enderstorage.sentinel.dto.BlockBreakTelemetry
import com.enderstorage.sentinel.dto.Heartbeat
import com.enderstorage.sentinel.dto.PlayerSnapshot
import com.enderstorage.sentinel.dto.RequestSigning
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** End-to-end over HTTP: browser session + CSRF, signed agent traffic, and every PostGIS-backed query. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformIntegrationTest : PostgisIntegrationTest() {
    @LocalServerPort var port = 0
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var events: MiningEventRepository
    @Autowired lateinit var alerts: AnomalyAlertRepository

    private val player = UUID.randomUUID()

    inner class Console {
        private val cookies = CookieManager()
        private val http = HttpClient.newBuilder().cookieHandler(cookies).build()
        fun send(method: String, path: String, body: Any? = null): HttpResponse<String> {
            val xsrf = cookies.cookieStore.cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value ?: ""
            val request = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", xsrf)
                .method(method, body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) } ?: HttpRequest.BodyPublishers.noBody())
                .build()
            return http.send(request, HttpResponse.BodyHandlers.ofString())
        }
        fun tree(method: String, path: String, body: Any? = null): JsonNode =
            send(method, path, body).also { assertEquals(200, it.statusCode(), "$method $path: ${it.body()}") }.body().let(json::readTree)
        fun login(username: String, password: String) = apply {
            send("GET", "/api/v1/auth/csrf")
            assertEquals(200, send("POST", "/api/v1/auth/login", mapOf("username" to username, "password" to password)).statusCode())
        }
    }

    inner class Agent(private val instance: String, private val secret: String) {
        private val http = HttpClient.newHttpClient()
        var token: String? = null
        fun send(purpose: String, body: Any, nonce: String = UUID.randomUUID().toString(),
                 timestamp: String = System.currentTimeMillis().toString(), signWith: String = secret): HttpResponse<String> {
            val bytes = json.writeValueAsBytes(body)
            val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/agent/$instance/$purpose"))
                .header("X-Caenis-Timestamp", timestamp).header("X-Caenis-Nonce", nonce)
                .header("X-Caenis-Signature", RequestSigning.sign(signWith, instance, purpose, timestamp, nonce, bytes))
                .apply { token?.let { header("X-Caenis-Token", it) } }
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build()
            return http.send(request, HttpResponse.BodyHandlers.ofString())
        }
        fun ok(purpose: String, body: Any): JsonNode =
            send(purpose, body).also { assertEquals(200, it.statusCode(), "$purpose: ${it.body()}") }.body().let(json::readTree)
        fun enroll() = apply { token = ok("enroll", emptyMap<String, Any>())["token"].asText() }
    }

    private fun admin() = Console().login("admin", ADMIN_PASSWORD)

    private fun register(admin: Console, id: String): Agent =
        Agent(id, admin.tree("POST", "/api/v1/fleet", mapOf("id" to id, "name" to id))["agentSecret"].asText()).enroll()

    private fun dig(instance: String, x: Int, exposed: Boolean, delta: Int? = null, at: Long = System.currentTimeMillis()) = BlockBreakTelemetry(
        playerId = player, playerName = "Steve_01", timestampMs = at, world = "world", x = x, y = -50, z = 200,
        blockType = "DIAMOND_ORE", toolUsed = "DIAMOND_PICKAXE", isExposedToAirOrCave = exposed, breakDeltaMs = delta,
        instanceId = instance, timingReliable = delta != null)

    private fun heartbeat() = Heartbeat(listOf(20.0, 19.9, 19.8), 12.5, 1000, 2000, 4000, 10, 5,
        listOf(PlayerSnapshot(player.toString(), "Steve_01", 30, "world")), listOf("world"), 0, 0, 0, "test", System.currentTimeMillis())

    private fun minedOn(instance: String) = events.findAll().count { it.instanceId == instance }

    @Test
    fun `agent ingest is authenticated, idempotent and feeds both detectors`() {
        val id = "srv-it-ingest"
        val agent = register(admin(), id)
        agent.token = "wrong"
        assertEquals(401, agent.send("heartbeat", heartbeat()).statusCode())
        agent.enroll()
        assertEquals(401, agent.send("mining", listOf(dig(id, 0, false)), signWith = "not-the-secret-0123456789abcdef").statusCode())

        val start = System.currentTimeMillis() - 60_000
        val batch = (0 until 12).map { dig(id, 100 + it, exposed = false, at = start + it * 1000L) }
        val nonce = UUID.randomUUID().toString(); val ts = System.currentTimeMillis().toString()
        assertEquals(12, json.readTree(agent.send("mining", batch, nonce, ts).body())["accepted"].asInt())
        assertEquals(200, agent.send("mining", batch, nonce, ts).statusCode())          // exact replay is acknowledged
        assertEquals(0, agent.ok("mining", batch)["accepted"].asInt())                 // same events, new nonce
        assertEquals(12, minedOn(id))

        assertEquals(1, agent.ok("mining", listOf(dig(id, 300, exposed = true, delta = 40)))["accepted"].asInt())
        val raised = alerts.findAll().filter { it.instanceId == id }
        assertTrue(raised.any { it.alertType == AlertType.TOPOLOGICAL_OCCLUSION_XRAY }, "x-ray alert expected")
        assertTrue(raised.any { it.alertType == AlertType.FAST_MINING_TEMPORAL_BREACH && it.severity == Severity.CRITICAL }, "fast-mining alert expected")
    }

    @Test
    fun `roles are enforced with 403 and conflicts return 409`() {
        val admin = admin()
        val analyst = mapOf("username" to "it_analyst", "password" to "analyst-password-1234", "role" to "ANALYST")
        assertEquals(200, admin.send("POST", "/api/v1/users", analyst).statusCode())
        assertEquals(409, admin.send("POST", "/api/v1/users", analyst).statusCode())
        val viewer = Console().login("it_analyst", "analyst-password-1234")
        assertEquals(403, viewer.send("GET", "/api/v1/users").statusCode())
        assertEquals(200, viewer.send("GET", "/api/v1/deck/stats").statusCode())
        assertEquals(401, Console().send("GET", "/api/v1/auth/me").statusCode())
        assertEquals(200, admin.send("POST", "/api/v1/auth/logout").statusCode())
        assertEquals(401, admin.send("GET", "/api/v1/auth/me").statusCode())
    }

    @Test
    fun `observation deck, fleet and audit queries run against PostGIS`() {
        val admin = admin()
        val id = "srv-it-deck"
        val agent = register(admin, id)
        agent.ok("heartbeat", heartbeat())
        agent.ok("mining", (0 until 12).map { dig(id, 100 + it, exposed = false, at = System.currentTimeMillis() - 30_000 + it * 1000L) })

        val box = "instanceId=$id&world=world&minX=-1000&maxX=1000&minZ=-1000&maxZ=1000"
        assertEquals(12, admin.tree("GET", "/api/v1/deck/map?$box").size())
        val clusters = admin.tree("GET", "/api/v1/deck/map?$box&cellSize=64")
        assertEquals(1, clusters.size())                                               // x 100..111 share one 64-block cell
        assertEquals(12, clusters[0]["count"].asInt())
        assertTrue(admin.tree("GET", "/api/v1/deck/alerts?instanceId=$id&q=Steve").size() > 0)
        assertTrue(admin.tree("GET", "/api/v1/fleet/$id/history?hours=1").size() > 0)
        assertEquals("ONLINE", admin.tree("GET", "/api/v1/fleet/$id")["status"].asText())
        assertTrue(admin.tree("GET", "/api/v1/deck/stats")["totalEventsLogged"].asLong() >= 12)
        assertTrue(admin.tree("GET", "/api/v1/audit?instanceId=$id&q=INSTANCE").any { it["action"].asText() == "INSTANCE_CREATE" })
        assertEquals(0, admin.tree("POST", "/api/v1/settings/baselines/train",
            mapOf("instanceId" to id, "from" to "2026-01-01T00:00:00Z", "until" to "2026-01-20T00:00:00Z", "reviewedAsLegitimate" to true))["models"].asInt())

        // Analysts see aliases and cannot search by real identity.
        val analyst = mapOf("username" to "it_deck_analyst", "password" to "analyst-password-5678", "role" to "ANALYST")
        assertEquals(200, admin.send("POST", "/api/v1/users", analyst).statusCode())
        val viewer = Console().login("it_deck_analyst", "analyst-password-5678")
        assertEquals(0, viewer.tree("GET", "/api/v1/deck/alerts?instanceId=$id&q=Steve").size())
        val seen = viewer.tree("GET", "/api/v1/deck/alerts?instanceId=$id")
        assertTrue(seen.size() > 0 && seen.all { it["playerName"].asText() != "Steve_01" })
        assertNotEquals(player.toString(), viewer.tree("GET", "/api/v1/deck/map?$box")[0]["playerId"].asText())
    }
}
