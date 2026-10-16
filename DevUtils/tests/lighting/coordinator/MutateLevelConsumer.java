import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Artifact-only control: restore the old actual Java propagation caller. */
public final class MutateLevelConsumer {
    public static void main(String[] args)throws Exception{
        var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);
        int changed=0;
        for(var method:node.methods)if(method.name.equals("runUpdate")&&method.desc.equals("()V")){
            method.instructions.clear();method.tryCatchBlocks.clear();
            if(method.localVariables!=null)method.localVariables.clear();
            method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"lightTasks","Lnet/minecraft/server/level/NativeLightTasks;"));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,"propagation","Ljava/lang/Runnable;"));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/server/level/NativeLightTasks","runUpdate","(Ljava/lang/Runnable;)V",false));
            method.instructions.add(new InsnNode(Opcodes.RETURN));method.maxLocals=1;method.maxStack=2;changed++;
        }
        if(changed!=1)throw new AssertionError("Actual private update caller was not found once");
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Path target=Path.of(args[1]);Files.createDirectories(target.getParent());Files.write(target,writer.toByteArray());
    }
}
