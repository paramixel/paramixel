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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/** A mapping from {@link TypeVariable} to resolved {@link Type}. */
class TypeResolutions {

    /** The type variables. */
    private final TypeVariable<?>[] typeVariables;

    /** The resolved type arguments. */
    Type[] resolvedTypeArguments;

    /**
     * Produce a list of type variable resolutions from a resolved type, by comparing its actual type parameters
     * with the generic (declared) parameters of its generic type.
     *
     * @param resolvedType
     *            the resolved type
     */
    TypeResolutions(final ParameterizedType resolvedType) {
        typeVariables = ((Class<?>) resolvedType.getRawType()).getTypeParameters();
        resolvedTypeArguments = resolvedType.getActualTypeArguments();
        if (resolvedTypeArguments.length != typeVariables.length) {
            throw new IllegalArgumentException("Type parameter count mismatch");
        }
    }

    /**
     * Resolve the type variables in a type using a type variable resolution list, producing a resolved type.
     *
     * @param type
     *            the type
     * @return the resolved type
     */
    Type resolveTypeVariables(final Type type) {
        if (type instanceof Class<?>) {
            // Arrays and non-generic classes have no type variables
            return type;

        } else if (type instanceof ParameterizedType) {
            // Recursively resolve parameterized types
            final ParameterizedType parameterizedType = (ParameterizedType) type;
            final Type[] typeArgs = parameterizedType.getActualTypeArguments();
            Type[] typeArgsResolved = null;
            for (int i = 0; i < typeArgs.length; i++) {
                // Recursively revolve each parameter of the type
                final Type typeArgResolved = resolveTypeVariables(typeArgs[i]);
                // Only compare typeArgs to typeArgResolved until the first difference is found
                if (typeArgsResolved == null) {
                    if (!typeArgResolved.equals(typeArgs[i])) {
                        // After the first difference is found, lazily allocate typeArgsResolved
                        typeArgsResolved = new Type[typeArgs.length];
                        // Go back and copy all the previous args
                        System.arraycopy(typeArgs, 0, typeArgsResolved, 0, i);
                        // Insert the first different arg
                        typeArgsResolved[i] = typeArgResolved;
                    }
                } else {
                    // After the first difference is found, keep copying the resolved args into the array
                    typeArgsResolved[i] = typeArgResolved;
                }
            }
            if (typeArgsResolved == null) {
                // There were no type parameters to resolve
                return type;
            } else {
                // Return new ParameterizedType that wraps the resolved type args
                return new ParameterizedTypeImpl(
                        (Class<?>) parameterizedType.getRawType(), typeArgsResolved, parameterizedType.getOwnerType());
            }

        } else if (type instanceof TypeVariable<?>) {
            // Look up concrete type for type variable
            final TypeVariable<?> typeVariable = (TypeVariable<?>) type;
            for (int i = 0; i < typeVariables.length; i++) {
                if (typeVariables[i].getName().equals(typeVariable.getName())) {
                    return resolvedTypeArguments[i];
                }
            }
            // Could not resolve type variable
            return type;

        } else if (type instanceof GenericArrayType) {
            // Count the array dimensions, and resolve the innermost type of the array
            int numArrayDims = 0;
            Type t = type;
            while (t instanceof GenericArrayType) {
                numArrayDims++;
                t = ((GenericArrayType) t).getGenericComponentType();
            }
            final Type innermostType = t;
            final Type innermostTypeResolved = resolveTypeVariables(innermostType);
            if (!(innermostTypeResolved instanceof Class<?>)) {
                throw new IllegalArgumentException("Could not resolve generic array type " + type);
            }
            final Class<?> innermostTypeResolvedClass = (Class<?>) innermostTypeResolved;

            // Build an array to hold the size of each dimension, filled with zeroes
            final int[] dims = (int[]) Array.newInstance(int.class, numArrayDims);

            // Build a zero-sized array of the required number of dimensions, using the resolved innermost class
            final Object arrayInstance = Array.newInstance(innermostTypeResolvedClass, dims);

            // Get the class of this array instance -- this is the resolved array type
            return arrayInstance.getClass();

        } else if (type instanceof WildcardType) {
            // TODO: Support WildcardType
            throw new RuntimeException("WildcardType not yet supported: " + type);

        } else {
            throw new RuntimeException("Got unexpected type: " + type);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (typeVariables.length == 0) {
            return "{ }";
        } else {
            final StringBuilder buf = new StringBuilder();
            buf.append("{ ");
            for (int i = 0; i < typeVariables.length; i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                buf.append(typeVariables[i]).append(" => ").append(resolvedTypeArguments[i]);
            }
            buf.append(" }");
            return buf.toString();
        }
    }
}
