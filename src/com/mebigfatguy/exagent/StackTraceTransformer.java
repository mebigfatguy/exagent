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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class StackTraceTransformer implements ClassFileTransformer {
    
    private Options options;
    
    public StackTraceTransformer(Options options) {
        this.options = options;
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {
        
        if (className.startsWith("java/") 
         || className.startsWith("javax/") 
         || className.startsWith("sun/") 
         || className.startsWith("com/mebigfatguy/exagent/")
         || !options.instrumentClass(className)) {
            return classfileBuffer;
        }
        
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
        ClassVisitor stackTraceVisitor = new StackTraceClassVisitor(cw, options.getParmSizeLimit());
        cr.accept(stackTraceVisitor, ClassReader.EXPAND_FRAMES);
        
        debugWriteBytes(className, cw.toByteArray());
        return cw.toByteArray();
    }
    
    private static void debugWriteBytes(String className, byte[] data) {
        File f = new File(System.getProperty("user.home"), "exaclasses");
        f.mkdirs();
        f = new File(f, className + ".class");
        f.getParentFile().mkdirs();
        
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(f))) {
            os.write(data);
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    @Override
    public String toString() {
        return ToString.build(this);
    }
}