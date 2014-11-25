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

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ExAgent {

    public static ThreadLocal<List<MethodInfo>> METHOD_INFO = new ThreadLocal<List<MethodInfo>>() {
        @Override 
        protected List<MethodInfo> initialValue() {
            return new ArrayList<>();
        }
    };
    
    public static void embellishMessage(Throwable t) throws IllegalAccessException, NoSuchFieldException {
        StringBuilder msg = new StringBuilder();
        for (MethodInfo mi : METHOD_INFO.get()) {
            msg.insert(0, mi.toString());
            msg.insert(0, "\n");
        }
        msg.insert(0, t.getMessage());

        Field f = getMessageField(t);
        f.set(t, msg.toString());
    }
    
    public static void popMethodInfo(int toDepth) {
        List<MethodInfo> mi = METHOD_INFO.get();
        while (!mi.isEmpty() && (mi.size() > toDepth)) {
            mi.remove(mi.size() - 1);
        }
    }
    
    private static Field getMessageField(Throwable t) throws NoSuchFieldException {
        Class<?> c = t.getClass();
        while (c != Throwable.class) {
            c = c.getSuperclass();
        }
        
        Field f = c.getDeclaredField("detailMessage");
        f.setAccessible(true);
        return f;
    }
    
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        Options options = new Options(agentArguments);
        
        StackTraceTransformer mutator = new StackTraceTransformer(options);
        instrumentation.addTransformer(mutator);
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}
