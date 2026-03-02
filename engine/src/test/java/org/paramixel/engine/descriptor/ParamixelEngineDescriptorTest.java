/*
 * Copyright 2006-present Douglas Hoard. All rights reserved.
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

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.UniqueId;

public class ParamixelEngineDescriptorTest {

    @Test
    public void addChild_removeChild_andFindByUniqueId_work() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");

        assertThat(engine.findByUniqueId(rootId))
                .hasValueSatisfying(found -> assertThat(found).isSameAs(engine));

        final ParamixelTestClassDescriptor clazz =
                new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "c");
        engine.addChild(clazz);

        assertThat(engine.getChildren()).anySatisfy(d -> assertThat(d).isSameAs(clazz));
        assertThat(engine.findByUniqueId(clazz.getUniqueId()))
                .hasValueSatisfying(found -> assertThat(found).isSameAs(clazz));

        engine.removeChild(clazz);
        assertThat(engine.getChildren()).noneMatch(d -> d == clazz);
        assertThat(engine.findByUniqueId(clazz.getUniqueId())).isEmpty();
    }

    @Test
    public void removeFromHierarchy_clearsChildren() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");

        engine.addChild(new ParamixelTestClassDescriptor(rootId.append("class", "c"), String.class, "c"));
        assertThat(engine.getChildren()).isNotEmpty();

        engine.removeFromHierarchy();
        assertThat(engine.getChildren()).isEmpty();
    }

    @Test
    public void setParent_isNoOp() {
        final UniqueId rootId = UniqueId.forEngine("paramixel");
        final ParamixelEngineDescriptor engine = new ParamixelEngineDescriptor(rootId, "paramixel");
        engine.setParent(engine);
        assertThat(engine.getParent()).isEmpty();
    }
}
