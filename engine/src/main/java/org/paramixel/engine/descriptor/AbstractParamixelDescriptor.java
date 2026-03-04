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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;

/**
 * Abstract base class for Paramixel test descriptors.
 *
 * <p>This class provides common functionality for all Paramixel descriptor types,
 * including the unique ID, display name, and type. It extends the JUnit Platform's
 * {@link TestDescriptor} to integrate with the JUnit test execution infrastructure.</p>
 *
 * @see ParamixelTestClassDescriptor
 * @see ParamixelTestMethodDescriptor
 * @see ParamixelInvocationDescriptor
 * @author Douglas Hoard <doug.hoard@gmail.com>
 * @since 0.0.1
 */
public abstract class AbstractParamixelDescriptor implements TestDescriptor {

    /**
     * Unique identifier for this descriptor.
     *
     * @since 0.0.1
     */
    private final UniqueId uniqueId;

    /**
     * Human-readable display name used in reports.
     *
     * @since 0.0.1
     */
    private final String displayName;

    /**
     * Descriptor type indicating container or test.
     *
     * @since 0.0.1
     */
    private final Type type;

    /**
     * Parent descriptor in the hierarchy, if any.
     *
     * @since 0.0.1
     */
    private TestDescriptor parent;

    /**
     * Mutable list of child descriptors.
     *
     * @since 0.0.1
     */
    private final List<TestDescriptor> children = new ArrayList<>(10);

    /**
     * Creates a new instance.
     *
     * @param uniqueId the uniqueId
     * @param displayName the displayName
     * @param type the type
     * @since 0.0.1
     */
    protected AbstractParamixelDescriptor(
            final @NonNull UniqueId uniqueId, final @NonNull String displayName, final @NonNull Type type) {
        this.uniqueId = uniqueId;
        this.displayName = displayName;
        this.type = type;
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
        return type;
    }

    @Override
    public Optional<TestDescriptor> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public void setParent(final @NonNull TestDescriptor parent) {
        this.parent = parent;
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.empty();
    }

    @Override
    public Set<TestTag> getTags() {
        return Collections.emptySet();
    }

    @Override
    public Set<? extends TestDescriptor> getChildren() {
        return new LinkedHashSet<>(children);
    }

    @Override
    public void addChild(final @NonNull TestDescriptor child) {
        children.add(child);
        child.setParent(this);
    }

    @Override
    public void removeChild(final @NonNull TestDescriptor child) {
        children.remove(child);
    }

    @Override
    public void removeFromHierarchy() {
        if (parent != null) {
            parent.removeChild(this);
            parent = null;
        }
    }

    @Override
    public Optional<? extends TestDescriptor> findByUniqueId(final @NonNull UniqueId uniqueId) {
        if (getUniqueId().equals(uniqueId)) {
            return Optional.of(this);
        }
        for (TestDescriptor child : children) {
            final Optional<? extends TestDescriptor> found = child.findByUniqueId(uniqueId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "AbstractParamixelDescriptor{" + "uniqueId="
                + uniqueId + ", displayName='"
                + displayName + '\'' + ", type="
                + type + '}';
    }
}
