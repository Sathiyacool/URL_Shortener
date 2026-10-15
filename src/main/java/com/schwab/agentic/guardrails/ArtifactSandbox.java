package com.schwab.agentic.guardrails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.InvalidPathException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArtifactSandbox {

    private final Path root;

    public ArtifactSandbox(@Value("${artifact.sandbox.root:${user.dir}/agent-workspace}") String root) {
        try {
            Path configuredRoot = Path.of(root).toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            this.root = configuredRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize artifact sandbox", exception);
        }
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new GuardrailViolationException("Artifact path must not be blank");
        }
        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (InvalidPathException exception) {
            throw new GuardrailViolationException("Artifact path is invalid");
        }
        if (relative.isAbsolute()) {
            throw new GuardrailViolationException("Absolute artifact paths are not allowed");
        }
        for (Path component : relative) {
            if (component.toString().equals("..")) {
                throw new GuardrailViolationException("Artifact path traversal is not allowed");
            }
        }

        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw new GuardrailViolationException("Artifact path escapes the sandbox");
        }
        verifyExistingAncestor(candidate);
        return candidate;
    }

    public Path resolveVersionedPath(UUID runId, int version, String relativePath) {
        if (runId == null || version < 1) {
            throw new GuardrailViolationException("Artifact run and version are required");
        }
        return resolve(Path.of(runId.toString(), Integer.toString(version), relativePath).toString());
    }

    public void writeVersioned(UUID runId, int version, String relativePath, String content) {
        Path target = resolveVersionedPath(runId, version, relativePath);
        try {
            Files.createDirectories(target.getParent());
            target = resolveVersionedPath(runId, version, relativePath);
            Files.writeString(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ArtifactWriteException("Unable to write versioned artifact", exception);
        }
    }

    private void verifyExistingAncestor(Path candidate) {
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new GuardrailViolationException("Artifact path has no existing sandbox ancestor");
        }
        try {
            if (!existing.toRealPath().startsWith(root)) {
                throw new GuardrailViolationException("Artifact path resolves outside the sandbox");
            }
        } catch (IOException exception) {
            throw new GuardrailViolationException("Unable to verify artifact path within the sandbox");
        }
    }
}
