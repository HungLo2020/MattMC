package fullgeneration;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** Starts the audit controller on the first server tick and times chunk status tasks. */
public final class FullGenAgent {
	private static final String TASKS = "net/minecraft/world/level/chunk/status/ChunkStatusTasks";
	private static final Type AUDIT = Type.getObjectType("fullgeneration/FullGenAudit");

	public static void premain(String options, Instrumentation instrumentation) {
		instrumentation.addTransformer(new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
				boolean server = name.equals("net/minecraft/server/MinecraftServer");
				boolean tasks = name.equals(TASKS);
				if (!server && !tasks) {
					return null;
				}
				ClassReader reader = new ClassReader(bytes);
				ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
				reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
					@Override
					public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
						MethodVisitor mv = super.visitMethod(access, method, descriptor, signature, exceptions);
						if (server && method.equals("tickServer") && descriptor.equals("(Ljava/util/function/BooleanSupplier;)V")) {
							return new AdviceAdapter(Opcodes.ASM9, mv, access, method, descriptor) {
								@Override
								protected void onMethodEnter() {
									loadThis();
									invokeStatic(AUDIT, new Method("capture", "(Ljava/lang/Object;)V"));
								}
							};
						}
						if (tasks && (access & Opcodes.ACC_STATIC) != 0 && descriptor.endsWith(")Ljava/util/concurrent/CompletableFuture;")) {
							return new AdviceAdapter(Opcodes.ASM9, mv, access, method, descriptor) {
								@Override
								protected void onMethodEnter() {
									invokeStatic(AUDIT, new Method("enter", "()V"));
								}

								@Override
								protected void onMethodExit(int opcode) {
									push(method);
									invokeStatic(AUDIT, new Method("exit", "(Ljava/lang/String;)V"));
								}
							};
						}
						return mv;
					}
				}, ClassReader.EXPAND_FRAMES);
				return writer.toByteArray();
			}
		});
	}
}
