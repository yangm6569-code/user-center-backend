package cn.scysn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserCenterApplicationTests @Autowired constructor(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @LocalServerPort
    private var port: Int = 0
    private val expectedIssuer = "http://127.0.0.1:8081"

    @Test
    fun `context loads and exposes jwks`() {
        val response = restTemplate.getForEntity("/api/v1/auth/jwks", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["keys"]).hasSize(1)
        assertThat(body["keys"][0]["kid"].asText()).startsWith("uc-")
        assertThat(body["keys"][0]["alg"].asText()).isEqualTo("RS256")
        assertThat(body["keys"][0]["use"].asText()).isEqualTo("sig")
        assertThat(body["keys"][0]["n"].asText()).hasSizeGreaterThan(100)
        assertThat(body["keys"][0]["e"].asText()).isEqualTo("AQAB")
    }

    @Test
    fun `openapi docs are exposed`() {
        val response = restTemplate.getForEntity("/v3/api-docs", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["openapi"].asText()).startsWith("3.")
        assertThat(body["info"]["title"].asText()).isEqualTo("User Center Backend API")
        assertThat(body["paths"].fieldNames().asSequence().toList())
            .contains("/api/v1/auth/login", "/api/v1/users")
    }

    @Test
    fun `swagger ui is exposed`() {
        val response = restTemplate.getForEntity("/swagger-ui/index.html", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Swagger UI")
    }

    @Test
    fun `app page works without keyword`() {
        val response = restTemplate.getForEntity("/api/v1/apps?page=1&pageSize=20", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("OK")
        val apps = body["data"]["items"]
        assertThat(apps.map { it["clientId"].asText() })
            .contains("user-center-console", "mes-web", "report-web")
        val mesWeb = apps.first { it["clientId"].asText() == "mes-web" }
        assertThat(mesWeb["redirectUris"].map { it.asText() })
            .contains(
                "http://127.0.0.1:8001/sso/callback",
                "http://127.0.0.1:8002/sso/callback"
            )
    }

    @Test
    fun `login succeeds with seeded admin account`() {
        val response = restTemplate.postForEntity(
            "/api/v1/auth/login",
            jsonRequest(
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console",
                  "deviceId": "unit-test"
                }
                """.trimIndent()
            ),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("OK")
        assertThat(body["data"]["tokenType"].asText()).isEqualTo("Bearer")
        assertThat(body["data"]["accessToken"].asText()).isNotBlank()
        assertThat(body["data"]["refreshToken"].asText()).isNotBlank()
        assertThat(body["data"]["user"]["username"].asText()).isEqualTo("admin")
        val token = body["data"]["accessToken"].asText()
        val sessionId = body["data"]["sessionId"].asText()
        val header = jwtHeader(token)
        val payload = jwtPayload(token)
        assertThat(header["alg"].asText()).isEqualTo("RS256")
        assertThat(header["kid"].asText()).startsWith("uc-")
        assertThat(payload["iss"].asText()).isEqualTo(expectedIssuer)
        assertThat(payload["sub"].asText()).isEqualTo("user-001")
        assertThat(payload["aud"].asText()).isEqualTo("user-center-console")
        assertThat(payload["azp"].asText()).isEqualTo("user-center-console")
        assertThat(payload["sid"].asText()).isEqualTo(sessionId)
        assertThat(payload["exp"].asLong()).isGreaterThan(Instant.now().epochSecond)

        val jwks = restTemplate.getForEntity("/api/v1/auth/jwks", String::class.java).body.asJson()
        assertThat(verifyJwtSignature(token, jwks["keys"][0])).isTrue()
    }

    @Test
    fun `login rejects invalid password`() {
        val response = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "wrong-password",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )

        assertThat(response.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("INVALID_CREDENTIALS")
        assertThat(body.path("data").isMissingOrNull()).isTrue()
    }

    @Test
    fun `login rejects app without assigned role`() {
        val response = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "report-web"
                }
                """.trimIndent()
        )

        assertThat(response.status).isEqualTo(HttpStatus.FORBIDDEN.value())
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("FORBIDDEN")
    }

    @Test
    fun `user page includes seeded admin user`() {
        val response = restTemplate.getForEntity("/api/v1/users?page=1&pageSize=10", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("OK")
        assertThat(body["data"]["total"].asLong()).isGreaterThanOrEqualTo(1)
        assertThat(body["data"]["items"].map { it["username"].asText() }).contains("admin")
    }

    @Test
    fun `create user validates required fields`() {
        val response = restTemplate.postForEntity(
            "/api/v1/users",
            jsonRequest("""{"username": "", "displayName": "", "initialPassword": ""}"""),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("VALIDATION_ERROR")
        assertThat(body["details"].map { it["field"].asText() })
            .contains("username", "displayName", "initialPassword")
    }

    @Test
    fun `user inherits role from parent org unit`() {
        val suffix = java.lang.Long.toString(System.nanoTime(), 36)
        val roleCode = "test_org_inherited_$suffix"
        val roleId = restTemplate.postForEntity(
            "/api/v1/roles",
            jsonRequest(
                """
                {
                  "appId": "user-center-console",
                  "roleCode": "$roleCode",
                  "roleName": "组织继承测试角色",
                  "roleType": "app"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        val parentOrgId = restTemplate.postForEntity(
            "/api/v1/org-units",
            jsonRequest(
                """
                {
                  "code": "PARENT_$suffix",
                  "name": "父组织-$suffix",
                  "type": "department"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        val childOrgId = restTemplate.postForEntity(
            "/api/v1/org-units",
            jsonRequest(
                """
                {
                  "parentId": "$parentOrgId",
                  "code": "CHILD_$suffix",
                  "name": "子组织-$suffix",
                  "type": "department"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        val bindResponse = restTemplate.postForEntity(
            "/api/v1/org-units/$parentOrgId/roles",
            jsonRequest(
                """
                {
                  "roleIds": ["$roleId"],
                  "mode": "append"
                }
                """.trimIndent()
            ),
            String::class.java
        )
        assertThat(bindResponse.statusCode).isEqualTo(HttpStatus.OK)

        val userId = restTemplate.postForEntity(
            "/api/v1/users",
            jsonRequest(
                """
                {
                  "username": "org_inherit_$suffix",
                  "displayName": "组织继承测试用户",
                  "employeeNo": "EMP_$suffix",
                  "initialPassword": "Test@123456",
                  "forceChangePassword": false
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        val addMemberResponse = restTemplate.postForEntity(
            "/api/v1/org-units/$childOrgId/users/$userId",
            null,
            String::class.java
        )
        assertThat(addMemberResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(addMemberResponse.body.asJson()["data"]["orgUnitIds"].map { it.asText() }).contains(childOrgId)

        val effectiveResponse = restTemplate.getForEntity(
            "/api/v1/users/$userId/permissions/effective?appId=user-center-console",
            String::class.java
        )

        assertThat(effectiveResponse.statusCode).isEqualTo(HttpStatus.OK)
        val effectiveBody = effectiveResponse.body.asJson()
        assertThat(effectiveBody["data"]["roles"].map { it.asText() }).contains(roleCode)
    }

    @Test
    fun `org unit supports batch adding users and listing bound roles`() {
        val suffix = java.lang.Long.toString(System.nanoTime(), 36)
        val roleCode = "test_org_batch_$suffix"
        val roleId = restTemplate.postForEntity(
            "/api/v1/roles",
            jsonRequest(
                """
                {
                  "appId": "report-web",
                  "roleCode": "$roleCode",
                  "roleName": "组织批量成员测试角色",
                  "roleType": "app"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        val orgId = restTemplate.postForEntity(
            "/api/v1/org-units",
            jsonRequest(
                """
                {
                  "code": "BATCH_$suffix",
                  "name": "组织批量成员测试$suffix",
                  "type": "department"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()

        restTemplate.postForEntity(
            "/api/v1/org-units/$orgId/roles",
            jsonRequest("""{"roleIds":["$roleId"],"mode":"append"}"""),
            String::class.java
        )

        val firstUserId = createTestUser("org_batch_a_$suffix", "EMPA_$suffix")
        val secondUserId = createTestUser("org_batch_b_$suffix", "EMPB_$suffix")

        val batchResponse = restTemplate.postForEntity(
            "/api/v1/org-units/$orgId/users",
            jsonRequest("""{"userIds":["$firstUserId","$secondUserId"]}"""),
            String::class.java
        )

        assertThat(batchResponse.statusCode).isEqualTo(HttpStatus.OK)
        val batchBody = batchResponse.body.asJson()
        assertThat(batchBody["data"].map { it["id"].asText() }).containsExactlyInAnyOrder(firstUserId, secondUserId)
        assertThat(batchBody["data"].flatMap { it["orgUnitIds"].map { org -> org.asText() } }).contains(orgId)

        val rolesResponse = restTemplate.getForEntity("/api/v1/org-units/$orgId/roles", String::class.java)

        assertThat(rolesResponse.statusCode).isEqualTo(HttpStatus.OK)
        val rolesBody = rolesResponse.body.asJson()
        assertThat(rolesBody["data"].map { it["id"].asText() }).contains(roleId)
        assertThat(rolesBody["data"].map { it["roleCode"].asText() }).contains(roleCode)

        listOf(firstUserId, secondUserId).forEach { userId ->
            val effectiveResponse = restTemplate.getForEntity(
                "/api/v1/users/$userId/permissions/effective?appId=report-web",
                String::class.java
            )
            assertThat(effectiveResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(effectiveResponse.body.asJson()["data"]["roles"].map { it.asText() }).contains(roleCode)
        }
    }

    @Test
    fun `oauth authorize and token use sso cookie authorization code flow`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console",
                  "deviceId": "sso-test"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val ssoCookie = loginResponse.headers["Set-Cookie"]
        assertThat(ssoCookie).contains("UC_SSO_SESSION=")
        val cookieHeader = ssoCookie!!

        val authorizeResponse = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=user-center-console&redirect_uri=http://example.test/callback&response_type=code&state=abc",
            headers = mapOf(HttpHeaders.COOKIE to cookieHeader.substringBefore(";")),
            followRedirects = false
        )

        assertThat(authorizeResponse.status).isEqualTo(HttpStatus.FOUND.value())
        val location = authorizeResponse.headers["Location"]
        assertThat(location).startsWith("http://example.test/callback?code=code_")
        assertThat(location).endsWith("&state=abc")
        val code = location!!.substringAfter("code=").substringBefore("&")

        val tokenResponse = rawRequest(
            method = "POST",
            path = "/oauth/token",
            body =
                """
                {
                  "grant_type": "authorization_code",
                  "client_id": "user-center-console",
                  "redirect_uri": "http://example.test/callback",
                  "code": "$code"
                }
                """.trimIndent()
        )
        assertThat(tokenResponse.status).isEqualTo(HttpStatus.OK.value())
        val tokenBody = tokenResponse.body.asJson()
        assertThat(tokenBody["tokenType"].asText()).isEqualTo("Bearer")
        assertThat(tokenBody["accessToken"].asText()).isNotBlank()
        assertThat(tokenBody["refreshToken"].asText()).isNotBlank()
        val tokenPayload = jwtPayload(tokenBody["accessToken"].asText())
        assertThat(tokenPayload["iss"].asText()).isEqualTo(expectedIssuer)
        assertThat(tokenPayload["sub"].asText()).isEqualTo("user-001")
        assertThat(tokenPayload["aud"].asText()).isEqualTo("user-center-console")
        assertThat(tokenPayload["azp"].asText()).isEqualTo("user-center-console")

        val reusedCodeResponse = rawRequest(
            method = "POST",
            path = "/oauth/token",
            body =
                """
                {
                  "grant_type": "authorization_code",
                  "client_id": "user-center-console",
                  "redirect_uri": "http://example.test/callback",
                  "code": "$code"
                }
                """.trimIndent()
        )
        assertThat(reusedCodeResponse.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    @Test
    fun `oauth authorize redirects to unified login page without sso cookie`() {
        val response = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=mes-web&redirect_uri=http://localhost:8081/demo-apps/mes/index.html&response_type=code&state=abc",
            followRedirects = false
        )

        assertThat(response.status).isEqualTo(HttpStatus.FOUND.value())
        assertThat(response.headers["Location"]).startsWith("/sso-login.html?return_to=")
    }

    @Test
    fun `oauth authorize issues mes token after unified sso login`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        val ssoCookie = loginResponse.headers["Set-Cookie"]
        assertThat(ssoCookie).contains("UC_SSO_SESSION=")

        val authorizeResponse = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=mes-web&redirect_uri=http://localhost:8081/demo-apps/mes/index.html&response_type=code",
            headers = mapOf(HttpHeaders.COOKIE to ssoCookie!!.substringBefore(";")),
            followRedirects = false
        )
        assertThat(authorizeResponse.status).isEqualTo(HttpStatus.FOUND.value())
        val code = authorizeResponse.headers["Location"]!!.substringAfter("code=").substringBefore("&")

        val tokenResponse = rawRequest(
            method = "POST",
            path = "/oauth/token",
            body =
                """
                {
                  "grant_type": "authorization_code",
                  "client_id": "mes-web",
                  "redirect_uri": "http://localhost:8081/demo-apps/mes/index.html",
                  "code": "$code"
                }
                """.trimIndent()
        )

        assertThat(tokenResponse.status).isEqualTo(HttpStatus.OK.value())
        val payload = jwtPayload(tokenResponse.body.asJson()["accessToken"].asText())
        assertThat(payload["aud"].asText()).isEqualTo("mes-web")
        assertThat(payload["azp"].asText()).isEqualTo("mes-web")
    }

    @Test
    fun `refresh rotates token and renews sso cookie`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val loginBody = loginResponse.body.asJson()
        val refreshToken = loginBody["data"]["refreshToken"].asText()
        val ssoCookie = loginResponse.headers["Set-Cookie"]!!.substringBefore(";")

        val refreshResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/refresh",
            body =
                """
                {
                  "clientId": "user-center-console",
                  "refreshToken": "$refreshToken"
                }
                """.trimIndent(),
            headers = mapOf(HttpHeaders.COOKIE to ssoCookie)
        )

        assertThat(refreshResponse.status).isEqualTo(HttpStatus.OK.value())
        val refreshBody = refreshResponse.body.asJson()
        assertThat(refreshBody["data"]["accessToken"].asText()).isNotBlank()
        assertThat(refreshBody["data"]["refreshToken"].asText()).isNotEqualTo(refreshToken)
        assertThat(refreshResponse.headers["Set-Cookie"]).contains("UC_SSO_SESSION=")
    }

    @Test
    fun `invalid bearer token is not allowed to fall back to sso cookie`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val loginBody = loginResponse.body.asJson()
        val invalidAccessToken = "${loginBody["data"]["accessToken"].asText()}x"
        val ssoCookie = loginResponse.headers["Set-Cookie"]!!.substringBefore(";")

        val meResponse = rawRequest(
            method = "GET",
            path = "/api/v1/auth/me",
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer $invalidAccessToken",
                HttpHeaders.COOKIE to ssoCookie,
            )
        )

        assertThat(meResponse.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `current user requires an explicit login context`() {
        val meResponse = rawRequest(
            method = "GET",
            path = "/api/v1/auth/me",
        )

        assertThat(meResponse.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `sso cookie alone does not authenticate console api`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val ssoCookie = loginResponse.headers["Set-Cookie"]!!.substringBefore(";")

        val meResponse = rawRequest(
            method = "GET",
            path = "/api/v1/auth/me",
            headers = mapOf(HttpHeaders.COOKIE to ssoCookie)
        )

        assertThat(meResponse.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `audit login events can be queried without time filters`() {
        val response = restTemplate.getForEntity("/api/v1/audit/login-events?page=1&pageSize=20", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("OK")
        assertThat(body["data"]["items"].isArray).isTrue()
    }

    @Test
    fun `audit tabs expose management permission and token events`() {
        val suffix = java.lang.Long.toString(System.nanoTime(), 36)
        val roleId = restTemplate.postForEntity(
            "/api/v1/roles",
            jsonRequest(
                """
                {
                  "appId": "user-center-console",
                  "roleCode": "test_audit_role_$suffix",
                  "roleName": "audit tab test role",
                  "roleType": "app"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()
        val userId = createTestUser("audit_tab_$suffix", "AUDIT_$suffix")
        restTemplate.postForEntity(
            "/api/v1/users/$userId/roles",
            jsonRequest(
                """
                {
                  "roleIds": ["$roleId"],
                  "reason": "audit tab test"
                }
                """.trimIndent()
            ),
            String::class.java
        )

        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val refreshToken = loginResponse.body.asJson()["data"]["refreshToken"].asText()
        val ssoCookie = loginResponse.headers["Set-Cookie"]!!.substringBefore(";")
        val refreshResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/refresh",
            body =
                """
                {
                  "clientId": "user-center-console",
                  "refreshToken": "$refreshToken"
                }
                """.trimIndent(),
            headers = mapOf(HttpHeaders.COOKIE to ssoCookie)
        )
        assertThat(refreshResponse.status).isEqualTo(HttpStatus.OK.value())

        assertThat(auditEventTypes("/api/v1/audit/admin-events?page=1&pageSize=50"))
            .contains("USER_CREATED")
        assertThat(auditEventTypes("/api/v1/audit/permission-events?page=1&pageSize=50"))
            .contains("ROLE_CREATED", "USER_ROLES_GRANTED")
        assertThat(auditEventTypes("/api/v1/audit/token-events?page=1&pageSize=50"))
            .contains("TOKEN_REFRESHED")
    }

    @Test
    fun `logout revokes current app session but keeps sso session for silent login`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val loginBody = loginResponse.body.asJson()
        val refreshToken = loginBody["data"]["refreshToken"].asText()
        val oldSsoCookie = loginResponse.headers["Set-Cookie"]!!.substringBefore(";")

        val logoutResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/logout",
            body =
                """
                {
                  "refreshToken": "$refreshToken"
                }
                """.trimIndent(),
            headers = mapOf(HttpHeaders.COOKIE to oldSsoCookie)
        )
        assertThat(logoutResponse.status).isEqualTo(HttpStatus.OK.value())
        val logoutBody = logoutResponse.body.asJson()
        assertThat(logoutBody["data"]["revoked"].asBoolean()).isTrue()
        assertThat(logoutBody["data"].has("revokedSsoSessions")).isFalse()

        val authorizeResponse = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=mes-web&redirect_uri=http://localhost:8081/demo-apps/mes/index.html&response_type=code",
            headers = mapOf(HttpHeaders.COOKIE to oldSsoCookie),
            followRedirects = false
        )

        assertThat(authorizeResponse.status).isEqualTo(HttpStatus.FOUND.value())
        assertThat(authorizeResponse.headers["Location"])
            .startsWith("http://localhost:8081/demo-apps/mes/index.html?code=code_")
    }

    @Test
    fun `logout all uses current sso cookie user instead of default admin`() {
        val adminLoginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(adminLoginResponse.status).isEqualTo(HttpStatus.OK.value())
        val adminRefreshToken = adminLoginResponse.body.asJson()["data"]["refreshToken"].asText()

        val suffix = java.lang.Long.toString(System.nanoTime(), 36)
        val roleId = restTemplate.postForEntity(
            "/api/v1/roles",
            jsonRequest(
                """
                {
                  "appId": "report-web",
                  "roleCode": "test_logout_all_report_$suffix",
                  "roleName": "logout all report test",
                  "roleType": "app"
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()
        val userId = createTestUser("logout_all_pym_$suffix", "LOGOUT_$suffix")
        restTemplate.postForEntity(
            "/api/v1/users/$userId/roles",
            jsonRequest(
                """
                {
                  "roleIds": ["$roleId"],
                  "reason": "test"
                }
                """.trimIndent()
            ),
            String::class.java
        )

        val userLoginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "logout_all_pym_$suffix",
                  "password": "Test@123456",
                  "clientId": "report-web"
                }
                """.trimIndent()
        )
        assertThat(userLoginResponse.status).isEqualTo(HttpStatus.OK.value())
        val userAccessToken = userLoginResponse.body.asJson()["data"]["accessToken"].asText()
        val userSsoCookie = userLoginResponse.headers["Set-Cookie"]!!.substringBefore(";")

        val logoutAllResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/logout-all",
            body = """{"reason":"test"}""",
            headers = mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer $userAccessToken",
                HttpHeaders.COOKIE to userSsoCookie,
            )
        )
        assertThat(logoutAllResponse.status).isEqualTo(HttpStatus.OK.value())

        val userAuthorizeResponse = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=report-web&redirect_uri=http://localhost:8081/demo-apps/report/index.html&response_type=code",
            headers = mapOf(HttpHeaders.COOKIE to userSsoCookie),
            followRedirects = false
        )
        assertThat(userAuthorizeResponse.headers["Location"]).startsWith("/sso-login.html?return_to=")

        val adminRefreshResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/refresh",
            body =
                """
                {
                  "clientId": "user-center-console",
                  "refreshToken": "$adminRefreshToken"
                }
                """.trimIndent()
        )
        assertThat(adminRefreshResponse.status).isEqualTo(HttpStatus.OK.value())
    }

    @Test
    fun `oauth authorize rejects app without assigned role even with sso cookie`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        assertThat(loginResponse.status).isEqualTo(HttpStatus.OK.value())
        val ssoCookie = loginResponse.headers["Set-Cookie"]
        assertThat(ssoCookie).contains("UC_SSO_SESSION=")
        val cookieHeader = ssoCookie!!

        val authorizeResponse = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=report-web&redirect_uri=http://localhost:8081/demo-apps/report/index.html&response_type=code",
            headers = mapOf(HttpHeaders.COOKIE to cookieHeader.substringBefore(";")),
            followRedirects = false
        )

        assertThat(authorizeResponse.status).isEqualTo(HttpStatus.FOUND.value())
        assertThat(authorizeResponse.headers["Location"])
            .startsWith("http://localhost:8081/demo-apps/report/index.html?error=access_denied")
    }

    @Test
    fun `oauth authorize rejects invalid redirect uri`() {
        val loginResponse = rawRequest(
            method = "POST",
            path = "/api/v1/auth/login",
            body =
                """
                {
                  "username": "admin",
                  "password": "Admin@123456",
                  "clientId": "user-center-console"
                }
                """.trimIndent()
        )
        val ssoCookie = loginResponse.headers["Set-Cookie"]
        assertThat(ssoCookie).contains("UC_SSO_SESSION=")
        val cookieHeader = ssoCookie!!

        val response = rawRequest(
            method = "GET",
            path = "/oauth/authorize?client_id=user-center-console&redirect_uri=http://evil.test/callback&response_type=code",
            headers = mapOf(HttpHeaders.COOKIE to cookieHeader.substringBefore(";")),
            followRedirects = false
        )

        assertThat(response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
    }

    private fun createTestUser(username: String, employeeNo: String): String {
        return restTemplate.postForEntity(
            "/api/v1/users",
            jsonRequest(
                """
                {
                  "username": "$username",
                  "displayName": "$username",
                  "employeeNo": "$employeeNo",
                  "initialPassword": "Test@123456",
                  "forceChangePassword": false
                }
                """.trimIndent()
            ),
            String::class.java
        ).body.asJson()["data"]["id"].asText()
    }

    private fun auditEventTypes(path: String): List<String> {
        val response = restTemplate.getForEntity(path, String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body.asJson()
        assertThat(body["code"].asText()).isEqualTo("OK")
        return body["data"]["items"].map { it["eventType"].asText() }
    }

    private fun jsonRequest(json: String): HttpEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(json, headers)
    }

    private fun rawRequest(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): RawResponse {
        val connection = URL("http://localhost:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = followRedirects
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val responseBody = (connection.errorStream ?: runCatching { connection.inputStream }.getOrNull())
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        return RawResponse(
            status = status,
            body = responseBody,
            headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
        )
    }

    private fun String?.asJson(): JsonNode {
        assertThat(this).isNotBlank()
        return objectMapper.readTree(this)
    }

    private fun jwtHeader(token: String): JsonNode = jwtPart(token, 0)

    private fun jwtPayload(token: String): JsonNode = jwtPart(token, 1)

    private fun jwtPart(token: String, index: Int): JsonNode {
        val parts = token.split(".")
        assertThat(parts).hasSize(3)
        return objectMapper.readTree(Base64.getUrlDecoder().decode(parts[index]).toString(Charsets.UTF_8))
    }

    private fun verifyJwtSignature(token: String, jwk: JsonNode): Boolean {
        val parts = token.split(".")
        assertThat(parts).hasSize(3)
        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(jwk["n"].asText()))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(jwk["e"].asText()))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(publicKey)
        signature.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
        return signature.verify(Base64.getUrlDecoder().decode(parts[2]))
    }

    private fun JsonNode.isMissingOrNull(): Boolean = isMissingNode || isNull

    private data class RawResponse(
        val status: Int,
        val body: String,
        val headers: Map<String, String>,
    )
}
