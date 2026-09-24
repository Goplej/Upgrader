package com.upgrader.integration;

import com.upgrader.value.source.ValueSource;

import java.util.Optional;

/**
 * One optional third-party mod adapter (ProjectE, Avaritia, Re:Avaritia, ...).
 *
 * <h2>Contract</h2>
 * <ol>
 *   <li>{@link #isAvailable()} must answer without touching any class of the target mod —
 *       typically {@code ModList.get().isLoaded(modId())}.</li>
 *   <li>{@link #init()} runs once on the main thread during common setup, only after
 *       {@code isAvailable()} returned true. It may cache method handles reflectively. Any
 *       {@link Throwable} raised here disables the integration permanently (logged ERROR by
 *       {@link IntegrationManager}); Upgrader itself must keep working.</li>
 *   <li>{@link #asValueSource()} returns the priority-0 evidence producer, or empty when the
 *       adapter exposes no direct values.</li>
 * </ol>
 *
 * <p>Addons implement this interface and hand it to
 * {@link com.upgrader.api.UpgraderAPI#registerIntegration(IntegrationProvider)}.</p>
 */
public interface IntegrationProvider {

    /** Mod id this provider binds to, e.g. {@code "projecte"}. Must not be null/blank. */
    String modId();

    /** Human-readable label used in breakdown explanations. */
    default String displayName() {
        return modId();
    }

    /** Cheap presence check that never class-loads the target mod. */
    boolean isAvailable();

    /** One-time reflective/API bootstrap. Exceptions are caught by the manager. */
    void init() throws Exception;

    /** Evidence producer contributed to the value chain, if any. */
    default Optional<ValueSource> asValueSource() {
        return Optional.empty();
    }

    /** True after a successful {@link #init()}; managed externally via {@link IntegrationManager}. */
    default boolean initialized() {
        return true;
    }
}
