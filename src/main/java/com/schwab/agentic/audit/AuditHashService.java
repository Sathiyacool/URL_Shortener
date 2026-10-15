package com.schwab.agentic.audit;

import com.schwab.agentic.orchestration.domain.ExecutionEventEntity;
import com.schwab.agentic.orchestration.domain.ExecutionRunEntity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class AuditHashService {

    public static final String GENESIS_HASH = ExecutionRunEntity.INITIAL_AUDIT_HASH;

    public String hash(ExecutionEventEntity event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateLong(digest, event.getSequenceNumber());
            update(digest, event.getPreviousHash());
            update(digest, event.getRunId().toString());
            update(digest, event.getTaskId());
            update(digest, event.getEventType().name());
            update(digest, event.getActorType().name());
            update(digest, event.getActorReference());
            update(digest, event.getPreviousValue());
            update(digest, event.getNewValue());
            update(digest, event.getDetails());
            update(digest, event.getOccurredAt().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        updateLong(digest, bytes.length);
        digest.update(bytes);
    }

    private void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
