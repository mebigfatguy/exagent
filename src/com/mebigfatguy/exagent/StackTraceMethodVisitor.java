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
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

public class StackTraceMethodVisitor extends MethodVisitor {

    private static Pattern PARM_PATTERN = Pattern.compile("(\\[*(?:[ZCBSIJFD]|(?:L[^;]+;)+))");
    
    private static String EXAGENT_CLASS_NAME = ExAgent.class.getName().replace('.', '/');
    private static String METHODINFO_CLASS_NAME = MethodInfo.class.getName().replace('.', '/');
    private static String STRING_CLASS_NAME = String.class.getName().replace('.',  '/');
    private static String THREADLOCAL_CLASS_NAME = ThreadLocal.class.getName().replace('.', '/');
    private static String LIST_CLASS_NAME = List.class.getName().replace('.', '/');
    private static String ARRAYLIST_CLASS_NAME = ArrayList.class.getName().replace('.', '/');
    private static String ARRAYS_CLASS_NAME = Arrays.class.getName().replace('.', '/');
    private static String NOSUCHFIELDEXCEPTION_CLASS_NAME = NoSuchFieldException.class.getName().replace('.', '/');
    
    private static BitSet RETURN_CODES = new BitSet();
    static {
        RETURN_CODES.set(Opcodes.IRETURN);
        RETURN_CODES.set(Opcodes.LRETURN);
        RETURN_CODES.set(Opcodes.FRETURN);
        RETURN_CODES.set(Opcodes.DRETURN);
        RETURN_CODES.set(Opcodes.ARETURN);
        RETURN_CODES.set(Opcodes.RETURN);
    }
    
    private static final String CTOR_NAME = "<init>";
    
    private String clsName;
    private String methodName;
    private List<Parm> parms = new ArrayList<>();
    private boolean isCtor;
    private boolean sawInvokeSpecial;
    private int lastParmReg;
    private int exReg;
    private int depthReg;
    
    public StackTraceMethodVisitor(MethodVisitor mv, String cls, String mName, int access, String desc) {
        super(Opcodes.ASM5, mv);
        clsName = cls;
        methodName = mName;
        
        int register = ((access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
        lastParmReg = register - 1;
        List<String> sigs = parseSignature(desc);
        for (String sig : sigs) {
            parms.add(new Parm(sig, register));
            lastParmReg = register;
            register += ("J".equals(sig) || "D".equals(sig)) ? 2 : 1;
        }
        
        exReg = register++;
        depthReg = register;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        
        isCtor = CTOR_NAME.equals(methodName);
        if (isCtor) {
            return;
        }
        
        injectCallStackPopulation();
    }

    @Override
    public void visitInsn(int opcode) {
        
        if (RETURN_CODES.get(opcode)) {
            super.visitVarInsn(Opcodes.ILOAD, depthReg);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "popMethodInfo", "(I)V", false);
        } else if (opcode == Opcodes.ATHROW) {
            
            super.visitVarInsn(Opcodes.ASTORE, exReg);
            
            Label tryLabel = new Label();
            Label endTryLabel = new Label();
            Label catchLabel = new Label();
            Label continueLabel = new Label();
            
            super.visitTryCatchBlock(tryLabel, endTryLabel, catchLabel, NOSUCHFIELDEXCEPTION_CLASS_NAME);
            
            super.visitLabel(tryLabel);
            
            super.visitVarInsn(Opcodes.ALOAD, exReg);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "embellishMessage", "(Ljava/lang/Throwable;)V", false);
            super.visitVarInsn(Opcodes.ILOAD, depthReg);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "popMethodInfo", "(I)V", false);

            super.visitJumpInsn(Opcodes.GOTO, continueLabel);
            super.visitLabel(endTryLabel);
            
            super.visitLabel(catchLabel);
            super.visitInsn(Opcodes.POP);
            
            super.visitLabel(continueLabel);
            super.visitVarInsn(Opcodes.ALOAD, exReg);
        }
        super.visitInsn(opcode);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
        
