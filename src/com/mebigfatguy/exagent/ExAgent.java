/*
 * exagent - An exception stack trace embellisher
 * Copyright 2014-2016 MeBigFatGuy.com
 * Copyright 2014-2016 Dave Brosius
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

public class ExAgent {
    
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
