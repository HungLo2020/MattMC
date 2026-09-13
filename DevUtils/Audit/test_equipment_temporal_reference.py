import sys
import unittest
import copy
import tempfile
from unittest.mock import patch
from contextlib import ExitStack
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Common'))
import equipment_temporal_reference as ref
import equipment_reference as equipment
import test_equipment_moving_reference as moving_test


class EquipmentTemporalTest(unittest.TestCase):
    def test_final_report_requires_original_reference_and_rechecks_both_executions(self):
        with tempfile.TemporaryDirectory() as tmp, ExitStack() as stack:
            root=Path(tmp);files={};documents={};visuals=[]
            for phase in (10000,40000):
                directory=root/str(phase);directory.mkdir()
                docs,phase_files=moving_test.MovingEquipmentTest().fixture(directory,phase)
                files.update(phase_files);pair={}
                for side,mode,doc in zip(('baseline','current'),
                    ('frozen-opengl-shaders-off','current-rust-vulkan-shaders-off'),docs):
                    path=directory/(side+'.json');documents[path]=doc
                    files[path]=dict(mode=dict(name=mode),tool='capture',capture=dict(exit_code=0),
                        validation=dict(complete=True,crash_free=True,device_loss_free=True,
                            rss_guard_triggered=False,orphan_process_detected=False,vulkan_validation_clean=True),
                        metrics=dict(rss_and_native_memory=dict(client_observation=dict(complete=True,
                            provider='linux-proc-status',sample_count=10,peak_rss_kb=100,peak_hwm_kb=100))))
                    pair[side+'_artifact']=str(path);pair[side+'_image']=doc['captures'][0]['screenshot']
                visuals.append(dict(passed=True,pairs=[pair]))
            reference=root/'reference.json'
            files[reference]=dict(success=True,equipment_parity=dict(passed=True),
                                  cross_repository_visual_parity=visuals[0])
            stack.enter_context(patch('graphics_harness.read_json',side_effect=lambda p:files[Path(p)]))
            stack.enter_context(patch('graphics_harness.deterministic_capture_document',side_effect=lambda p:documents[Path(p)]))
            stack.enter_context(patch('graphics_harness.latest_capture_meta_path',return_value=root/'meta.txt'))
            stack.enter_context(patch('graphics_harness.read_key_values',return_value={'forced_option_guiScale':'3'}))
            args=['-Dmattmc.dev.graphicsAuditEquipment=foil','-Dmattmc.dev.equipmentFoilPhase=40000',
                  '-Dmattmc.dev.equipmentFoilPoseStep=8000','-Dmattmc.dev.equipmentTickStepping=true',
                  '-Dmattmc.dev.equipmentFoilTiming=true']
            result=equipment.report(visuals[1],args,reference)
            self.assertTrue(result['passed'],result.get('reason'))
            self.assertTrue(result['temporal']['passed']);self.assertFalse(result['capability_admitted'])
            files[reference]['success']=False
            self.assertFalse(equipment.report(visuals[1],args,reference)['passed'])
            files[reference]['success']=True
            for path in documents:
                files[path]['validation']['complete']=False
                self.assertFalse(equipment.report(visuals[1],args,reference)['passed'])
                files[path]['validation']['complete']=True
            # A same-pose cross-phase geometry change must not be attributed to glint.
            paths=[Path(documents[root/('40000/'+side+'.json')]['captures'][3]['screenshot']
                        +'.equipment-timing.json') for side in ('baseline','current')]
            originals=[copy.deepcopy(files[path]) for path in paths]
            for path in paths:
                files[path]['entityTransforms'][0]['modelView'][0]=0.01
            self.assertFalse(equipment.report(visuals[1],args,reference)['passed'])
            for path,original in zip(paths,originals):files[path]=original
            files[reference]['cross_repository_visual_parity']=visuals[1]
            self.assertFalse(equipment.report(visuals[1],args,reference)['passed'])

    def sequence(self, color):
        return [Image.new('RGB', (1280, 720), color) for _ in range(5)]

    def test_requires_visible_matching_change_on_each_piece(self):
        before = self.sequence((20, 20, 20))
        after = self.sequence((26, 20, 20))
        self.assertTrue(ref.moving_changes(before, before, after, after)['passed'])
        self.assertFalse(ref.moving_changes(before, before, after, after)['capability_admitted'])
        for group, indices in ref.GROUPS.items():
            missing = [image.copy() for image in after]
            for image in missing:
                for index in indices:
                    x, y, _ = ref.REFERENCES['foil']['probes'][index]
                    image.paste((20, 20, 20), (x - 1, y - 1, x + 2, y + 2))
            with self.subTest(group=group), self.assertRaises(ValueError):
                ref.moving_changes(before, before, missing, missing)
        for change in (0, 4):
            weak = self.sequence((20 + change, 20, 20))
            with self.assertRaises(ValueError): ref.moving_changes(before, before, weak, weak)

    def test_checks_every_pose_probe_channel_and_sequence_length(self):
        before = self.sequence((20, 20, 20))
        after = self.sequence((30, 20, 20))
        for pose in range(5):
            for x, y, _ in ref.REFERENCES['foil']['probes']:
                bad = [image.copy() for image in after]
                bad[pose].putpixel((x, y), (30, 23, 20))
                with self.assertRaises(ValueError): ref.moving_changes(before, before, after, bad)
        for side in range(4):
            sequences = [before, before, after, after]
            sequences[side] = sequences[side][:-1]
            with self.assertRaises(ValueError): ref.moving_changes(*sequences)
        with self.assertRaises(ValueError):
            ref.moving_changes(before, before, after, self.sequence((10, 20, 20)))


if __name__ == '__main__': unittest.main()
