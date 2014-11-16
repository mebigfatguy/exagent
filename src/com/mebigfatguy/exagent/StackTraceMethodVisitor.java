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

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

public class StackTraceMethodVisitor extends MethodVisitor {

    private List<MethodInfo> callStack;
    private String clsName;
    private String methodDescription;
    private List<String> parmNames;
    
    public StackTraceMethodVisitor(MethodVisitor mv, List<MethodInfo> cs, String cls, String desc) {
        super(Opcodes.ASM5, mv);
        callStack = cs;
        clsName = cls;
        methodDescription = desc;  
        parmNames = new ArrayList<>();
    }
    
    @Override
    public void visitParameter(String name, int access) {
        super.visitParameter(name, access);
        parmNames.add(name);
    }

    @Override
    public void visitAttribute(Attribute attr) {
        super.visitAttribute(attr);
    }

    @Override
    public void visitCode() {
        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name,
            String desc) {
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        super.visitLdcInsn(cst);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        super.visitIincInsn(var, increment);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt,
            Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        super.visitMultiANewArrayInsn(desc, dims);
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler,
            String type) {
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef,
            TypePath typePath, String desc, boolean visible) {
        return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
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
