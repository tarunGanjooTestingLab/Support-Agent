package com.supportAgentMcpClient.config;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.supportAgentMcpClient.dto.MailpitMessage;
import com.supportAgentMcpClient.dto.MailpitMessageResponse;
import com.supportAgentMcpClient.dto.MailpitMessageSummary;

/**
 * Thin wrapper over the Mailpit REST API. Encapsulates the few calls the agent
 * needs so the rest of the code never deals with raw HTTP or Mailpit URLs.
 *
 * @see <a href="https://mailpit.axllent.org/docs/api-v1/">Mailpit API v1</a>
 */

@Component 
public class MailpitClient {
    
    private final RestClient restClient;
    private final String inboxAddress;

    public MailpitClient(RestClient.Builder builder, InboxProperties props) {
        this.restClient = builder.baseUrl(props.baseUrl()).build();
        this.inboxAddress = props.address();
    }

    /**
     * List the unread messages addressed to the support mailbox (newest first).
     * The query is scoped to mail sent to the support address and
     * not from it, so the agent's own outbound replies — which Mailpit
     * also captures — are never picked back up and reprocessed.
     */

    public List<MailpitMessageSummary> listUnread(int limit) {
        String query = "is:unread to:%s !from:%s".formatted(inboxAddress, inboxAddress);
        MailpitMessageResponse response = restClient.get()
                .uri(uri -> uri.path("/api/v1/search")
                        .queryParam("query", query)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(MailpitMessageResponse.class);
        return response != null ? response.messages() : List.of();
    }

    /**
     * Fetch a full message by id. Side effect: Mailpit marks the message as read.
     */
    public MailpitMessage getMessage(String id) {
        return restClient.get()
                .uri("/api/v1/message/{id}", id)
                .retrieve()
                .body(MailpitMessage.class);
    }

    /**
     * Set the read flag for a single message. Used to put a message back to
     * unread when processing fails, so it is retried on the next poll.
     */
    public void setRead(String id, boolean read) {
        restClient.put()
                .uri("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReadUpdate(List.of(id), read))
                .retrieve()
                .toBodilessEntity();
    }

    /** Request body for {PUT /api/v1/messages}. */
    private record ReadUpdate(
            @JsonProperty("IDs") List<String> ids,
            @JsonProperty("Read") boolean read
    ) {
    }
}
