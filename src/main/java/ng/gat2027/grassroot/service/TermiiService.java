package ng.gat2027.grassroot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends transactional SMS via Termii (api.ng.termii.com) - the primary password-reset channel,
 * since every member has a phone number (mandatory at registration), unlike email which is
 * intentionally optional for this audience. Mirrors MailService/WhatsAppService: missing
 * credentials never break a request - it just logs and lets the caller fall back to the next
 * channel (WhatsApp, then email, then the dev-mode direct link).
 *
 * <p>Uses Termii's "dnd" channel, meant for OTP/transactional alerts so delivery isn't blocked
 * by Nigeria's Do-Not-Disturb registry the way promotional SMS would be.
 */
@Service
public class TermiiService {
    private static final Logger log = LoggerFactory.getLogger(TermiiService.class);

    private final RestClient client;
    private final boolean configured;
    private final String apiKey;
    private final String senderId;

    public TermiiService(@Value("${gat.termii.api-key:}") String apiKey,
                          @Value("${gat.termii.sender-id:}") String senderId,
                          @Value("${gat.termii.api-base:https://api.ng.termii.com}") String apiBase) {
        this.apiKey = apiKey;
        this.senderId = senderId;
        this.configured = apiKey != null && !apiKey.isBlank() && senderId != null && !senderId.isBlank();
        this.client = configured ? RestClient.builder().baseUrl(apiBase).build() : null;
    }

    public boolean isConfigured() { return configured; }

    /** phoneE164 is digits only, no leading + (e.g. "2348031234567") - see Codes.nigeriaE164. Returns
     *  true only if Termii accepted the send; the caller should fall back to another channel on false. */
    public boolean sendPasswordResetLink(String phoneE164, String firstName, String resetLink) {
        if (!configured) {
            log.warn("[termii] Not configured — password reset link for {} <{}>: {}", firstName, phoneE164, resetLink);
            return false;
        }
        String message = "Hi " + firstName + ", reset your GAT 2027 password here: " + resetLink + " (expires in 30 minutes)";
        try {
            Map<String, Object> body = Map.of(
                "to", phoneE164,
                "from", senderId,
                "sms", message,
                "type", "plain",
                "channel", "dnd",
                "api_key", apiKey
            );
            client.post().uri("/api/sms/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[termii] Failed to send password reset to {}: {}", phoneE164, e.getMessage());
            return false;
        }
    }
}
