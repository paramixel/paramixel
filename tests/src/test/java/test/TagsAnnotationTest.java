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

package test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.paramixel.api.ArgumentContext;
import org.paramixel.api.ArgumentsCollector;
import org.paramixel.api.ClassContext;
import org.paramixel.api.Paramixel;

@Paramixel.TestClass
@Paramixel.Tags({"integration", "database", "slow"})
/**
 * Verifies that the @Paramixel.Tags annotation is properly recognized and accessible.
 *
 * <p>This test demonstrates the use of tags to categorize test classes. The tags are
 * validated during discovery to ensure proper usage constraints.</p>
 */
public class TagsAnnotationTest {

    private static final AtomicInteger testCount = new AtomicInteger(0);

    @Paramixel.ArgumentsCollector
    public static void arguments(final @NonNull ArgumentsCollector collector) {
        collector.addArgument("test");
    }

    @Paramixel.Test
    public void testWithTags(final @NonNull ArgumentContext context) {
        testCount.incrementAndGet();
        assertThat(context.getArgument()).isNotNull();
    }

    @Paramixel.Finalize
    public void finalize(final ClassContext context) {
        assertThat(testCount.get()).isEqualTo(1);
    }
}
