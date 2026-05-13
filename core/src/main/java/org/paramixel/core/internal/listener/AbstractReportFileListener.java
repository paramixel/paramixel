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

package org.paramixel.core.internal.listener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.paramixel.core.Listener;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.internal.TildePathExpander;
import org.paramixel.core.support.Arguments;

/**
 * Creates report output files with parent-directory creation, UTF-8 writer lifecycle management, and {@link UncheckedIOException} wrapping on I/O failure.
 * Subclasses implement {@code writeReport} to produce report content and {@code formatName} to identify the
 * report format in error messages.
 */
abstract class AbstractReportFileListener implements Listener {

    private final Path reportFile;

    AbstractReportFileListener(String reportFile) {
        Objects.requireNonNull(reportFile, "reportFile must not be null");
        Arguments.requireNonBlank(reportFile, "reportFile must not be blank");
        this.reportFile = TildePathExpander.expand(reportFile);
    }

    /**
     * Creates parent directories, opens a UTF-8 buffered writer, delegates to {@code writeReport}, and wraps
     * {@link java.io.IOException} in {@link java.io.UncheckedIOException} on failure.
     */
    @Override
    public final void runCompleted(Runner runner, Result result) {
        Objects.requireNonNull(runner, "runner must not be null");
        Objects.requireNonNull(result, "result must not be null");
        try {
            Path parent = reportFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                writeReport(writer, runner, result);
            }
        } catch (IOException e) {
            String name = formatName();
            String qualifier = name.isEmpty() ? "" : name + " ";
            throw new UncheckedIOException("Unable to write " + qualifier + "report file: " + reportFile, e);
        }
    }

    /**
     * Writes the report content to the supplied writer. The writer is opened and closed by the base class;
     * implementations must not close it.
     *
     * @param writer the writer for report output
     * @param runner the runner that completed
     * @param result the root result of the run
     * @throws IOException if writing fails
     */
    protected abstract void writeReport(Writer writer, Runner runner, Result result) throws IOException;

    /**
     * Returns the report format name used in error messages. Return an empty string to omit the format qualifier
     * from error messages.
     *
     * @return the format name, or an empty string for no qualifier
     */
    protected abstract String formatName();
}
