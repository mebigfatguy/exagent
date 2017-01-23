/*
 * baremetal4j - A java aspect for allowing debugging at the byte code level from source debuggers (as in IDEs)
 * Copyright 2016 MeBigFatGuy.com
 * Copyright 2016 Dave Brosius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.mebigfatguy.exagent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * an automatic toString() builder using reflection
 */
public class ToString {

    private static class VisitedInfo {
        Set<Integer> visited = new HashSet<>();
        int count = 0;
    }

    private static final ThreadLocal<VisitedInfo> visited = new ThreadLocal<VisitedInfo>() {

        @Override
        protected VisitedInfo initialValue() {
            return new VisitedInfo();
        }
    };

    private ToString() {
    }

    public static String build(Object o) {
        VisitedInfo vi = visited.get();
        try {
            vi.count++;
            return generate(o, vi.visited);
        } finally {
            if (--vi.count == 0) {
                vi.visited.clear();
            }
        }
    }

    private static String generate(Object o, Set<Integer> visitedObjects) {

        StringBuilder sb = new StringBuilder(100);
        Class<?> cls = o.getClass();
        int identityHC = System.identityHashCode(o);
        sb.append(cls.getSimpleName()).append('[').append(identityHC).append("]{");

        if (!visitedObjects.contains(identityHC)) {
            try {
                visitedObjects.add(identityHC);
                String sep = "";
                for (Field f : cls.getDeclaredFields()) {
                    if (!f.isSynthetic() && !f.getName().contains("$")) {
                        sb.append(sep);
                        sep = ", ";
                        sb.append(f.getName()).append('=');
                        try {
                            f.setAccessible(true);
                            Object value = f.get(o);
                            if (value == null) {
                                sb.append((String) null);
                            } else if (value.getClass().isArray()) {
                                sb.append(Arrays.toString((Object[]) value));
                            } else {
                                sb.append(value);
                            }
                        } catch (SecurityException e) {
                            sb.append("*SECURITY_EXCEPTION*");
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        sb.append('}');
        return sb.toString();
    }
}
