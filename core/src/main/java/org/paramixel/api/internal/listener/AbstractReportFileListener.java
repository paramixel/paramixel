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

package org.paramixel.api.internal.listener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.paramixel.api.Listener;
import org.paramixel.api.Result;
import org.paramixel.api.internal.support.Arguments;
import org.paramixel.api.internal.support.TildePathExpander;

/**
 * Base class for listeners that write an end-of-run report to a file.
 */
abstract class AbstractReportFileListener implements Listener {

    private final Path reportFile;

    AbstractReportFileListener(final String reportFile) {
        Objects.requireNonNull(reportFile, "reportFile must not be null");
        Arguments.requireNonBlank(reportFile, "reportFile must not be blank");
        this.reportFile = TildePathExpander.expand(reportFile);
    }

    @Override
    public final void onRunCompleted(final Result result) {
        try {
            final var parent = reportFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                writeReport(writer, result);
            }
        } catch (IOException e) {
            final var name = formatName();
            final var qualifier = name.isBlank() ? "" : name + " ";
            throw new UncheckedIOException("Unable to write " + qualifier + "report file: " + reportFile, e);
        }
    }

    /**
     * Writes the report content.
     *
     * @param writer the output writer
     * @param result the run result; never {@code null}; the descriptor may be absent
     * @throws IOException if writing fails
     */
    protected abstract void writeReport(Writer writer, Result result) throws IOException;

    /**
     * Returns the report format name for error messages.
     *
     * @return the format name, or empty string if unnamed
     */
    protected abstract String formatName();
}
