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

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates RFC 9562 UUID version 4 strings backed by {@link ThreadLocalRandom}
 * rather than {@link java.security.SecureRandom SecureRandom}.
 *
 * <p>Use this class when standard UUID format is required for interoperability
 * with external systems and the generation rate is high enough that
 * {@link UUID#randomUUID() UUID.randomUUID()} becomes a bottleneck. The output
 * is format-identical to a version&nbsp;4 UUID — only the entropy source differs.
 *
 * <p><strong>This is not suitable for security-sensitive contexts</strong> such
 * as password-reset tokens, session identifiers, or cryptographic nonces; use
 * {@link UUID#randomUUID()} or a {@link java.security.SecureRandom SecureRandom}-based
 * approach for those cases instead.
 *
 * <p>This class has a private constructor and is not instantiable.
 * The {@link #generateUuid()} method is thread-safe.
 */
public class FastUUID {

    private FastUUID() {
        // Intentionally empty
    }

    /**
     * Produces a randomly generated UUID version 4 string in canonical
     * RFC 9562 format ({@code 8-4-4-4-12} hyphen layout).
     *
     * <p>Version&nbsp;4 and variant bits are set per the specification so the
     * result is parseable by {@link UUID#fromString(String)} and interoperable
     * with any RFC&nbsp;9562‑compliant consumer.
     *
     * @return a canonical UUID version 4 string; never {@code null}
     */
    public static String generateUuid() {
        var rng = ThreadLocalRandom.current();
        long msb = rng.nextLong();
        long lsb = rng.nextLong();
        msb = (msb & ~0x0000_0000_0000_F000L) | 0x0000_0000_0000_4000L;
        lsb = (lsb & ~0xC000_0000_0000_0000L) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb).toString();
    }
}
