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

/**
 * Paramixel: define an immutable task tree, let the framework validate and compile it, then execute it.
 *
 * <p>Only the {@code org.paramixel.api} packages are exported. Everything under {@code nonapi} is framework
 * internal: on the module path it is unreachable, and on the classpath it is internal by convention only.
 */
module org.paramixel.api {
    requires java.logging;
    requires java.xml;

    exports org.paramixel.api;
    exports org.paramixel.api.exception;
    exports org.paramixel.api.selector;
    exports org.paramixel.api.support;
}
