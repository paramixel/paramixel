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

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An implementation of {@link ParameterizedType}, used to replace type variables with concrete types. */
class ParameterizedTypeImpl implements ParameterizedType {

    /** The actual type arguments. */
    private final Type[] actualTypeArguments;

    /** The raw type. */
    private final Class<?> rawType;

    /** The owner type. */
    private final Type ownerType;

    /** The type parameters of {@link Map} instances of unknown generic type. */
    public static final Type MAP_OF_UNKNOWN_TYPE =
            new ParameterizedTypeImpl(Map.class, new Type[] {Object.class, Object.class}, null);

    /** The type parameter of {@link List} instances of unknown generic type. */
    public static final Type LIST_OF_UNKNOWN_TYPE =
            new ParameterizedTypeImpl(List.class, new Type[] {Object.class}, null);

    /**
     * Constructor.
     *
     * @param rawType
     *            the raw type
     * @param actualTypeArguments
     *            the actual type arguments
     * @param ownerType
     *            the owner type
     */
    ParameterizedTypeImpl(final Class<?> rawType, final Type[] actualTypeArguments, final Type ownerType) {
        this.actualTypeArguments = actualTypeArguments;
        this.rawType = rawType;
        this.ownerType = (ownerType != null) ? ownerType : rawType.getDeclaringClass();
        if (rawType.getTypeParameters().length != actualTypeArguments.length) {
            throw new IllegalArgumentException("Argument length mismatch");
        }
    }

    /* (non-Javadoc)
     * @see java.lang.reflect.ParameterizedType#getActualTypeArguments()
     */
    @Override
    public Type[] getActualTypeArguments() {
        return actualTypeArguments.clone();
    }

    /* (non-Javadoc)
     * @see java.lang.reflect.ParameterizedType#getRawType()
     */
    @Override
    public Class<?> getRawType() {
        return rawType;
    }

    /* (non-Javadoc)
     * @see java.lang.reflect.ParameterizedType#getOwnerType()
     */
    @Override
    public Type getOwnerType() {
        return ownerType;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ParameterizedType)) {
            return false;
        }
        final ParameterizedType other = (ParameterizedType) obj;
        return Objects.equals(ownerType, other.getOwnerType())
                && Objects.equals(rawType, other.getRawType())
                && Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        if (ownerType == null) {
            buf.append(rawType.getName());
        } else {
            if (ownerType instanceof Class) {
                buf.append(((Class<?>) ownerType).getName());
            } else {
                buf.append(ownerType);
            }
            buf.append('$');
            if (ownerType instanceof ParameterizedTypeImpl) {
                final String simpleName =
                        rawType.getName().replace(((ParameterizedTypeImpl) ownerType).rawType.getName() + "$", "");
                buf.append(simpleName);
            } else {
                buf.append(rawType.getSimpleName());
            }
        }
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            buf.append('<');
            boolean first = true;
            for (final Type t : actualTypeArguments) {
                if (first) {
                    first = false;
                } else {
                    buf.append(", ");
                }
                buf.append(t.toString());
            }
            buf.append('>');
        }
        return buf.toString();
    }
}
