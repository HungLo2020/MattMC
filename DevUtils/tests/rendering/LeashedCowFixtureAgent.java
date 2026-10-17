package leashedcowfixture;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.nio.file.Path;
import java.net.URI;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;

/** Add the missing leash input solely in Frozen's copied-world capture helper. */
public final class LeashedCowFixtureAgent {
    public static void premain(String frozenSourcePrefix, Instrumentation instrumentation) {
        if (frozenSourcePrefix == null || frozenSourcePrefix.isEmpty()) throw new IllegalArgumentException("Frozen CodeSource prefix required");
        Path frozenBuild = Path.of(URI.create(frozenSourcePrefix)).toAbsolutePath().normalize();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraft/client/dev/DeterministicCameraCapture".equals(name)
                        || domain == null || domain.getCodeSource() == null
                        || !Path.of(URI.create(domain.getCodeSource().getLocation().toString()))
                                .toAbsolutePath().normalize().startsWith(frozenBuild)) return null;
                int[] sites = {0};
                ClassFile classFile = ClassFile.of();
                byte[] changed = classFile.transformClass(classFile.parse(bytes),
                        ClassTransform.transformingMethodBodies((builder, element) -> {
                            if (element instanceof InvokeInstruction invoke
                                    && invoke.opcode() == Opcode.INVOKEVIRTUAL
                                    && invoke.owner().asInternalName().equals("net/minecraft/server/level/ServerLevel")
                                    && invoke.name().stringValue().equals("addFreshEntity")
                                    && invoke.type().stringValue().equals("(Lnet/minecraft/world/entity/Entity;)Z")) {
                                builder.dup().invokestatic(ClassDesc.of("leashedcowfixture.LeashedCowFixtureHook"),
                                        "attach", MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));
                                sites[0]++;
                            }
                            builder.with(element);
                        }));
                if (sites[0] == 0) throw new IllegalStateException("No capture-helper spawn sites found");
                System.out.println("[Cow leash fixture] capture-helper spawn sites=" + sites[0]);
                return changed;
            }
        });
    }
}
