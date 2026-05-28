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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import org.paramixel.api.Descriptor;
import org.paramixel.api.Result;

/**
 * Writes a plain-text descriptor report.
 */
public final class ReportListener extends AbstractReportFileListener {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Creates a report listener.
     */
    public ReportListener() {}

    @Override
    protected void writeReport(final Writer writer, final Result result) throws IOException {
        var descriptor = result.descriptor().orElse(null);
        if (descriptor == null) {
            writer.write("No Paramixel tests found");
            writer.write(LINE_SEPARATOR);
            return;
        }
        write(writer, descriptor, 0);
    }

    @Override
    protected String formatName() {
        return "text";
    }

    private static void write(final Writer writer, final Descriptor descriptor, final int depth) throws IOException {
        var metadata = descriptor.metadata();
        writer.write("  ".repeat(depth));
        writer.write(metadata.status().name());
        writer.write(" | ");
        writer.write(metadata.name());
        var message = metadata.message();
        if (message.isPresent()) {
            writer.write(" | ");
            writer.write(Listeners.sanitizeMessage(message.orElseThrow()));
        }
        writer.write(LINE_SEPARATOR);
        descriptor.before().ifPresent(b -> {
            try {
                write(writer, b, depth + 1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        for (Descriptor child : descriptor.children()) {
            write(writer, child, depth + 1);
        }
        descriptor.after().ifPresent(a -> {
            try {
                write(writer, a, depth + 1);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
