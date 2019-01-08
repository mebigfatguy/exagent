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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import com.mebigfatguy.exagent.rtsupport.EXASupport;

public class StackTraceMethodVisitor extends MethodVisitor {

    private static Pattern PARM_PATTERN = Pattern.compile("(\\[*(?:[ZCBSIJFD]|(?:L[^;]+;)))");
    
    private static String EXASUPPORT_CLASS_NAME = EXASupport.class.getName().replace('.', '/');
    private static String METHODINFO_CLASS_NAME = MethodInfo.class.getName().replace('.', '/');
    private static String STRING_CLASS_NAME = String.class.getName().replace('.',  '/');
    private static String THREADLOCAL_CLASS_NAME = ThreadLocal.class.getName().replace('.', '/');
    private static String LIST_CLASS_NAME = List.class.getName().replace('.', '/');
    private static String ARRAYLIST_CLASS_NAME = ArrayList.class.getName().replace('.', '/');
    private static String ARRAYS_CLASS_NAME = Arrays.class.getName().replace('.', '/');
    private static String COLLECTIONS_CLASS_NAME = Collections.class.getName().replace('.', '/');
    private static String EXCEPTION_CLASS_NAME = Exception.class.getName().replace('.', '/');
    
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
    private int lastParmSlot;
    private int exLocalSlot;
    private int depthLocalSlot;
    private int maxParmSize;
    
    public StackTraceMethodVisitor(MethodVisitor mv, String cls, String mName, int access, String desc, int parmSizeLimit) {
        super(Opcodes.ASM5, mv);
        clsName = cls;
        methodName = mName;
        maxParmSize = parmSizeLimit;
        
        int nextSlot = ((access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
        lastParmSlot = nextSlot - 1;
        List<String> sigs = parseSignature(desc);
        for (String sig : sigs) {
            parms.add(new Parm(sig, nextSlot));
            lastParmSlot = nextSlot;
            nextSlot += ("J".equals(sig) || "D".equals(sig)) ? 2 : 1;
        }
        
        exLocalSlot = nextSlot++;
        depthLocalSlot = nextSlot;
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
            super.visitVarInsn(Opcodes.ILOAD, depthLocalSlot);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXASUPPORT_CLASS_NAME, "popMethodInfo", "(I)V", false);
        } else if (opcode == Opcodes.ATHROW) {
            
            super.visitVarInsn(Opcodes.ASTORE, exLocalSlot);
            
            Label tryLabel = new Label();
            Label endTryLabel = new Label();
            Label catchLabel = new Label();
            Label continueLabel = new Label();
            
            super.visitTryCatchBlock(tryLabel, endTryLabel, catchLabel, EXCEPTION_CLASS_NAME);
            
            super.visitLabel(tryLabel);
            
            super.visitVarInsn(Opcodes.ALOAD, exLocalSlot);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXASUPPORT_CLASS_NAME, "embellishMessage", "(Ljava/lang/Throwable;)V", false);
            super.visitVarInsn(Opcodes.ILOAD, depthLocalSlot);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, EXASUPPORT_CLASS_NAME, "popMethodInfo", "(I)V", false);

            super.visitJumpInsn(Opcodes.GOTO, continueLabel);
            super.visitLabel(endTryLabel);
            
            super.visitLabel(catchLabel);
            super.visitInsn(Opcodes.POP);
            
            super.visitLabel(continueLabel);
            super.visitVarInsn(Opcodes.ALOAD, exLocalSlot);
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
        super.visitVarInsn(opcode, (var <= lastParmSlot) ? var : var + 2);
    }
    
    @Override
    public void visitIincInsn(int var, int increment) {
        mv.visitIincInsn((var <= lastParmSlot) ? var : var + 2, increment);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        super.visitLocalVariable(name, desc, signature, start, end, (index <= lastParmSlot) ? index : index+2);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
        if (api < Opcodes.ASM5) {
            throw new RuntimeException();
        }
        int[] modifiedIndices = new int[index.length];
        System.arraycopy(index, 0, modifiedIndices, 0, index.length);
        for (int i = 0; i < modifiedIndices.length; i++) {
            if (index[i] > lastParmSlot) {
                modifiedIndices[i] += 2;
            }
        }
        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, modifiedIndices, desc, visible);
    }
    
    private void injectCallStackPopulation() {
        
        // ExAgent.METHOD_INFO.get();
        super.visitFieldInsn(Opcodes.GETSTATIC, EXASUPPORT_CLASS_NAME, "METHOD_INFO", signaturizeClass(THREADLOCAL_CLASS_NAME));
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREADLOCAL_CLASS_NAME, "get", "()Ljava/lang/Object;", false);
        super.visitTypeInsn(Opcodes.CHECKCAST, LIST_CLASS_NAME);
        
        super.visitInsn(Opcodes.DUP);
        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "size", "()I", true);
        super.visitVarInsn(Opcodes.ISTORE, depthLocalSlot);
        
        //new MethodInfo(cls, name, parmMap);
        super.visitTypeInsn(Opcodes.NEW, METHODINFO_CLASS_NAME);
        super.visitInsn(Opcodes.DUP);
        super.visitLdcInsn(clsName.replace('.',  '/'));
        super.visitLdcInsn(methodName);        
        
        if (parms.isEmpty()) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, COLLECTIONS_CLASS_NAME, "emptyList", "()Ljava/util/List;", false);
        } else {
            super.visitTypeInsn(Opcodes.NEW, ARRAYLIST_CLASS_NAME);
            super.visitInsn(Opcodes.DUP);
            super.visitIntInsn(Opcodes.BIPUSH, parms.size());
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, ARRAYLIST_CLASS_NAME, CTOR_NAME, "(I)V", false);

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
                        char arrayElemTypeChar = parm.signature.charAt(1);
                        if ((arrayElemTypeChar == 'L') || (arrayElemTypeChar == '[')) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS_CLASS_NAME, "toString", "([Ljava/lang/Object;)Ljava/lang/String;", false);
                        } else {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ARRAYS_CLASS_NAME, "toString", "([" + arrayElemTypeChar + ")Ljava/lang/String;", false);                            
                        }
                    } else {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                    }
                    break;
                }  
                
                if (maxParmSize > 0) {
                    super.visitInsn(Opcodes.DUP);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_CLASS_NAME, "length", "()I", false);
                    if (maxParmSize <= 127) {
                        super.visitIntInsn(Opcodes.BIPUSH, maxParmSize);
                    } else {
                        super.visitLdcInsn(maxParmSize);
                    }
                    Label falseLabel = new Label();
                    super.visitJumpInsn(Opcodes.IF_ICMPLE, falseLabel);
                    super.visitIntInsn(Opcodes.BIPUSH, 0);
                    super.visitLdcInsn(maxParmSize);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_CLASS_NAME, "substring", "(II)Ljava/lang/String;", false);
                    super.visitLabel(falseLabel);
                }
                
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "add", "(Ljava/lang/Object;)Z", true);
                super.visitInsn(Opcodes.POP);
            }
        }
        
        super.visitMethodInsn(Opcodes.INVOKESPECIAL,  METHODINFO_CLASS_NAME, CTOR_NAME, "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)V", false);

        //add(methodInfo);
        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, LIST_CLASS_NAME, "add", "(Ljava/lang/Object;)Z", true);
        super.visitInsn(Opcodes.POP);
    }
    
    private static List<String> parseSignature(String signature) {
        List<String> parms = new ArrayList<>(8);
        
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
