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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.LocalVariablesSorter;

public class StackTraceMethodVisitor extends LocalVariablesSorter {

    private static Pattern PARM_PATTERN = Pattern.compile("([ZCBSIJFD]|(?:L[^;]+;)+)");
    
    private static String EXAGENT_CLASS_NAME = ExAgent.class.getName().replace('.', '/');
    private static String METHODINFO_CLASS_NAME = MethodInfo.class.getName().replace('.', '/');
    private static String STRING_CLASS_NAME = String.class.getName().replace('.',  '/');
    private static String THREADLOCAL_CLASS_NAME = ThreadLocal.class.getName().replace('.', '/');
    private static String LIST_CLASS_NAME = List.class.getName().replace('.', '/');
    private static String ARRAYLIST_CLASS_NAME = ArrayList.class.getName().replace('.', '/');
    private static String THROWABLE_CLASS_NAME = Throwable.class.getName().replace('.', '/');
    private static String OBJECT_CLASS_NAME = Object.class.getName().replace('.', '/');
    private static String CLASS_CLASS_NAME = Class.class.getName().replace('.', '/');
    private static String FIELD_CLASS_NAME = Field.class.getName().replace('.', '/');
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
    private int parmCnt;
    private boolean isCtor;
    private int exReg, messageReg;
    
    public StackTraceMethodVisitor(MethodVisitor mv, String cls, String mName, int access, String desc) {
        super(Opcodes.ASM5, access, desc, mv);
        clsName = cls;
        methodName = mName;
        
        int register = ((access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
        int parmIdx = 1;
        List<String> sigs = parseSignature(desc);
        for (String sig : sigs) {
            parms.add(new Parm("parm " + parmIdx++, sig, register));
            register += ("J".equals(sig) || "D".equals(sig)) ? 2 : 1;
        }
    }
    
    @Override
    public void visitParameter(String name, int access) {
        super.visitParameter(name, access);
        if (name != null)
            parms.get(++parmCnt).name = name;
    }

    @Override
    public void visitAttribute(Attribute attr) {
        super.visitAttribute(attr);
    }

    @Override
    public void visitCode() {
        super.visitCode();
        
        isCtor = CTOR_NAME.equals(methodName);
        if (isCtor || ("<clinit>".equals(methodName))) {
            return;
        }
        
        injectCallStackPopulation();
    }

    @Override
    public void visitInsn(int opcode) {
        if (!isCtor) {
            if (RETURN_CODES.get(opcode)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "popMethodInfo", "()V", false);
            } else if (opcode == Opcodes.ATHROW) {
                
                exReg = newLocal(Type.getObjectType("java/lang/Throwable"));
                messageReg = newLocal(Type.getObjectType("java/lang/String"));
                
                super.visitVarInsn(Opcodes.ASTORE, exReg);
                
                Label tryLabel = new Label();
                Label endTryLabel = new Label();
                Label catchLabel = new Label();
                Label continueLabel = new Label();
                
                super.visitTryCatchBlock(tryLabel, endTryLabel, catchLabel, NOSUCHFIELDEXCEPTION_CLASS_NAME);
                
                super.visitLabel(tryLabel);
                
                super.visitVarInsn(Opcodes.ALOAD, exReg);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "embellishMessage", "(Ljava/lang/Throwable;)V", false);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, EXAGENT_CLASS_NAME, "popMethodInfo", "()V", false);

                super.visitJumpInsn(Opcodes.GOTO, continueLabel);
                super.visitLabel(endTryLabel);
                
                super.visitLabel(catchLabel);
                super.visitInsn(Opcodes.POP);
                
                super.visitLabel(continueLabel);
                super.visitVarInsn(Opcodes.ALOAD, exReg);
            }
        }
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
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
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
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
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
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        super.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
    }
    
    @Override
    public void visitEnd() {
        super.visitEnd();
    }
    
    private void injectCallStackPopulation() {
        
        // ExAgent.METHOD_INFO.get();
        super.visitFieldInsn(Opcodes.GETSTATIC, EXAGENT_CLASS_NAME, "METHOD_INFO", signaturizeClass(THREADLOCAL_CLASS_NAME));
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREADLOCAL_CLASS_NAME, "get", "()Ljava/lang/Object;", false);
        super.visitTypeInsn(Opcodes.CHECKCAST, LIST_CLASS_NAME);
        
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
            
            super.visitLdcInsn(parm.name);
            super.visitLdcInsn(": ");
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_CLASS_NAME, "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
            
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
                super.visitMethodInsn(Opcodes.INVOKESTATIC, STRING_CLASS_NAME, "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                break;
            }
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_CLASS_NAME, "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
            
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
