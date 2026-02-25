package dev.dettmer.simplenotes.utils

import android.content.Context
import dev.dettmer.simplenotes.R
import java.net.URL

/**
 * URL Validator für Network Security (v1.1.2)
 * Erlaubt HTTP nur für lokale Netzwerke (RFC 1918 Private IPs)
 */
object UrlValidator {
    
    // RFC 1918 Private IP Ranges
    private const val PRIVATE_CLASS_A_FIRST_OCTET = 10
    private const val PRIVATE_CLASS_B_FIRST_OCTET = 172
    private const val PRIVATE_CLASS_B_SECOND_OCTET_MIN = 16
    private const val PRIVATE_CLASS_B_SECOND_OCTET_MAX = 31
    private const val PRIVATE_CLASS_C_FIRST_OCTET = 192
    private const val PRIVATE_CLASS_C_SECOND_OCTET = 168
    private const val LOCALHOST_FIRST_OCTET = 127
    private const val OCTET_MAX_VALUE = 255
    
    /**
     * Prüft ob eine URL eine lokale/private Adresse ist
     * Erlaubt:
     * - 192.168.x.x (Class C private)
     * - 10.x.x.x (Class A private)
     * - 172.16.x.x - 172.31.x.x (Class B private)
     * - 127.x.x.x (Localhost)
     * - .local domains (mDNS/Bonjour)
     */
    @Suppress("ReturnCount") // Early returns for validation checks are clearer
    fun isLocalUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host.lowercase()
            
            // Check for .local domains (e.g., nas.local)
            if (host.endsWith(".local")) {
                return true
            }
            
            // Check for localhost
            if (host == "localhost" || host == "127.0.0.1") {
                return true
            }
            
            // Parse IP address if it's numeric
            val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
            val match = ipPattern.find(host)
            
            if (match != null) {
                val octets = match.groupValues.drop(1).map { it.toInt() }
                
                // Validate octets are in range 0-255
                if (octets.any { it > OCTET_MAX_VALUE }) {
                    return false
                }
                
                // Extract octets individually (destructuring with 4 elements triggers detekt warning)
                val o1 = octets[0]
                val o2 = octets[1]
                
                // Check RFC 1918 private IP ranges
                return when {
                    // 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
                    o1 == PRIVATE_CLASS_A_FIRST_OCTET -> true
                    
                    // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
                    o1 == PRIVATE_CLASS_B_FIRST_OCTET &&
                        o2 in PRIVATE_CLASS_B_SECOND_OCTET_MIN..PRIVATE_CLASS_B_SECOND_OCTET_MAX -> true
                    
                    // 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)
                    o1 == PRIVATE_CLASS_C_FIRST_OCTET &&
                        o2 == PRIVATE_CLASS_C_SECOND_OCTET -> true
                    
                    // 127.0.0.0/8 (Localhost)
                    o1 == LOCALHOST_FIRST_OCTET -> true
                    
                    else -> false
                }
            }
            
            // Not a recognized local address
            false
        } catch (e: Exception) {
            Logger.w("UrlValidator", "Failed to parse URL: ${e.message}")
            false
        }
    }
    
    /**
     * Validiert ob HTTP URL erlaubt ist
     * @return Pair<Boolean, String?> - (isValid, errorMessage)
     */
    fun validateHttpUrl(context: Context, url: String): Pair<Boolean, String?> {
        return try {
            val parsedUrl = URL(url)
            
            // HTTPS ist immer erlaubt
            if (parsedUrl.protocol.equals("https", ignoreCase = true)) {
                return Pair(true, null)
            }
            
            // HTTP nur für lokale URLs erlaubt
            if (parsedUrl.protocol.equals("http", ignoreCase = true)) {
                if (isLocalUrl(url)) {
                    return Pair(true, null)
                } else {
                    return Pair(
                        false,
                        context.getString(R.string.error_http_local_only)
                    )
                }
            }
            
            // Anderes Protokoll
            Pair(false, context.getString(R.string.error_invalid_protocol, parsedUrl.protocol))
        } catch (e: Exception) {
            Pair(false, context.getString(R.string.error_invalid_url, e.message.orEmpty()))
        }
    }
}
