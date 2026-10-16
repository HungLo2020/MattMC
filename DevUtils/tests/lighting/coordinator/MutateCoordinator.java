import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Artifact-only omitted-operation controls; never modifies production classes. */
public final class MutateCoordinator {
    public static void main(String[] args) throws Exception {
        ClassNode owner = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(owner, 0);
        int matches = 0;
        for (MethodNode method : owner.methods) {
            if (!method.name.equals(args[2])) continue;
            matches++;
            Type result = Type.getReturnType(method.desc);
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            switch (result.getSort()) {
                case Type.VOID -> method.instructions.add(new InsnNode(Opcodes.RETURN));
                case Type.BOOLEAN, Type.INT -> {
                    method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                    method.instructions.add(new InsnNode(Opcodes.IRETURN));
                }
                case Type.OBJECT -> {
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.add(new InsnNode(Opcodes.ARETURN));
                }
                default -> throw new AssertionError("Unsupported control return: " + method.desc);
            }
        }
        if (matches != 1) throw new AssertionError("Expected one operation, found " + matches);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        Path target = Path.of(args[1]);
        Files.createDirectories(target.getParent());
        Files.write(target, writer.toByteArray());
        System.out.println("Omitted actual coordinator operation " + args[2]);
    }
}
