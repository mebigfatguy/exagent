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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Options {

    private static final String EXCLUSION_PATTERN_NAME = "exclusion_pattern";
    private static final String INCLUSION_PATTERN_NAME = "inclusion_pattern";
    private static final String PARM_SIZE_LIMIT_NAME = "parm_size_limit";
    
    private Pattern classExclusionPattern;
    private Pattern classInclusionPattern;
    private int parmSizeLimit;
    
    public Options(String agentArgs) {
        if (agentArgs != null) {
            String[] args = agentArgs.split(";");
            for (String arg : args) {
                try {
                    String[] kv = arg.split("=");
                    String key = kv[0].trim();
                    
                    switch (key) {
                        case EXCLUSION_PATTERN_NAME:
                            classExclusionPattern = Pattern.compile(kv[1].trim());
                        break;
                        
                        case INCLUSION_PATTERN_NAME:
                            classInclusionPattern = Pattern.compile(kv[1].trim());
                        break;
                        
                        case PARM_SIZE_LIMIT_NAME:
                            parmSizeLimit = Integer.parseInt(kv[1].trim());
                        break;
                    }
                } catch (Exception e) {
                    // swallow it
                }
            }
        }
    }
    
    public boolean instrumentClass(String className) {
        
        if (classExclusionPattern != null) {
            Matcher m = classExclusionPattern.matcher(className);
            if (m.matches()) {
                return false;
            }
        }
        
        if (classInclusionPattern != null) {
            Matcher m = classInclusionPattern.matcher(className);
            if (m.matches()) {
                return true;
            }
            return false;
        }
        
        return true;
    }
    
    public int getParmSizeLimit() {
        return parmSizeLimit;
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}
