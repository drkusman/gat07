package ng.gat2027.grassroot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends transactional messages via Meta's WhatsApp Business Cloud API. Mirrors {@link MailService}:
 * missing credentials never break a request - it just logs the message and lets the caller fall back
 * (e.g. to email, or the dev-mode direct link shown on /forgot-password).
 *
 * <p>Setup needed before this does anything: a Meta Business Platform app with a WhatsApp Business
 * phone number, and a "Utility" category message template (submitted to Meta for approval) with one
 * body text variable (first name) and one dynamic URL button variable (the reset link). Once approved,
 * set the GAT_WHATSAPP_ACCESS_TOKEN, GAT_WHATSAPP_PHONE_NUMBER_ID, and (if the template name/language
 * differ from the defaults below) GAT_WHATSAPP_TEMPLATE_NAME / GAT_WHATSAPP_LANGUAGE_CODE env vars.
 */
@Service
public class WhatsAppService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final RestClient client;
    private final boolean configured;
    private final String phoneNumberId;
    private final String templateName;
    private final String languageCode;

    public WhatsAppService(@Value("${gat.whatsapp.access-token:}") String accessToken,
                            @Value("${gat.whatsapp.phone-number-id:}") String phoneNumberId,
                            @Value("${gat.whatsapp.template-name:password_reset}") String templateName,
                            @Value("${gat.whatsapp.language-code:en_US}") String languageCode,
                            @Value("${gat.whatsapp.api-base:https://graph.facebook.com/v21.0}") String apiBase) {
        this.phoneNumberId = phoneNumberId;
        this.templateName = templateName;
        this.languageCode = languageCode;
        this.configured = accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
        this.client = configured
            ? RestClient.builder().baseUrl(apiBase).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).build()
            : null;
    }

    public boolean isConfigured() { return configured; }

    /** phoneE164 is digits only, no leading + (e.g. "2348031234567") - see Codes.nigeriaE164. Returns
     *  true only if the API accepted the send; the caller should fall back to another channel on false. */
    public boolean sendPasswordResetLink(String phoneE164, String firstName, String resetLink) {
        if (!configured) {
            log.warn("[whatsapp] Not configured — password reset link for {} <{}>: {}", firstName, phoneE164, resetLink);
            return false;
        }
        try {
            Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", phoneE164,
                "type", "template",
                "template", Map.of(
                    "name", templateName,
                    "language", Map.of("code", languageCode),
                    "components", List.of(
                        Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", firstName))),
                        Map.of("type", "button", "sub_type", "url", "index", "0",
                            "parameters", List.of(Map.of("type", "text", "text", resetLink)))
                    )
                )
            );
            client.post().uri("/{phoneNumberId}/messages", phoneNumberId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("[whatsapp] Failed to send password reset to {}: {}", phoneE164, e.getMessage());
            return false;
        }
    }
}
