package cn.scysn.iam.infrastructure.persistence.jpa.entity

import cn.scysn.iam.domain.shared.Attributes
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object JsonColumns {
    @PublishedApi
    internal val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun write(value: Any?): String = mapper.writeValueAsString(value)

    inline fun <reified T> readSet(json: String?): Set<T> {
        if (json.isNullOrBlank()) return emptySet()
        return mapper.readValue(json, object : TypeReference<Set<T>>() {})
    }

    fun readAttributes(json: String?): Attributes {
        if (json.isNullOrBlank()) return emptyMap()
        return mapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    }

    fun <T> readObject(json: String?, type: Class<T>): T {
        return mapper.readValue(json?.takeIf { it.isNotBlank() } ?: "{}", type)
    }
}
