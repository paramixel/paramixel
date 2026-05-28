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

package nonapi.org.paramixel.listener;

/**
 * Targets for listener output exclusion controlled by the
 * {@link org.paramixel.api.Configuration#LISTENER_EXCLUDE} configuration key.
 */
enum ExcludeTarget {

    /**
     * Exclude per-action "starting" header lines from
     * {@link StatusListener#onBeforeExecution(org.paramixel.api.Descriptor)}.
     */
    STATUS_HEADER,

    /**
     * Exclude per-action completion status lines from
     * {@link StatusListener#onAfterExecution(org.paramixel.api.Descriptor)}.
     */
    STATUS_FOOTER,

    /**
     * Exclude the starting header from {@link SummaryListener#onRunStarted()}.
     */
    SUMMARY_HEADER,

    /**
     * Exclude the rendered action tree from {@link SummaryListener#onRunCompleted(org.paramixel.api.Result)}.
     */
    SUMMARY_TREE,

    /**
     * Exclude the final status/timestamp/total-time footer from {@link SummaryListener#onRunCompleted(org.paramixel.api.Result)}.
     */
    SUMMARY_FOOTER
}
