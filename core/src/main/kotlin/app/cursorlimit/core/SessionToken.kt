package app.cursorlimit.core

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * A Cursor browser login is the WorkosCursorSessionToken cookie.
 * It has two parts, a user id and a JWT, separated by "::" (often %3A%3A in the cookie).
 * The app sends the JWT as a Bearer token to api2.cursor.sh and the same cookie to cursor.com.
 */
object SessionToken {
    fun accessToken(raw: String): String? = split(raw)?.jwt

    fun userId(raw: String): String? {
        val parts = split(raw) ?: return null
        if (!parts.userFromPrefix.isNullOrBlank()) return parts.userFromPrefix
        return userFromJwt(parts.jwt)
    }

    /** Cookie header for cursor.com. It needs the user id. */
    fun cookieHeader(raw: String): String? {
        val jwt = accessToken(raw) ?: return null
        val user = userId(raw) ?: return null
        return "WorkosCursorSessionToken=$user%3A%3A$jwt"
    }

    private data class Parts(val userFromPrefix: String?, val jwt: String)

    private fun split(raw: String): Parts? {
        var text = raw.trim().trim('"').trim('\'')
        val marker = "WorkosCursorSessionToken="
        val markerAt = text.indexOf(marker, ignoreCase = true)
        if (markerAt >= 0) text = text.substring(markerAt + marker.length)
        text = text.substringBefore(';').substringBefore(' ').substringBefore('\n').trim()
        text = text.removePrefix("Bearer ").removePrefix("bearer ")
        text = text.replaceFirst("%3A%3A", "::", ignoreCase = true)
        if (text.isEmpty()) return null

        val separator = text.indexOf("::")
        val user = if (separator >= 0) text.substring(0, separator) else null
        val jwt = if (separator >= 0) text.substring(separator + 2) else text
        if (jwt.split('.').size != 3) return null
        return Parts(user?.takeIf { it.isNotBlank() }, jwt)
    }

    private fun userFromJwt(jwt: String): String? {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        val json = decodeBase64Url(payload) ?: return null
        val sub = try {
            JsonParser.parseString(json).asJsonObject.get("sub")?.asString
        } catch (_: Exception) {
            null
        } ?: return null
        // sub can be "user_abc" or "google-oauth2|user_abc". The user id is the part after "|".
        val id = sub.substringAfterLast('|').trim()
        return id.takeIf { it.isNotEmpty() }
    }

    private fun decodeBase64Url(data: String): String? {
        return try {
            var text = data.replace('-', '+').replace('_', '/')
            val pad = (4 - text.length % 4) % 4
            text += "=".repeat(pad)
            String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
