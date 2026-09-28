package com.supportAgentMcpClient.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.supportAgentMcpClient.config.EmailHandler;
import com.supportAgentMcpClient.config.InboxProperties;
import com.supportAgentMcpClient.config.MailpitClient;
import com.supportAgentMcpClient.dto.MailpitAddress;
import com.supportAgentMcpClient.dto.MailpitMessage;
import com.supportAgentMcpClient.dto.MailpitMessageSummary;
import com.supportAgentMcpClient.model.IncomingEmail;

/**
 * Polls the Mailpit inbox on a fixed interval, turns every unread message into
 * an {@link IncomingEmail}, and forwards it to the configured
 * {@link EmailHandler}.
 * <p>
 * Read/unread state lives on the Mailpit server: fetching a message marks it
 * read, which is how we avoid reprocessing. If the handler reports failure, the
 * message is flipped back to unread so the next poll retries it.
 */

@Service  
public class InboxMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(InboxMonitor.class);

    private final MailpitClient mailpitClient;
    private final EmailHandler emailHandler;
    private final InboxProperties props;

    InboxMonitor(MailpitClient mailpitClient, EmailHandler emailHandler, InboxProperties props){
        this.mailpitClient = mailpitClient;
        this.emailHandler = emailHandler;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${support-agent.inbox.poll-interval:10000}")
    public void poll(){
        try {
            List<MailpitMessageSummary> unread = mailpitClient.listUnread(props.batchSize());
            if (unread.isEmpty()) {
                logger.debug("No new mail");
                return;
            }
            logger.info("Found {} new message(s)", unread.size());
            for (MailpitMessageSummary summary : unread) {
                processOne(summary.id());
            }
        } catch (Exception e) {
            // Mailpit may be starting up or briefly unreachable. Log and let the
            // next scheduled poll retry rather than killing the scheduler.
            logger.warn("Inbox poll failed: {}", e.getMessage(), e);
        }
    }

    private void processOne(String id) {
        try {
            // Fetching the full message marks it read on the server.
            MailpitMessage message = mailpitClient.getMessage(id);
            IncomingEmail email = toIncomingEmail(message);
            boolean handled = emailHandler.handle(email);
            if (!handled) {
                // Leave it unread so the next poll picks it up again.
                mailpitClient.setRead(id, false);
            }
        } catch (Exception e) {
            logger.error("Failed to process message {}; resetting to unread for retry", id, e);
            try {
                mailpitClient.setRead(id, false);
            } catch (Exception reset) {
                logger.warn("Could not reset message {} to unread: {}", id, reset.getMessage());
            }
        }
    }

    private IncomingEmail toIncomingEmail(MailpitMessage message) {
        String from = message.from() != null ? message.from().address() : "(unknown)";
        List<String> to = message.to().stream()
                .map(MailpitAddress::address)
                .toList();
        String subject = message.subject() != null ? message.subject() : "";
        String body = bestBody(message);
        Instant receivedAt = parseDate(message.date());
        return new IncomingEmail(message.messageId(), from, to, subject, body, receivedAt);
    }

    /** Prefer the plain-text body; fall back to HTML if that is all there is. */
    private String bestBody(MailpitMessage message) {
        if (message.text() != null && !message.text().isBlank()) {
            return message.text().strip();
        }
        return message.html() != null ? message.html().strip() : "";
    }

    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(date).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(date);
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }


}
