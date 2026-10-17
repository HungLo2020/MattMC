#!/usr/bin/env python3
"""Prepare an isolated Gradle home that instruments only Frozen's game JVM.

Place this home outside the capture artifact root, then pass it through
GRADLE_USER_HOME to a Frozen OpenGL Capture.py --renderdoc-capture run.
No Frozen source, build configuration or renderer behavior is changed.
"""
from __future__ import annotations

import argparse
from pathlib import Path


INIT_SCRIPT = '''import org.gradle.process.ExecOperations
import javax.inject.Inject
interface MattmcRenderDocExecServices {
    @Inject ExecOperations getExecOperations()
}
gradle.projectsEvaluated {
    allprojects { project ->
        tasks.withType(org.gradle.api.tasks.JavaExec).matching { it.name == 'runClient' }.configureEach { task ->
            if (System.getenv('MATTMC_RENDERDOC_CAPTURE') != 'true') {
                throw new GradleException('This isolated init script requires a RenderDoc diagnostic')
            }
            def services = project.objects.newInstance(MattmcRenderDocExecServices)
            def launch = { org.gradle.api.Task executable ->
                if (task.mainClass.get() != 'net.fabricmc.loader.impl.launch.knot.KnotClient'
                    || task.mainModule.present || !task.jvmArgumentProviders.empty || !task.argumentProviders.empty) {
                    throw new GradleException('Unsupported JavaExec launch shape for exact client observation')
                }
                def java = task.javaLauncher.get().executablePath.asFile.absolutePath
                def command = [System.getenv('MATTMC_RENDERDOC_CMD'), 'capture', '-d', task.workingDir.absolutePath,
                    '-w', '--opt-api-validation', '-c', System.getenv('MATTMC_RENDERDOC_CAPTURE_TEMPLATE'), java]
                command.addAll(task.allJvmArgs)
                command.addAll(['-cp', task.classpath.asPath, task.mainClass.get()])
                command.addAll(task.args)
                println 'MattmcRenderDocExactJavaExec java=' + java + ' cwd=' + task.workingDir
                services.execOperations.exec { spec ->
                    spec.commandLine command
                    spec.workingDir task.workingDir
                    spec.environment task.environment
                    if (task.standardOutput != null) spec.standardOutput task.standardOutput
                    if (task.errorOutput != null) spec.errorOutput task.errorOutput
                    if (task.standardInput != null) spec.standardInput task.standardInput
                    spec.ignoreExitValue task.ignoreExitValue
                }.assertNormalExitValue()
            } as org.gradle.api.Action<org.gradle.api.Task>
            // Preserve dependencies and fixture-staging actions. Replace only
            // the final JavaExec action with the diagnostic process wrapper.
            task.actions.set(task.actions.size() - 1, launch)
        }
    }
}
'''


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-home', type=Path, required=True)
    parser.add_argument('--base-gradle-home', type=Path, default=Path.home() / '.gradle')
    args = parser.parse_args()
    output = args.gradle_home.resolve()
    base = args.base_gradle_home.resolve(strict=True)
    if (base / 'init.d/mattmc-renderdoc-game.gradle').exists():
        parser.error('base Gradle home already contains the reserved diagnostic init script')
    output.mkdir(parents=True, exist_ok=False)
    for name in ('caches', 'wrapper', 'jdks', 'native', 'daemon', 'notifications', 'workers', 'build-scan-data'):
        path = base / name
        if path.exists():
            (output / name).symlink_to(path, target_is_directory=path.is_dir())
    init = output / 'init.d'
    init.mkdir()
    for path in (base / 'init.d').glob('*'):
        if path.is_file():
            (init / path.name).symlink_to(path)
    (init / 'mattmc-renderdoc-game.gradle').write_text(INIT_SCRIPT)
    print(output)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
