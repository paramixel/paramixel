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

package org.paramixel.api;

/**
 * How a task's direct children are executed.
 *
 * <p>{@code SEQUENTIAL} children run one at a time in resolved order. {@code PARALLEL} children are eligible
 * concurrently and each holds one permit of the task's {@link Pool}.
 */
public enum ExecutionMode {

    /** Direct children run one at a time, in resolved order. */
    SEQUENTIAL,

    /** Direct children may run concurrently, bounded by the task's {@link Pool}. */
    PARALLEL
}
