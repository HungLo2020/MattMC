"""Verify effective HotSpot controls despite different Current/Frozen defaults."""
from pathlib import Path
import os
import re
import shlex
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'Common'))
import graphics_harness as harness


class BenchmarkJvmControlsTest(unittest.TestCase):
    def test_vm_overrides_win_without_moving_gameplay_properties(self):
        result=shlex.split(harness.benchmark_java_options(
            '-Dkept=value -Xms2G', ['-Xmx6G', '-XX:+UseG1GC', '-Dscenario=explicit']))
        self.assertIn('-Dkept=value',result)
        self.assertNotIn('-Dscenario=explicit',result)
        self.assertNotIn('-XX:+UseZGC',result)
        self.assertEqual([x for x in result if x.startswith('-Xms')][-1],'-Xms2G')
        self.assertEqual([x for x in result if x.startswith('-Xmx')][-1],'-Xmx6G')

    def test_both_legacy_commands_have_identical_effective_vm_options(self):
        jdk=os.environ.get('JAVA_HOME')
        if not jdk:self.skipTest('JAVA_HOME must identify JDK25 for effective-VM verification')
        env=os.environ.copy();env.pop('JAVA_TOOL_OPTIONS',None)
        env['_JAVA_OPTIONS']=harness.benchmark_java_options('',[])
        keys=['UseG1GC','UseZGC','InitialHeapSize','MaxHeapSize','UseCompactObjectHeaders',
              'ZUncommitDelay','Tier3InvocationThreshold','Tier4InvocationThreshold',
              'Tier4MinInvocationThreshold','Tier4CompileThreshold']
        actual=[]
        for flags in (['-XX:+UseG1GC','-Xms1G','-Xmx8G'],
                      ['-XX:+UseZGC','-Xms4G','-Xmx8G']):
            result=subprocess.run([str(Path(jdk)/'bin/java'),*flags,'-XX:+PrintFlagsFinal','-version'],
                                  env=env,text=True,capture_output=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stderr)
            values={key:re.search(r'\b'+key+r'\s*=\s*(\S+)',result.stdout).group(1) for key in keys}
            actual.append(values)
        self.assertEqual(actual[0],actual[1])
        self.assertEqual(actual[0]['InitialHeapSize'],str(1<<30))
        self.assertEqual(actual[0]['MaxHeapSize'],str(8<<30))
        self.assertEqual(actual[0]['UseZGC'],'false' if sys.platform=='darwin' else 'true')
        self.assertEqual(actual[0]['Tier4InvocationThreshold'],'600')


if __name__=='__main__':unittest.main()
