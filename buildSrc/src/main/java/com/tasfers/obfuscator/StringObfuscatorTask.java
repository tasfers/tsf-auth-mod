package com.tasfers.obfuscator;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class StringObfuscatorTask extends DefaultTask {
    private File inputJar;
    private File outputJar;

    @InputFile
    public File getInputJar() { return inputJar; }
    public void setInputJar(File inputJar) { this.inputJar = inputJar; }

    @OutputFile
    public File getOutputJar() { return outputJar; }
    public void setOutputJar(File outputJar) { this.outputJar = outputJar; }

    @TaskAction
    public void obfuscate() throws Exception {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(inputJar));
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputJar));
        ZipEntry entry;
        
        while ((entry = zis.getNextEntry()) != null) {
            byte[] data = readAllBytes(zis);
            if (entry.getName().endsWith(".class") && entry.getName().startsWith("com/tasfers/tsfauth/")) {
                boolean isExcluded = entry.getName().equals("com/tasfers/tsfauth/security/StringDecrypter.class") ||
                                     entry.getName().startsWith("com/tasfers/tsfauth/mixin/") ||
                                     entry.getName().equals("com/tasfers/tsfauth/TsfAuthPreLaunch.class") ||
                                     entry.getName().equals("com/tasfers/tsfauth/integration/ModMenuIntegration.class") ||
                                     entry.getName().equals("com/tasfers/tsfauth/TsfAuthClient.class");
                if (!isExcluded) {
                    data = obfuscateClass(data);
                }
            }
            ZipEntry newEntry = new ZipEntry(entry.getName());
            zos.putNextEntry(newEntry);
            zos.write(data);
            zos.closeEntry();
        }
        zis.close();
        zos.close();
    }

    private byte[] obfuscateClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean modified = false;
        int fieldCounter = 0;
        
        MethodNode clinit = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<clinit>")) {
                clinit = mn;
                break;
            }
        }
        
        InsnList clinitInsn = new InsnList();

        for (MethodNode mn : cn.methods) {
            ListIterator<AbstractInsnNode> it = mn.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof String) {
                        String original = (String) ldc.cst;
                        
                        byte[] encrypted = encryptBytes(original);
                        String fieldName = "OBF_STR_" + (++fieldCounter);
                        
                        cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, fieldName, "[B", null, null));
                        
                        pushInt(clinitInsn, encrypted.length);
                        clinitInsn.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE));
                        
                        for (int i = 0; i < encrypted.length; i++) {
                            clinitInsn.add(new InsnNode(Opcodes.DUP));
                            pushInt(clinitInsn, i);
                            pushInt(clinitInsn, encrypted[i]);
                            clinitInsn.add(new InsnNode(Opcodes.BASTORE));
                        }
                        
                        clinitInsn.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, fieldName, "[B"));
                        
                        InsnList il = new InsnList();
                        il.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, fieldName, "[B"));
                        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/tasfers/tsfauth/security/StringDecrypter", "decrypt", "([B)Ljava/lang/String;", false));
                        
                        mn.instructions.insert(insn, il);
                        mn.instructions.remove(insn);
                        modified = true;
                    }
                }
            }
        }

        if (modified) {
            if (clinit == null) {
                clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                clinit.instructions.add(new InsnNode(Opcodes.RETURN));
                cn.methods.add(clinit);
            }
            clinit.instructions.insert(clinitInsn);
            
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return classBytes;
    }

    private void pushInt(InsnList list, int value) {
        if (value >= -1 && value <= 5) {
            list.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            list.add(new IntInsnNode(Opcodes.BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            list.add(new IntInsnNode(Opcodes.SIPUSH, value));
        } else {
            list.add(new LdcInsnNode(value));
        }
    }

    private byte[] encryptBytes(String str) {
        byte[] strBytes = str.getBytes();
        byte[] result = new byte[strBytes.length];
        for (int i = 0; i < strBytes.length; i++) {
            result[i] = (byte) (strBytes[i] ^ 0x42);
        }
        return result;
    }
    
    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
