import copy
import math
import sys
import unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"Common"))
from held_special_foil_reference import native_inputs, clock_pixels, held_pixels, CLOCK_PROBES, COMPASS_PROBES, RECOVERY_PROBES, stopped_hand


class HeldSpecialFoilReferenceTest(unittest.TestCase):
    def fixture(self):
        pose=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
        # Independent canonical first-person front-quad projection and Frozen
        # stopped setupGlintTexturing, unrelated to a particular native hash.
        semantic=dict(context="first-person",first_person=True,trusted_normals=True,
            model_pose=pose,normal_pose=[1,0,0,0,1,0,0,0,1],mesh_key=23,mesh_generation=7,
            clock_millis=12345,speed=0.0,strength=0.5,scaled_ticks=0)
        uploaded=dict(model_pose=pose.copy(),strength=0.5,uv_word_offset=12,
            foil_rows=[8*math.cos(math.pi/18),-8*math.sin(math.pi/18),0,0,
                       8*math.sin(math.pi/18),8*math.cos(math.pi/18),0,0],
            first_projected_uvs=[0,-1/96,0,0,1/96,0,1/96,-1/96],projected_uv_xxh32="1234abcd")
        draw=dict(mesh_key=23,mesh_generation=7,material_mode=4,
            program="vulkanic:builtin/direct_world_decal_foil_v1",instance_count=1,
            vertex_count=88,index_count=132,payload_bytes=752,texture_width=16,texture_height=16,
            texture_rgba_xxh32="23e4c5c8",sampler=dict(min_filter="Linear",mag_filter="Linear",
                mip_filter="Nearest",address_u="Repeat",address_v="Repeat",comparison=None),
            first_source_vertices=[dict(position=[x,y,0.53125],normal_packed=0x007f0000)
                for x,y in ((0,1),(0,0),(1,0),(1,1))],
            uploaded_instances=[uploaded])
        receipt=dict(schema="world-decal-submission-inputs-v1",complete=True,gpu_readback=False,
            gameplay_frame_id=10,correlation_id=11,gal_submission_id=20,deterministic_rendered_frame_index=5,
            semantic_instances=[semantic],draws=[draw])
        owner=dict(artifact_class="rust_vulkan_whole_frame_gameplay_correlation",gameplay_frame_id=10,
            correlation_id=11,gal_submission_id=20,deterministic_rendered_frame_index=5,
            rust_whole_frame_presenter=True,java_vulkan_frame_execution=False,same_acquired_presented_image=True,
            world_lod_instances=0,world_lod_route_selected=False,acquired_swapchain_image=30,
            presented_swapchain_image=30,present_completed_submission_id=20)
        ack=dict(renderedFrameIndex=5,wholeFramePresentationCorrelation=dict(gameplayFrameId=10,
            correlationId=11,submissionId=20,acquiredSwapchainImage=30,presentedSwapchainImage=30))
        timing=dict(enabled=True,complete=True,frameSequence=10,
            hand=dict(enabled=True,complete=True,frameSequence=10,scaledTicks=[0]))
        return [receipt,owner,ack,timing]

    def test_native_inputs_reject_wrong_ownership_frames_materials_and_projection(self):
        self.assertTrue(native_inputs(*self.fixture())["passed"])
        mutations=[
            (0,["complete"],False),(0,["complete"],1),(0,["gpu_readback"],True),
            (0,["gal_submission_id"],19),(0,["deterministic_rendered_frame_index"],True),
            (1,["gal_submission_id"],21),(1,["java_vulkan_frame_execution"],True),
            (1,["rust_whole_frame_presenter"],False),(1,["world_lod_instances"],False),
            (1,["world_lod_route_selected"],True),(1,["present_completed_submission_id"],19),
            (1,["presented_swapchain_image"],31),(2,["renderedFrameIndex"],6),
            (3,["hand","frameSequence"],9),(3,["hand","scaledTicks"],[1]),
            (3,["frameSequence"],9),
            (3,["hand","scaledTicks"],[False]),(3,["hand","complete"],False),
            (0,["semantic_instances",0,"first_person"],False),
            (0,["semantic_instances",0,"context"],"world"),
            (0,["semantic_instances",0,"normal_pose"],[0]*8),
            (0,["semantic_instances",0,"normal_pose"],[math.nan]*9),
            (0,["semantic_instances",0,"normal_pose"],[0]*9),
            (0,["semantic_instances",0,"speed"],0.5),
            (0,["semantic_instances",0,"mesh_generation"],8),
            (0,["draws",0,"program"],"vulkanic:builtin/direct_standard_item_foil_v1"),
            (0,["draws",0,"texture_rgba_xxh32"],"00000000"),
            (0,["draws",0,"sampler","address_u"],"ClampToEdge"),
            (0,["draws",0,"sampler","min_filter"],"Nearest"),
            (0,["draws",0,"payload_bytes"],48),
            (0,["draws",0,"first_source_vertices",0,"normal_packed"],0x00810000),
            (0,["draws",0,"first_source_vertices",1,"position"],[0.5,0,0.53125]),
            (0,["draws",0,"first_source_vertices"],[]),
            (0,["draws",0,"uploaded_instances",0,"model_pose",0],2),
            (0,["draws",0,"uploaded_instances",0,"uv_word_offset"],0),
            (0,["draws",0,"uploaded_instances",0,"first_projected_uvs"],[0,1,0,0,1,0,1,1]),
            (0,["draws",0,"uploaded_instances",0,"foil_rows"],[0]*8),
            (0,["draws"],[]),(0,["semantic_instances"],[None]),
        ]
        for document,path,value in mutations:
            with self.subTest(path=path,value=value):
                docs=copy.deepcopy(self.fixture());target=docs[document]
                for key in path[:-1]: target=target[key]
                target[path[-1]]=value
                with self.assertRaises(ValueError): native_inputs(*docs)

    def test_moving_native_input_uses_the_actual_hand_clock_and_uploaded_translation(self):
        docs=self.fixture();semantic=docs[0]["semantic_instances"][0]
        semantic.update(clock_millis=85032,speed=.5,scaled_ticks=340128)
        docs[3]["hand"]["scaledTicks"]=[340128]
        # A moving semantic clock cannot validate a stopped GPU transform.
        with self.assertRaises(ValueError): native_inputs(*docs,phase=10000)
        rows=docs[0]["draws"][0]["uploaded_instances"][0]["foil_rows"]
        rows[2]=-(340128%110000)/110000;rows[6]=(340128%30000)/30000
        self.assertTrue(native_inputs(*docs,phase=10000)["passed"])
        with self.assertRaises(ValueError): native_inputs(*docs)
        with self.assertRaises(ValueError): native_inputs(*docs,phase=40000)
        with self.assertRaises(ValueError): native_inputs(*docs,phase=True)
        for change in (lambda d:d[0]["semantic_instances"][0].update(clock_millis=85033),
                       lambda d:d[3]["hand"].update(scaledTicks=[340132]),
                       lambda d:d[0]["draws"][0]["uploaded_instances"][0]["foil_rows"].__setitem__(2,0)):
            wrong=copy.deepcopy(docs);change(wrong)
            with self.assertRaises(ValueError): native_inputs(*wrong,phase=10000)

    def test_back_face_first_requires_its_own_source_coordinates(self):
        docs=self.fixture();draw=docs[0]["draws"][0]
        draw["first_source_vertices"]=[dict(position=[x,y,0.46875],normal_packed=0x00810000)
            for x,y in ((1,1),(1,0),(0,0),(0,1))]
        # Front UVs cannot be retained when the first source face changes.
        with self.assertRaises(ValueError):native_inputs(*docs)
        draw["uploaded_instances"][0]["first_projected_uvs"]=[-1/96,-1/96,-1/96,0,0,0,0,-1/96]
        self.assertTrue(native_inputs(*docs)["passed"])

    def test_pixel_gate_checks_both_fixed_oracles_and_whole_region(self):
        frozen=Image.new("RGB",(1280,720),(100,90,80))
        for x,y,rgb in CLOCK_PROBES: frozen.putpixel((x,y),rgb)
        self.assertTrue(clock_pixels(frozen,frozen)["passed"])
        for index in range(len(CLOCK_PROBES)):
            wrong=frozen.copy();x,y,rgb=CLOCK_PROBES[index];wrong.putpixel((x,y),(0,0,0))
            self.assertFalse(clock_pixels(frozen,wrong)["passed"])
            self.assertFalse(clock_pixels(wrong,wrong)["passed"])
        wrong=frozen.copy();wrong.paste((0,0,0),(960,336,1280,720))
        for x,y,rgb in CLOCK_PROBES: wrong.putpixel((x,y),rgb)
        self.assertFalse(clock_pixels(frozen,wrong)["passed"],"matching probes cannot hide a missing held region")

    def test_compass_scope_requires_its_own_geometry_and_frozen_pixels(self):
        docs=self.fixture()
        with self.assertRaises(ValueError): native_inputs(*docs,context="held-compass")
        draw=docs[0]["draws"][0]
        draw.update(vertex_count=72,index_count=108,payload_bytes=624)
        self.assertTrue(native_inputs(*docs,context="held-compass")["passed"])
        with self.assertRaises(ValueError): native_inputs(*docs,context="held-clock")
        with self.assertRaises(ValueError): native_inputs(*docs,context="held-recovery-compass")
        compass=Image.new("RGB",(1280,720),(100,90,80))
        for x,y,rgb in COMPASS_PROBES: compass.putpixel((x,y),rgb)
        self.assertTrue(held_pixels(compass,compass,context="held-compass")["passed"])
        self.assertFalse(clock_pixels(compass,compass)["passed"])
        for x,y,_ in COMPASS_PROBES:
            wrong=compass.copy();wrong.putpixel((x,y),(0,0,0))
            self.assertFalse(held_pixels(compass,wrong,context="held-compass")["passed"])

    def test_moving_compass_requires_both_phases_and_each_material_group(self):
        from held_special_foil_reference import moving_held_change
        before=Image.new("RGB",(1280,720),(100,90,80));after=before.copy()
        for x,y,rgb in COMPASS_PROBES:
            before.putpixel((x,y),rgb)
            after.putpixel((x,y),tuple(v+10 for v in rgb))
        self.assertTrue(moving_held_change(before,before,after,after,context="held-compass")["passed"])
        self.assertFalse(moving_held_change(before,before,before,before,context="held-compass")["passed"])
        for group in ((0,1,2,9,10),(3,4,5,8,11),(6,7)):
            partial=after.copy()
            for i in group:
                x,y,rgb=COMPASS_PROBES[i];partial.putpixel((x,y),rgb)
            self.assertFalse(moving_held_change(before,before,partial,partial,context="held-compass")["passed"])
        with self.assertRaises(ValueError):
            moving_held_change(before,before,after,before,context="held-compass")
        missing=Image.new("RGB",(1280,720))
        self.assertFalse(held_pixels(missing,missing,context="held-compass",phase=10000)["passed"])
        self.assertFalse(held_pixels(before,before,context="held-recovery-compass",phase=10000)["passed"])

    def test_moving_recovery_requires_its_own_material_groups(self):
        from held_special_foil_reference import moving_held_change
        before=Image.new("RGB",(1280,720),(100,90,80));after=before.copy()
        for x,y,rgb in RECOVERY_PROBES:
            before.putpixel((x,y),rgb)
            after.putpixel((x,y),tuple(v+10 for v in rgb))
        self.assertTrue(moving_held_change(before,before,after,after,context="held-recovery-compass")["passed"])
        self.assertFalse(moving_held_change(before,before,before,before,context="held-recovery-compass")["passed"])
        for group in ((0,2,9,10),(3,4,5,7,8,11),(1,6)):
            partial=after.copy()
            for i in group:
                x,y,rgb=RECOVERY_PROBES[i];partial.putpixel((x,y),rgb)
            self.assertFalse(moving_held_change(before,before,partial,partial,context="held-recovery-compass")["passed"])
        with self.assertRaises(ValueError):
            moving_held_change(before,before,after,before,context="held-recovery-compass")
        self.assertFalse(held_pixels(before,before,context="held-compass",phase=10000)["passed"])

    def test_recovery_requires_its_own_pixels_and_preserves_scope_rejections(self):
        self.assertTrue(native_inputs(*self.fixture(),context="held-recovery-compass")["passed"])
        image=Image.new("RGB",(1280,720),(100,90,80))
        for x,y,rgb in RECOVERY_PROBES: image.putpixel((x,y),rgb)
        self.assertTrue(held_pixels(image,image,context="held-recovery-compass")["passed"])
        self.assertFalse(held_pixels(image,image,context="held-compass")["passed"])
        self.assertFalse(clock_pixels(image,image)["passed"])
        for x,y,_ in RECOVERY_PROBES:
            wrong=image.copy();wrong.putpixel((x,y),(0,0,0))
            self.assertFalse(held_pixels(image,wrong,context="held-recovery-compass")["passed"])
            self.assertFalse(held_pixels(wrong,wrong,context="held-recovery-compass")["passed"])

    def test_moving_pixels_and_temporal_change_reject_frozen_missing_and_partial_output(self):
        from held_special_foil_reference import moving_clock_change
        before=Image.new("RGB",(1280,720),(100,90,80));after=before.copy()
        for x,y,rgb in CLOCK_PROBES:
            before.putpixel((x,y),rgb)
            after.putpixel((x,y),tuple(v+10 for v in rgb))
        self.assertTrue(moving_clock_change(before,before,after,after)["passed"])
        self.assertFalse(moving_clock_change(before,before,before,before)["passed"])
        partial=before.copy()
        for x,y,rgb in CLOCK_PROBES[:4]:partial.putpixel((x,y),tuple(v+10 for v in rgb))
        self.assertFalse(moving_clock_change(before,before,partial,partial)["passed"])
        with self.assertRaises(ValueError):moving_clock_change(before,before,after,before)
        missing=Image.new("RGB",(1280,720),(0,0,0))
        self.assertFalse(held_pixels(missing,missing,context="held-clock",phase=10000)["passed"])
        wrong=after.copy();x,y,rgb=CLOCK_PROBES[0];wrong.putpixel((x,y),tuple(v+12 for v in rgb))
        self.assertFalse(held_pixels(after,wrong,context="held-clock",phase=40000)["passed"])
        self.assertFalse(held_pixels(after,after,context="held-compass",phase=10000)["passed"])
        with self.assertRaises(ValueError):held_pixels(after,after,context="held-clock",phase=True)

    def test_alignment_center_requires_correlated_completed_native_evidence(self):
        import graphics_harness as h
        from types import SimpleNamespace
        from unittest.mock import patch
        docs=self.fixture();native,owner,ack,timing=docs
        native["semantic_instances"][0].update(clock_millis=85032,speed=.5,scaled_ticks=340128)
        timing["hand"]["scaledTicks"]=[340128]
        native["draws"][0]["uploaded_instances"][0]["foil_rows"][2]=-(340128%110000)/110000
        native["draws"][0]["uploaded_instances"][0]["foil_rows"][6]=(340128%30000)/30000
        timing["sourceEvidence"]=dict(enabled=True,complete=True,schema="gui-foil-frame-sources-v1",frameSequence=10,
            sources=[dict(sprite="minecraft:item/clock_00",positions=[0,1,.53125,0,0,.53125,1,0,.53125,1,1,.53125],atlasUvs=[0,0,0,1,1,1,1,0])])
        screenshot="/capture/01_initial.png"
        ack.update(status="captured",screenshot=screenshot)
        artifact=dict(mode=dict(name="current-rust-vulkan-shaders-off"),capture=dict(exit_code=0),
            validation=dict(complete=True,crash_free=True,device_loss_free=True,vulkan_validation_clean=True,
                            orphan_process_detected=False,rss_guard_triggered=False))
        lookup={"artifact.json":artifact,"01_initial.png.foil-timing.json":timing,
                "01_initial.png.decal-inputs.json":native,"gameplay-correlation-frame-10.json":owner,
                "capture_request_01_initial.ack.json":ack}
        result=SimpleNamespace(success=True,exit_code=0,timed_out=False,artifact_path="/run/artifact.json")
        args=SimpleNamespace(flat_item_foil_phase=10000,selected_hotbar_slot=2,hotbar_item_fixture="special-foil")
        with patch.object(h,"read_json",side_effect=lambda p:lookup[p.name]), patch.object(h,"deterministic_capture_document",
                return_value=dict(captures=[dict(screenshot=screenshot)])):
            self.assertEqual(10128,h.held_special_foil_phase_center(result,args))
            owner["gal_submission_id"]=19
            with self.assertRaises(ValueError):h.held_special_foil_phase_center(result,args)
            owner["gal_submission_id"]=20
            artifact["validation"]["vulkan_validation_clean"]=False
            with self.assertRaises(ValueError):h.held_special_foil_phase_center(result,args)
            artifact["validation"]["vulkan_validation_clean"]=True
            timing["sourceEvidence"]["frameSequence"]=9
            with self.assertRaises(ValueError):h.held_special_foil_phase_center(result,args)

    def test_phase_alignment_launch_is_explicit_ordered_and_requires_observed_center(self):
        import graphics_harness as h
        import tempfile
        from harness_test_support import fake_repo
        args=h.parse_args(["capture","--align-held-special-foil-phase",
            "--mode","current-rust-vulkan-shaders-off","--mode","frozen-opengl-shaders-off",
            "--hotbar-item-fixture","special-foil","--selected-hotbar-slot","2",
            "--gui-resource-pack-scenario","special-item-foil-moving",
            "--world-resource-reload","--rust-full-gameplay-attachments"])
        h.validate_fixture_combinations(args)
        args.special_foil_context="held-clock"
        args.world_static_terrain_scenario="real-world"
        h.validate_fixture_combinations(args)
        args.special_foil_context="held-compass"
        args.selected_hotbar_slot=3
        h.validate_fixture_combinations(args)
        args.special_foil_context="held-recovery-compass"
        with self.assertRaises(ValueError): h.validate_fixture_combinations(args)
        args.hotbar_item_fixture="recovery-foil"
        h.validate_fixture_combinations(args)
        for field,value in (("mode",list(reversed(args.mode))),("repetitions",2),
                            ("gui_resource_pack_scenario","special-item-foil-pattern"),
                            ("selected_hotbar_slot",1),("rust_full_gameplay_attachments",False)):
            wrong=copy.deepcopy(args);setattr(wrong,field,value)
            with self.assertRaises(ValueError): h.validate_fixture_combinations(wrong)
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for name in args.mode:
                target=fake_repo(root,name);mode=next(m for m in h.MATRIX_MODES if m.name==name)
                if name.startswith("frozen"):
                    with self.assertRaises(ValueError): h.build_capture_command(target,mode,root/name/"capture","correctness",args,"capture")
                    args._held_special_foil_phase_center=10188
                _,env=h.build_capture_command(target,mode,root/name/"capture","correctness",args,"capture")
                self.assertIn("handItemFoilTiming=true",env["JAVA_TOOL_OPTIONS"])
                if name.startswith("frozen"):
                    self.assertIn("graphicsAuditHandFoilPhaseCenter=10188",env["JAVA_TOOL_OPTIONS"])
                else:
                    self.assertNotIn("graphicsAuditHandFoilPhaseCenter",env["JAVA_TOOL_OPTIONS"])

    def test_cli_requires_explicit_held_scope_and_complete_fixture(self):
        import graphics_harness as h
        args=h.parse_args(["capture","--special-foil-context","held-clock","--hotbar-item-fixture","special-foil",
            "--gui-resource-pack-scenario","special-item-foil-pattern","--selected-hotbar-slot","2",
            "--world-static-terrain-scenario","real-world","--world-resource-reload","--rust-full-gameplay-attachments"])
        h.validate_fixture_combinations(args)
        for field,value in (("selected_hotbar_slot",1),("flat_item_gui_scale",2),
            ("gui_resource_pack_scenario","special-item-foil-moving"),("world_resource_reload",False),
            ("rust_full_gameplay_attachments",False),("capture_world_time",6001)):
            wrong=copy.deepcopy(args);setattr(wrong,field,value)
            with self.assertRaises(ValueError): h.validate_fixture_combinations(wrong)
        self.assertEqual(h.parse_args(["capture"]).special_foil_context,"gui")
        args.special_foil_context="held-compass"
        with self.assertRaises(ValueError): h.validate_fixture_combinations(args)
        args.selected_hotbar_slot=3
        h.validate_fixture_combinations(args)
        args.special_foil_context="held-recovery-compass"
        with self.assertRaises(ValueError): h.validate_fixture_combinations(args)
        args.hotbar_item_fixture="recovery-foil"
        h.validate_fixture_combinations(args)
        args.special_foil_context="held-compass"
        with self.assertRaises(ValueError): h.validate_fixture_combinations(args)


if __name__ == "__main__": unittest.main()