        if ((opcode == Opcodes.INVOKESPECIAL) && isCtor && !sawInvokeSpecial) {
            sawInvokeSpecial = true;
            
            injectCallStackPopulation();
        }
    }
    
    @Override
    public void visitVarInsn(int opcode, int var) {
        super.visitVarInsn(opcode, (var <= lastParmReg) ? var : var + 2);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        if (mv != null) {
            super.visitLocalVariable(name, desc, signature, start, end, (index <= lastParmReg) ? index : index+2);
        }
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
            TypePath typePath, Label[] start, Label[] end, int[] index,
            String desc, boolean visible) {
        if (api < Opcodes.ASM5) {
            throw new RuntimeException();
        }
        if (mv != null) {
            int[] modifiedIndices = new int[index.length];
            System.arraycopy(index, 0, modifiedIndices, 0, index.length);
            for (int i = 0; i < modifiedIndices.length; i++) {
                if (index[i] > lastParmReg) {
                    modifiedIndices[i] += 2;
                }
            }
            return mv.visitLocalVariableAnnotation(typeRef, typePath, start, end, modifiedIndices, desc, visible);
        }
        return null;
    }
    
    private void injectCallStackPopulation() {
        
        // ExAgent.METHOD_INFO.get();
        super.visitFieldInsn(Opcodes.GETSTATIC, EXAGENT_CLASS_NAME, "METHOD_INFO", signaturizeClass(THREADLOCAL_CLASS_NAME));
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREADLOCAL_CLASS_NAME, "get", "()Ljava/lang/Object;", false);
        super.visitTypeInsn(Opcodes.CHECKCAST, LIST_CLASS_NAME);
        
        super.visitInsn(Opcodes.DUP);
        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "size", "()I", true);
        super.visitVarInsn(Opcodes.ISTORE, depthReg);
        
        //new MethodInfo(cls, name, parmMap);
        super.visitTypeInsn(Opcodes.NEW, METHODINFO_CLASS_NAME);
        super.visitInsn(Opcodes.DUP);
        super.visitLdcInsn(Type.getObjectType(clsName.replace('.',  '/')));
        super.visitLdcInsn(methodName);
        
        super.visitTypeInsn(Opcodes.NEW, ARRAYLIST_CLASS_NAME);
        super.visitInsn(Opcodes.DUP);
        super.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAYLIST_CLASS_NAME, CTOR_NAME, "()V", false);
        
        for (Parm parm : parms) {
            super.visitInsn(Opcodes.DUP);
                  
            switch (parm.signature) {

            case "C":
                super.visitVarInsn(Opcodes.ILOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(C)Ljava/lang/String;", false);
                break;
                
            case "Z":
                super.visitVarInsn(Opcodes.ILOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(Z)Ljava/lang/String;", false);
                break;
                
            case "B":
            case "S":
            case "I":
                super.visitVarInsn(Opcodes.ILOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(I)Ljava/lang/String;", false);
                break;
                
            case "J":
                super.visitVarInsn(Opcodes.LLOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(J)Ljava/lang/String;", false);
                break;
                
            case "F":
                super.visitVarInsn(Opcodes.FLOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(F)Ljava/lang/String;", false);
                break;
                
            case "D":
                super.visitVarInsn(Opcodes.DLOAD, parm.register);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(D)Ljava/lang/String;", false);
                break;
                
            default:
                super.visitVarInsn(Opcodes.ALOAD, parm.register);
                if (parm.signature.startsWith("[")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS_CLASS_NAME, "toString", "([Ljava/lang/Object;)Ljava/lang/String;", false);
                } else {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                }
                break;
            }            
            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "add", "(Ljava/lang/Object;)Z", true);
            super.visitInsn(Opcodes.POP);
        }
        
        super.visitMethodInsn(Opcodes.INVOKESPECIAL,  METHODINFO_CLASS_NAME, CTOR_NAME, "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/List;)V", false);

        //add(methodInfo);
        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "add", "(Ljava/lang/Object;)Z", true);
        super.visitInsn(Opcodes.POP);
    }
    
    private static List<String> parseSignature(String signature) {
        List<String> parms = new ArrayList<>();
        
        int openParenPos = signature.indexOf('(');
        int closeParenPos = signature.indexOf(')', openParenPos+1);
        
        String args = signature.substring(openParenPos + 1, closeParenPos);
        if (!args.isEmpty()) {
            Matcher m = PARM_PATTERN.matcher(args);
            while (m.find()) {
                parms.add(m.group(1));
            }
        }
        
        return parms;
    }
    
    private static String signaturizeClass(String className) {
        return 'L' + className + ';';
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}
