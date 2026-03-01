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

package test.argument;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
public class CollectionOfSameObjectsArgumentTest {

    @Paramixel.ArgumentSupplier
    public static Collection<Object> arguments() {
        Collection<Object> collection = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            collection.add("test" + i);
        }
        return collection;
    }

    @Paramixel.Test
    public void test(final @NonNull ArgumentContext argumentContext) {
        assertThat(argumentContext).isNotNull();
        assertThat(argumentContext.getStore()).isNotNull();
        assertThat(argumentContext.getArgument()).isInstanceOf(String.class);
        assertThat((String) argumentContext.getArgument()).startsWith("test");
    }
}
