/*
 * exagent - An exception stack trace embellisher
 * Copyright 2014 MeBigFatGuy.com
 * Copyright 2014 Dave Brosius
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

/**
 * an automatic toString() builder using reflection
 */
public class ToString {

    private ToString() {
    }
    
    public static String build(Object o) {
        StringBuilder sb = new StringBuilder(100);
        Class<?> cls = o.getClass();
        sb.append(cls.getSimpleName()).append('[');
        
        try {
            String sep = "";
            for (Field f : cls.getDeclaredFields()) {
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
        } catch (Exception e) {
        }
        
        sb.append(']');
        return sb.toString();
    }
}
