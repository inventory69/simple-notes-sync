package dev.dettmer.simplenotes.utils

import java.net.URL

/**
 * URL Validator für Network Security (v1.1.2)
 * Erlaubt HTTP nur für lokale Netzwerke (RFC 1918 Private IPs)
 */
object UrlValidator {
    
    /**
     * Prüft ob eine URL eine lokale/private Adresse ist
     * Erlaubt:
     * - 192.168.x.x (Class C private)
     * - 10.x.x.x (Class A private)
     * - 172.16.x.x - 172.31.x.x (Class B private)
     * - 127.x.x.x (Localhost)
     * - .local domains (mDNS/Bonjour)
     */
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
                if (octets.any { it > 255 }) {
                    return false
                }
                
                val (o1, o2, o3, o4) = octets
                
                // Check RFC 1918 private IP ranges
                return when {
                    // 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
                    o1 == 10 -> true
                    
                    // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
                    o1 == 172 && o2 in 16..31 -> true
                    
                    // 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)
                    o1 == 192 && o2 == 168 -> true
                    
                    // 127.0.0.0/8 (Localhost)
                    o1 == 127 -> true
                    
                    else -> false
                }
            }
            
            // Not a recognized local address
            false
        } catch (e: Exception) {
            // Invalid URL format
            false
        }
    }
    
    /**
     * Validiert ob HTTP URL erlaubt ist
     * @return Pair<Boolean, String?> - (isValid, errorMessage)
     */
    fun validateHttpUrl(url: String): Pair<Boolean, String?> {
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
                        "HTTP ist nur für lokale Server erlaubt (z.B. 192.168.x.x, 10.x.x.x, nas.local). " +
                        "Für öffentliche Server verwende bitte HTTPS."
                    )
                }
            }
            
            // Anderes Protokoll
            Pair(false, "Ungültiges Protokoll: ${parsedUrl.protocol}. Bitte verwende HTTP oder HTTPS.")
        } catch (e: Exception) {
            Pair(false, "Ungültige URL: ${e.message}")
        }
    }
}
