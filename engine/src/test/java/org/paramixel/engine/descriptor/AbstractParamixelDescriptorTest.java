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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor.Type;
import org.junit.platform.engine.UniqueId;

public class AbstractParamixelDescriptorTest {

    @Test
    public void addChild_setsParent_andFindByUniqueIdSearchesRecursively() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final DummyDescriptor root = new DummyDescriptor(rootId, "root", Type.CONTAINER);
        final DummyDescriptor child = new DummyDescriptor(rootId.append("c", "1"), "child", Type.CONTAINER);
        final DummyDescriptor grandChild =
                new DummyDescriptor(rootId.append("c", "1").append("g", "2"), "gc", Type.TEST);

        root.addChild(child);
        child.addChild(grandChild);

        assertThat(child.getParent()).contains(root);
        assertThat(grandChild.getParent()).contains(child);

        assertThat(root.findByUniqueId(grandChild.getUniqueId()))
                .hasValueSatisfying(found -> assertThat(found).isSameAs(grandChild));
        assertThat(root.findByUniqueId(root.getUniqueId()))
                .hasValueSatisfying(found -> assertThat(found).isSameAs(root));
    }

    @Test
    public void getChildren_returnsCopy_notBackedByInternalList() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final DummyDescriptor root = new DummyDescriptor(rootId, "root", Type.CONTAINER);
        final DummyDescriptor child = new DummyDescriptor(rootId.append("c", "1"), "child", Type.TEST);
        root.addChild(child);

        final var children = root.getChildren();
        assertThat(children).anySatisfy(d -> assertThat(d).isSameAs(child));
        children.clear();

        assertThat(root.getChildren()).anySatisfy(d -> assertThat(d).isSameAs(child));
    }

    @Test
    public void removeFromHierarchy_detachesFromParent() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final DummyDescriptor root = new DummyDescriptor(rootId, "root", Type.CONTAINER);
        final DummyDescriptor child = new DummyDescriptor(rootId.append("c", "1"), "child", Type.TEST);
        root.addChild(child);

        child.removeFromHierarchy();

        assertThat(child.getParent()).isEmpty();
        assertThat(root.getChildren()).noneMatch(d -> d == child);

        assertThat(root.findByUniqueId(child.getUniqueId())).isEqualTo(Optional.empty());
    }

    @Test
    public void toString_includesUniqueIdDisplayNameAndType() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final DummyDescriptor root = new DummyDescriptor(rootId, "root", Type.CONTAINER);

        assertThat(root.toString())
                .contains("uniqueId=")
                .contains("displayName='root'")
                .contains("type=");
    }

    @Test
    public void accessors_returnConfiguredType_andEmptyTagsAndSource() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final DummyDescriptor root = new DummyDescriptor(rootId, "root", Type.CONTAINER);

        assertThat(root.getType()).isEqualTo(Type.CONTAINER);
        assertThat(root.getTags()).isEmpty();
        assertThat(root.getSource()).isEmpty();
    }

    private static final class DummyDescriptor extends AbstractParamixelDescriptor {

        private DummyDescriptor(final UniqueId uniqueId, final String displayName, final Type type) {
            super(uniqueId, displayName, type);
        }
    }
}
