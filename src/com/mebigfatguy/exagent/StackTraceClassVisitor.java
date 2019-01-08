/*
 * exagent - An exception stack trace embellisher
 * Copyright 2014-2019 MeBigFatGuy.com
 * Copyright 2014-2019 Dave Brosius
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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StackTraceClassVisitor extends ClassVisitor {

    private String clsName;
    private int maxParmSize;
    
    public StackTraceClassVisitor(ClassWriter cw, int parmSizeLimit) {
        super(Opcodes.ASM5, cw);
        maxParmSize = parmSizeLimit;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        clsName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    /**
     * we ignore instrumenting toString as you can get into infinite recursive loops
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("toString") && desc.equals("()Ljava/lang/String;")) {
            return mv;
        }
        
        return new StackTraceMethodVisitor(mv, clsName, name, access, desc, maxParmSize);
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}
