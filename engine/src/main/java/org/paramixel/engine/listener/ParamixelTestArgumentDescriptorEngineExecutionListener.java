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

package org.paramixel.engine.listener;

import org.jspecify.annotations.NonNull;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

/**
 * Reports execution events for argument descriptors.
 *
 * <p>This listener prints a single line on start and finish using the class and argument display
 * names.
 *
 * @author Douglas Hoard
 */
public class ParamixelTestArgumentDescriptorEngineExecutionListener extends AbstractEngineExecutionListener {

    @Override
    public void executionStarted(final @NonNull TestDescriptor testDescriptor) {
        String threadName = Thread.currentThread().getName();
        String displayName = getDisplayName(2, testDescriptor);
        System.out.println(INFO + " " + TEST + " | " + threadName + " | " + displayName);
    }

    @Override
    public void executionFinished(
            final @NonNull TestDescriptor testDescriptor, final @NonNull TestExecutionResult testExecutionResult) {
        String threadName = Thread.currentThread().getName();
        String displayName = getDisplayName(2, testDescriptor);
        String message = getStatusMessage(testExecutionResult, threadName, displayName);
        System.out.println(message);
    }
}
