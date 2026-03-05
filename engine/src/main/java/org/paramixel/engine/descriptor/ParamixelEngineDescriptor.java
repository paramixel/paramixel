/*
 * Copyright 2026-present Douglas Hoard. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.paramixel.engine.descriptor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;

/**
 * Root engine descriptor for Paramixel.
 *
 * <p>This descriptor represents the root of the Paramixel test hierarchy and owns
 * all discovered test class descriptors.</p>
 *
 * @author Douglas Hoard (doug.hoard@gmail.com)
 * @since 0.0.1
 */
public final class ParamixelEngineDescriptor implements TestDescriptor {

    /**
     * Unique identifier for the engine descriptor.
     */
    private final UniqueId uniqueId;

    /**
     * Display name used in reports and IDEs.
     */
    private final String displayName;

    /**
     * Child descriptors discovered for this engine.
     */
    private final Set<TestDescriptor> children = new LinkedHashSet<>();

    /**
     * Creates a new engine descriptor.
     *
     * @param uniqueId the unique ID for the engine descriptor
     * @param displayName the display name for the engine
     * @since 0.0.1
     */
    public ParamixelEngineDescriptor(final @NonNull UniqueId uniqueId, final @NonNull String displayName) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
    }

    @Override
    public UniqueId getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.empty();
    }

    @Override
    public Optional<TestDescriptor> getParent() {
        return Optional.empty();
    }

    @Override
    public void setParent(final TestDescriptor parent) {
        // INTENTIONALLY EMPTY
    }

    @Override
    public Set<? extends TestDescriptor> getChildren() {
        return children;
    }

    @Override
    public void addChild(final @NonNull TestDescriptor child) {
        children.add(child);
    }

    @Override
    public void removeChild(final @NonNull TestDescriptor child) {
        children.remove(child);
    }

    @Override
    public void removeFromHierarchy() {
        children.clear();
    }

    @Override
    public Optional<? extends TestDescriptor> findByUniqueId(final @NonNull UniqueId uniqueId) {
        if (getUniqueId().equals(uniqueId)) {
            return Optional.of(this);
        }
        for (TestDescriptor child : children) {
            Optional<? extends TestDescriptor> found = child.findByUniqueId(uniqueId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<TestTag> getTags() {
        return Collections.emptySet();
    }
}
