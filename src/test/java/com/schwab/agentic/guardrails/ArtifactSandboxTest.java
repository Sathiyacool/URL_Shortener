package com.schwab.agentic.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactSandboxTest {

    @TempDir
    Path tempRoot;

    @Test
    void resolvesOnlyPathsUnderRoot() throws IOException {
        ArtifactSandbox sandbox = new ArtifactSandbox(sandboxRoot().toString());

        Path resolved = sandbox.resolve("reports\\summary.txt");

        assertTrue(resolved.startsWith(sandboxRoot().toRealPath()));
    }

    @Test
    void rejectsAbsolutePaths() {
        ArtifactSandbox sandbox = new ArtifactSandbox(sandboxRoot().toString());

        assertThrows(
                GuardrailViolationException.class,
                () -> sandbox.resolve(sandboxRoot().resolve("outside.txt").toString()));
    }

    @Test
    void rejectsSymlinkThatTargetsOutsideSandbox() throws IOException {
        Path sandboxRoot = sandboxRoot();
        ArtifactSandbox sandbox = new ArtifactSandbox(sandboxRoot.toString());
        Path outside = Files.createDirectory(tempRoot.resolve("outside"));
        Path link = sandboxRoot.resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception);
        }

        assertThrows(GuardrailViolationException.class, () -> sandbox.resolve("escape\\artifact.txt"));
    }

    @Test
    void writesVersionedArtifactsWithCreateNewSemantics() throws IOException {
        ArtifactSandbox sandbox = new ArtifactSandbox(sandboxRoot().toString());
        UUID runId = UUID.randomUUID();

        sandbox.writeVersioned(runId, 1, "docs\\summary.md", "version one");

        Path artifact = sandbox.resolveVersionedPath(runId, 1, "docs\\summary.md");
        assertEquals("version one", Files.readString(artifact));
        assertThrows(
                ArtifactWriteException.class,
                () -> sandbox.writeVersioned(runId, 1, "docs\\summary.md", "overwrite"));
    }

    private Path sandboxRoot() {
        return tempRoot.resolve("sandbox");
    }
}
