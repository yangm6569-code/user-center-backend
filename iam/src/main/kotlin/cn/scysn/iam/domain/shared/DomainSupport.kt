package cn.scysn.iam.domain.shared

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

typealias Attributes = Map<String, Any?>

fun newId(prefix: String): String = "$prefix-${UUID.randomUUID().toString().replace("-", "")}"

fun now(): OffsetDateTime = OffsetDateTime.now()

fun randomSecret(prefix: String): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return "$prefix${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
}

fun requireCode(value: String, fieldName: String) {
    require(value.isNotBlank()) { "$fieldName 不能为空" }
    require(value.length <= 128) { "$fieldName 长度不能超过 128 个字符" }
    require(value.matches(Regex("^[a-zA-Z0-9_.:-]+$"))) { "$fieldName 只能包含字母、数字、下划线、点、冒号和短横线" }
}

fun requireName(value: String, fieldName: String) {
    require(value.isNotBlank()) { "$fieldName 不能为空" }
    require(value.length <= 128) { "$fieldName 长度不能超过 128 个字符" }
}
