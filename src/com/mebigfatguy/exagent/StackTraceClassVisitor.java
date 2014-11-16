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

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

public class StackTraceClassVisitor extends ClassVisitor {

    private List<MethodInfo> methodInfo;
    private StackTraceMethodVisitor methodVisitor;
    
    public StackTraceClassVisitor(ClassWriter cw, List<MethodInfo> methodInfo) {
        super(Opcodes.ASM5, cw);
        this.methodInfo = methodInfo;
        methodVisitor = new StackTraceMethodVisitor(methodInfo);
    }
    
    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        methodVisitor.setClass(name);
    }

    @Override
    public void visitAttribute(Attribute attr) {
        super.visitAttribute(attr);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
        methodVisitor.setMethodDescription(desc);
        return methodVisitor;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}
