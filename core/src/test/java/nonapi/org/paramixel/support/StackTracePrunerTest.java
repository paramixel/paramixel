/*
 * Copyright (c) 2026-present Douglas Hoard
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

package nonapi.org.paramixel.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StackTracePruner")
class StackTracePrunerTest {

    @Nested
    @DisplayName("prune")
    class Prune {

        @Test
        @DisplayName("does nothing for null input")
        void doesNothingForNullInput() {
            StackTracePruner.prune(null);
        }

        @Test
        @DisplayName("does nothing for empty stack trace")
        void doesNothingForEmptyStackTrace() {
            var exception = new RuntimeException("test");
            exception.setStackTrace(new StackTraceElement[0]);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace()).isEmpty();
        }

        @Test
        @DisplayName("keeps all frames when none are framework")
        void keepsAllFramesWhenNoneAreFramework() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("java.lang.Thread", "run", 833),
                frame("com.example.Main", "main", 12),
                frame("com.example.MyTest", "myMethod", 25)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace()).containsExactly(frames);
        }

        @Test
        @DisplayName("removes nonapi.org.paramixel frames")
        void removesNonapiOrgParamixelFrames() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("nonapi.org.paramixel.Scheduler", "runStep", 598), frame("com.example.MyTest", "myMethod", 25)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace()).containsExactly(frame("com.example.MyTest", "myMethod", 25));
        }

        @Test
        @DisplayName("removes org.paramixel frames")
        void removesOrgParamixelFrames() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("org.paramixel.api.action.Step", "throwableConsumer", 68),
                frame("com.example.MyTest", "myMethod", 25)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace()).containsExactly(frame("com.example.MyTest", "myMethod", 25));
        }

        @Test
        @DisplayName("removes both framework prefix types")
        void removesBothFrameworkPrefixTypes() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("nonapi.org.paramixel.Scheduler", "runStep", 598),
                frame("org.paramixel.api.action.Step", "throwableConsumer", 68),
                frame("com.example.MyTest", "myMethod", 25),
                frame("java.lang.RuntimeException", "<init>", 64)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace())
                    .containsExactly(
                            frame("com.example.MyTest", "myMethod", 25),
                            frame("java.lang.RuntimeException", "<init>", 64));
        }

        @Test
        @DisplayName("preserves stack trace order")
        void preservesStackTraceOrder() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("com.example.MyTest", "helper", 42),
                frame("com.example.MyTest", "myMethod", 25),
                frame("nonapi.org.paramixel.Scheduler", "runStep", 598),
                frame("nonapi.org.paramixel.ConcreteRunner", "runInternal", 80),
                frame("com.example.__ParamixelRunner__", "main", 15),
                frame("java.lang.Thread", "run", 833)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace())
                    .containsExactly(
                            frame("com.example.MyTest", "helper", 42),
                            frame("com.example.MyTest", "myMethod", 25),
                            frame("com.example.__ParamixelRunner__", "main", 15),
                            frame("java.lang.Thread", "run", 833));
        }

        @Test
        @DisplayName("keeps original when all frames are framework")
        void keepsOriginalWhenAllFramesAreFramework() {
            var exception = new RuntimeException("test");
            StackTraceElement[] frames = {
                frame("nonapi.org.paramixel.Scheduler", "runStep", 598),
                frame("nonapi.org.paramixel.ConcreteRunner", "runInternal", 80)
            };
            exception.setStackTrace(frames);
            StackTracePruner.prune(exception);
            assertThat(exception.getStackTrace()).containsExactly(frames);
        }
    }

    private static StackTraceElement frame(final String className, final String methodName, final int lineNumber) {
        return new StackTraceElement(className, methodName, "TestFile.java", lineNumber);
    }
}
