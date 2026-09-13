import sys
import unittest
from unittest import mock
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))
from shield_animation_reference import (FROZEN_PHASE_FOUR, FROZEN_PHASE_NINE_INTERPOLATED, FROZEN_PHASE_ZERO_INTERPOLATED,
    compare_phase_four_images, compare_phase_images)
from shield_item_reference import BASE_PROBES
import graphics_harness as harness


class ShieldAnimationReferenceTest(unittest.TestCase):
    def test_normal_admission_requires_unflagged_route_and_exact_exclusive_presentation(self):
        pair = {"current_artifact":"current/artifact.json", "baseline_artifact":"frozen/artifact.json"}
        phase = {"passed":True,"pairs":[{"passed":True,"uploads":[{"native":{
            "presentation_correlation":7,"presentation_submission":9}}]}]}
        receipt = {"schema":"rust-owned-shield-atlas-v1","normalRoute":True,"privateFlagsPresent":False}
        doc = {"shieldAtlasAdmission":receipt}
        health = {"complete":True,"crash_free":True,"device_loss_free":True,
                  "rss_guard_triggered":False,"orphan_process_detected":False,"vulkan_validation_clean":True}
        current = {"mode":{"name":"current-rust-vulkan-shaders-off"},"validation":dict(health)}
        frozen = {"mode":{"name":"frozen-opengl-shaders-off"},"validation":dict(health)}
        owner = {"correlation_id":7,"gal_submission_id":9,"rust_whole_frame_presenter":True,
                 "java_vulkan_frame_execution":False,"same_acquired_presented_image":True,
                 "world_lod_instances":0,"world_lod_route_selected":False}
        def read(path):
            return owner if path.name.startswith("gameplay-correlation") else current if str(path).startswith("current") else frozen
        with mock.patch.object(harness,"deterministic_capture_document",return_value=doc), \
             mock.patch.object(harness,"read_json",side_effect=read):
            def check(): return harness.shield_animation_normal_admission({"pairs":[pair]},phase)["passed"]
            self.assertTrue(check())
            for obj,key,value in ((receipt,"normalRoute",False),(receipt,"normalRoute",1),
                    (receipt,"privateFlagsPresent",True),(receipt,"privateFlagsPresent",0),
                    (owner,"correlation_id",8),(owner,"gal_submission_id",10),
                    (owner,"java_vulkan_frame_execution",True),(owner,"rust_whole_frame_presenter",False),
                    (owner,"same_acquired_presented_image",False),(owner,"world_lod_instances",1),
                    (current["validation"],"vulkan_validation_clean",False),
                    (current["validation"],"rss_guard_triggered",True),
                    (frozen["validation"],"crash_free",False),
                    (frozen["mode"],"name","frozen-vulkan-shaders-off")):
                with self.subTest(key=key,value=value):
                    before=obj[key];obj[key]=value
                    self.assertFalse(check());obj[key]=before
            del doc["shieldAtlasAdmission"]
            self.assertFalse(check())  # Historical private evidence cannot become normal admission.
            doc["shieldAtlasAdmission"]=receipt
            phase["pairs"][0]["passed"]=False
            self.assertFalse(check())
            self.assertFalse(harness.shield_animation_normal_admission({"pairs":[]},phase)["passed"])

    def test_idle_pixels_cannot_supply_a_blocking_pose_oracle(self):
        image = self.fixture(True)
        with self.assertRaises(ValueError):
            compare_phase_images(image, image, interpolated=True, phase=4, pose="blocking")
        with self.assertRaises(ValueError):
            compare_phase_images(image, image, interpolated=True, phase=4, pose="unknown")

    def test_blocking_diagnostic_requires_authored_pose_and_actual_use_receipt(self):
        receipt = {"fixture":"held-shield-v1","selectedSlot":1,"mainHand":"minecraft:shield","count":1,
                   "foil":False,"usingItem":True,"speed":0.0,"strength":0.5,"complete":True}
        doc = {"hotbarItemFixture":"shield","shieldPose":"blocking","shieldFoilFixture":receipt,
               "blockDisplayAnimationAtCapture":{"frame":1,"subFrame":1}}
        pair = {"baseline_artifact":"frozen/artifact.json","current_artifact":"rust/artifact.json",
                "baseline_image":"frozen.png","current_image":"rust.png"}
        with mock.patch.object(harness,"deterministic_capture_document",return_value=doc),              mock.patch.object(harness,"latest_capture_meta_path",return_value=Path("meta")),              mock.patch.object(harness,"read_key_values",return_value={
                 "forced_option_guiScale":"3","gui_resource_pack_scenario":"shield-animation-interpolated"}),              mock.patch.object(harness,"block_display_animation_upload_equivalence",return_value={
                 "passed":True,"native":{"texture":4260828946,"retained_frame":1,"retained_subframe":1,"retained_sheet_frame":0}}),              mock.patch("shield_animation_reference.compare_phase_paths",return_value={"passed":True}) as pixels:
            def check():
                return harness.model_item_foil_parity_report({"pairs":[pair]},"shield","shield-animation-interpolated","blocking")
            self.assertTrue(check()["phase_diagnostic"]["passed"])
            self.assertFalse(check()["passed"])
            self.assertEqual(pixels.call_args.kwargs["pose"],"blocking")
            receipt["usingItem"] = False
            self.assertFalse(check()["phase_diagnostic"]["passed"])
            receipt["usingItem"] = True
            doc["shieldPose"] = "idle"
            self.assertFalse(check()["phase_diagnostic"]["passed"])
            doc["shieldPose"] = "blocking-offhand"
            receipt["usingItem"] = True
            def offhand():
                return harness.model_item_foil_parity_report({"pairs":[pair]},"shield","shield-animation-interpolated","blocking-offhand")
            for hand in (None,"NONE","MAIN_HAND"):
                doc["shieldUseHand"] = hand
                self.assertFalse(offhand()["phase_diagnostic"]["passed"])
            doc["shieldUseHand"] = "OFF_HAND"
            self.assertTrue(offhand()["phase_diagnostic"]["passed"])
            self.assertFalse(offhand()["passed"])
            self.assertEqual(pixels.call_args.kwargs["pose"],"blocking-offhand")

    def test_mixed_phase_uses_each_materials_authored_schedule(self):
        from shield_animation_scenarios import shield_animation_phase, shield_animation_frames
        for role, frame, sub in (("dye",1,1),("border",0,4),("cross",1,2),("body",0,4)):
            frames = shield_animation_frames("mixed", role)
            self.assertEqual(shield_animation_phase(frame,sub,frames),4)
            self.assertEqual(sum(duration for _,duration in frames),17)
        self.assertNotEqual(shield_animation_phase(1,2,shield_animation_frames("mixed","cross")),
                            shield_animation_phase(1,2,shield_animation_frames("combined","cross")))
        with self.assertRaises(ValueError): shield_animation_frames("mixed", "plain")
        with self.assertRaises(ValueError): shield_animation_frames("unknown", "unknown")

    def test_authored_schedule_covers_every_subframe_without_confusing_repeated_sheets(self):
        from shield_animation_scenarios import shield_animation_phase
        expected = [list(range(0,3)), list(range(3,8)), list(range(8,10)), list(range(10,17))]
        for frame, phases in enumerate(expected):
            self.assertEqual([shield_animation_phase(frame, sub) for sub in range(len(phases))], phases)
        for frame, sub in ((True,0),(0,False),(1.0,0),(0,"0"),(-1,0),(4,0),(0,-1),(0,3),(1,5),(2,2),(3,7)):
            with self.assertRaises(ValueError): shield_animation_phase(frame,sub)

    def test_material_selection_cannot_borrow_another_materials_pixel_oracle(self):
        from shield_animation_reference import FROZEN_VARIANT_PROBES
        image=Image.new("RGB",(1280,720))
        for _,x,y,pixels in FROZEN_VARIANT_PROBES[(True,False,True,4)]:
            patch=Image.new("RGB",(3,3));patch.putdata([tuple(pixel) for pixel in pixels])
            image.paste(patch,(x-1,y-1))
        for material in ("body","dye","border","unknown","plain"):
            with self.subTest(material=material):
                try:
                    result=compare_phase_images(image,image,interpolated=True,phase=4,patterned=True,material=material)
                except ValueError:
                    continue  # Unknown material/phase has no oracle.
                self.assertFalse(result["passed"])

    def test_recorded_materials_require_their_own_frozen_pixels(self):
        from shield_animation_reference import FROZEN_MATERIAL_PROBES, FROZEN_VARIANT_PROBES
        def picture(probes):
            image=Image.new("RGB",(1280,720))
            for _,x,y,pixels in probes:
                patch=Image.new("RGB",(3,3));patch.putdata([tuple(pixel) for pixel in pixels])
                image.paste(patch,(x-1,y-1))
            return image
        for (material,foil,interpolated,phase),probes in FROZEN_MATERIAL_PROBES.items():
            with self.subTest(material=material,foil=foil,phase=phase):
                image=picture(probes)
                kwargs=dict(material=material,foil=foil,interpolated=interpolated,phase=phase,patterned=True)
                result=compare_phase_images(image,image,**kwargs)
                self.assertTrue(result["passed"])
                self.assertFalse(result["capability_admitted"])
                cross=picture(FROZEN_VARIANT_PROBES[(True,foil,True,4)])
                self.assertFalse(compare_phase_images(cross,cross,**kwargs)["passed"])
                blank=Image.new("RGB",(1280,720))
                self.assertFalse(compare_phase_images(blank,blank,**kwargs)["passed"])

    def test_every_recorded_variant_requires_nonblank_frozen_pixels(self):
        from shield_animation_reference import FROZEN_VARIANT_PROBES
        for (patterned,foil,interpolated,phase),probes in FROZEN_VARIANT_PROBES.items():
            with self.subTest(patterned=patterned,foil=foil,phase=phase):
                blank=Image.new("RGB",(1280,720));image=blank.copy()
                for _,x,y,pixels in probes:
                    patch=Image.new("RGB",(3,3));patch.putdata([tuple(pixel) for pixel in pixels])
                    image.paste(patch,(x-1,y-1))
                kwargs=dict(patterned=patterned,foil=foil,interpolated=interpolated,phase=phase)
                result=compare_phase_images(image,image,**kwargs)
                self.assertTrue(result["passed"])
                self.assertFalse(result["capability_admitted"])
                self.assertFalse(compare_phase_images(blank,blank,**kwargs)["passed"])

    def test_animated_pattern_rejects_static_cross_and_missing_layers(self):
        from shield_animation_reference import FROZEN_VARIANT_PROBES
        from shield_item_reference import PATTERN_PROBES
        def picture(probes):
            image = Image.new("RGB",(1280,720))
            for _,x,y,pixels in probes:
                patch=Image.new("RGB",(3,3));patch.putdata([tuple(pixel) for pixel in pixels]);image.paste(patch,(x-1,y-1))
            return image
        frozen=picture(FROZEN_VARIANT_PROBES[(True,False,True,4)])
        self.assertTrue(compare_phase_images(frozen,frozen,interpolated=True,phase=4,patterned=True)["passed"])
        stale=picture(PATTERN_PROBES)
        self.assertFalse(compare_phase_images(stale,stale,interpolated=True,phase=4,patterned=True)["passed"])
        missing=frozen.copy();missing.paste((0,0,0),(387,673,390,676))
        self.assertFalse(compare_phase_images(frozen,missing,interpolated=True,phase=4,patterned=True)["passed"])

    def test_foil_requires_its_own_frozen_pixels_and_known_phase(self):
        from shield_animation_reference import FROZEN_VARIANT_PROBES
        frozen = Image.new("RGB", (1280,720))
        for _,x,y,pixels in FROZEN_VARIANT_PROBES[(False,True,True,4)]:
            patch = Image.new("RGB", (3,3)); patch.putdata(pixels)
            frozen.paste(patch,(x-1,y-1))
        result = compare_phase_images(frozen,frozen,interpolated=True,phase=4,foil=True)
        self.assertTrue(result["passed"])
        self.assertFalse(result["capability_admitted"])
        plain = self.fixture(True)
        self.assertFalse(compare_phase_images(plain,plain,interpolated=True,phase=4,foil=True)["passed"])
        for phase,interpolated in ((0,True),(4,False),(True,True)):
            with self.assertRaises(ValueError):
                compare_phase_images(frozen,frozen,interpolated=interpolated,phase=phase,foil=True)

    def test_combined_diagnostic_requires_every_material_at_one_presentation(self):
        names = [harness.SHIELD_ANIMATION_SPRITES[role] for role in harness.shield_animation_materials("combined")]
        receipt = {"fixture":"held-shield-patterns-foil-v1","selectedSlot":1,"mainHand":"minecraft:shield",
                   "count":1,"foil":True,"usingItem":False,"complete":True,"speed":0.0,"strength":0.5,
                   "baseColor":"yellow","patterns":"minecraft:cross:red,minecraft:border:blue"}
        doc = {"hotbarItemFixture":"shield-patterns-foil", "shieldPatternFixture":receipt,
               "blockDisplayAnimationAtCapture":{"kind":"selected-atlas-sprites","sprites":[
                   {"spriteName":name,"frame":1,"subFrame":1} for name in names]}}
        pair = {"baseline_artifact":"frozen/artifact.json","current_artifact":"rust/artifact.json",
                "baseline_image":"frozen.png","current_image":"rust.png"}
        def upload(**changes):
            return {"passed":True,"native":{"texture":4260828946,"retained_frame":1,"retained_subframe":1,"retained_sheet_frame":0,
                    "presentation_correlation":5,"presentation_submission":8,"generation":3,**changes}}
        def check():
            return harness.shield_animation_phase_diagnostic({"pairs":[pair]}, "shield-patterns-foil",
                                                             "shield-combined-animation-interpolated")
        with mock.patch.object(harness,"deterministic_capture_document",return_value=doc), \
             mock.patch.object(harness,"latest_capture_meta_path",return_value=Path("meta")), \
             mock.patch.object(harness,"read_key_values",return_value={"forced_option_guiScale":"3",
                 "gui_resource_pack_scenario":"shield-combined-animation-interpolated"}), \
             mock.patch.object(harness,"block_display_animation_upload_equivalence",return_value=upload()) as source, \
             mock.patch("shield_animation_reference.compare_phase_paths",return_value={"passed":True}) as pixels:
            self.assertTrue(check()["passed"])
            self.assertEqual([call.kwargs["required_sprite"] for call in source.call_args_list], names)
            self.assertEqual(pixels.call_args.kwargs["material"], "combined")
            for bad in ({"presentation_correlation":6}, {"presentation_submission":9}, {"generation":4},
                        {"retained_subframe":2}):
                source.side_effect = [upload(), upload(), upload(**bad), upload()]
                pixels.reset_mock()
                self.assertFalse(check()["passed"])
                pixels.assert_not_called()
            source.side_effect = None
            doc["blockDisplayAnimationAtCapture"]["sprites"].pop()
            self.assertFalse(check()["passed"])

    def test_variant_diagnostic_checks_authored_components_and_selected_material(self):
        for material,prefix,sprite in (
                ("plain","shield-animation","minecraft:entity/shield_base_nopattern"),
                ("cross","shield-pattern-animation","minecraft:entity/shield/cross"),
                ("body","shield-body-animation","minecraft:entity/shield_base"),
                ("dye","shield-dye-animation","minecraft:entity/shield/base"),
                ("border","shield-border-animation","minecraft:entity/shield/border")):
            patterned = material != "plain"
            for foil in (False,True):
                fixture = "shield"+("-patterns" if patterned else "")+("-foil" if foil else "")
                scenario = prefix+"-interpolated"
                receipt = {"fixture":"held-shield-v1","selectedSlot":1,"mainHand":"minecraft:shield",
                           "count":1,"foil":foil,"usingItem":False,"complete":True}
                if patterned:
                    receipt.update(fixture="held-shield-patterns-foil-v1" if foil else "held-shield-patterns-v1",
                                   baseColor="yellow",patterns="minecraft:cross:red,minecraft:border:blue")
                if foil or not patterned: receipt.update(speed=0.0,strength=0.5)
                doc = {"hotbarItemFixture":fixture,"shieldPatternFixture" if patterned else "shieldFoilFixture":receipt,
                       "blockDisplayAnimationAtCapture":{"frame":1,"subFrame":1}}
                pair = {"baseline_artifact":"frozen/artifact.json","current_artifact":"rust/artifact.json",
                        "baseline_image":"frozen.png","current_image":"rust.png"}
                with self.subTest(fixture=fixture), \
                     mock.patch.object(harness,"deterministic_capture_document",return_value=doc), \
                     mock.patch.object(harness,"latest_capture_meta_path",return_value=Path("meta")), \
                     mock.patch.object(harness,"read_key_values",return_value={
                         "forced_option_guiScale":"3","gui_resource_pack_scenario":scenario}), \
                     mock.patch.object(harness,"block_display_animation_upload_equivalence",return_value={
                         "passed":True,"native":{"texture":4260828946,"retained_frame":1,"retained_subframe":1,"retained_sheet_frame":0}}) as upload, \
                     mock.patch("shield_animation_reference.compare_phase_paths",return_value={"passed":True}) as pixels:
                    result = harness.model_item_foil_parity_report({"pairs":[pair]},fixture,scenario)
                    self.assertTrue(result["phase_diagnostic"]["passed"])
                    self.assertFalse(result["passed"])
                    self.assertEqual(upload.call_args.kwargs["required_sprite"],sprite)
                    self.assertEqual(pixels.call_args.kwargs["material"],material)
                    self.assertEqual(pixels.call_args.kwargs["foil"],foil)
                    self.assertEqual(pixels.call_args.kwargs["patterned"],patterned)
                    self.assertEqual(upload.call_args.kwargs["required_texture"],4260828946)
                    self.assertEqual(upload.call_args.kwargs["require_retained_phase"],scenario.endswith("-interpolated"))
                    if not scenario.endswith("-interpolated"):
                        native = upload.return_value["native"]
                        native["retained_frame"] = 0  # Different entry, same accepted sheet.
                        self.assertTrue(harness.model_item_foil_parity_report(
                            {"pairs":[pair]},fixture,scenario)["phase_diagnostic"]["passed"])
                        native["retained_sheet_frame"] = 2  # Stale selected source must fail.
                        self.assertFalse(harness.model_item_foil_parity_report(
                            {"pairs":[pair]},fixture,scenario)["phase_diagnostic"]["passed"])
                        native["retained_sheet_frame"] = 0
                    pixels.reset_mock()
                    receipt["foil"] = not foil
                    self.assertFalse(harness.model_item_foil_parity_report(
                        {"pairs":[pair]},fixture,scenario)["phase_diagnostic"]["passed"])
                    pixels.assert_not_called()

    def fixture(self, interpolated):
        image = Image.new("RGB", (1280, 720))
        for (_, x, y, _), color in zip(BASE_PROBES, FROZEN_PHASE_FOUR[interpolated]):
            image.paste(color, (x-1, y-1, x+2, y+2))
        return image

    def test_both_independent_frozen_anchors_pass_without_admitting_capability(self):
        for interpolated in (False, True):
            source = self.fixture(interpolated)
            result = compare_phase_four_images(source, source, interpolated=interpolated)
            self.assertTrue(result["passed"])
            self.assertFalse(result["capability_admitted"])

    def test_matching_blank_or_wrong_phase_images_fail_frozen_anchor(self):
        blank = Image.new("RGB", (1280, 720))
        for interpolated in (False, True):
            self.assertFalse(compare_phase_four_images(blank, blank, interpolated=interpolated)["passed"])
            wrong = self.fixture(not interpolated)
            self.assertFalse(compare_phase_four_images(wrong, wrong, interpolated=interpolated)["passed"])

    def test_stale_gui_raster_and_missing_held_geometry_fail_independently(self):
        frozen = self.fixture(True)
        for index in (0, 5):
            current = frozen.copy()
            _, x, y, _ = BASE_PROBES[index]
            current.paste((0, 0, 255), (x-1, y-1, x+2, y+2))
            self.assertFalse(compare_phase_four_images(frozen, current, interpolated=True)["passed"])

    def test_region_difference_outside_probes_is_not_ignored(self):
        frozen = self.fixture(False)
        current = frozen.copy()
        current.paste((255, 255, 255), (900, 450, 1100, 590))
        self.assertFalse(compare_phase_four_images(frozen, current, interpolated=False)["passed"])

    def test_repeated_source_phase_has_its_own_frozen_oracle(self):
        repeated = Image.new("RGB", (1280, 720))
        for (_, x, y, _), color in zip(BASE_PROBES, FROZEN_PHASE_NINE_INTERPOLATED):
            repeated.paste(color, (x-1, y-1, x+2, y+2))
        self.assertTrue(compare_phase_images(repeated, repeated, interpolated=True, phase=9)["passed"])
        wrong = self.fixture(True)
        self.assertFalse(compare_phase_images(wrong, wrong, interpolated=True, phase=9)["passed"])
        for phase, interpolated in ((9, False), (0, False), (16, True), (True, True)):
            with self.assertRaises(ValueError):
                compare_phase_images(repeated, repeated, interpolated=interpolated, phase=phase)

    def test_cycle_boundary_uses_the_independent_first_declared_frame_not_sheet_zero(self):
        boundary = Image.new("RGB", (1280, 720))
        for (_, x, y, _), color in zip(BASE_PROBES, FROZEN_PHASE_ZERO_INTERPOLATED):
            boundary.paste(color, (x-1, y-1, x+2, y+2))
        self.assertTrue(compare_phase_images(boundary, boundary, interpolated=True, phase=0)["passed"])
        sheet_zero = self.fixture(False)
        self.assertFalse(compare_phase_images(sheet_zero, sheet_zero, interpolated=True, phase=0)["passed"])

    def test_diagnostic_requires_correlated_right_owner_and_never_admits_whole_animation(self):
        fixture = {"fixture": "held-shield-v1", "selectedSlot": 1,
                   "mainHand": "minecraft:shield", "count": 1, "foil": False,
                   "usingItem": False, "speed": 0.0, "strength": 0.5, "complete": True}
        doc = {"hotbarItemFixture": "shield", "shieldFoilFixture": fixture,
               "blockDisplayAnimationAtCapture": {"frame": 1, "subFrame": 1}}
        pair = {"baseline_artifact": "frozen/artifact.json", "current_artifact": "rust/artifact.json",
                "baseline_image": "frozen.png", "current_image": "rust.png"}
        scenario = "shield-animation-interpolated"
        with mock.patch.object(harness, "deterministic_capture_document", return_value=doc), \
             mock.patch.object(harness, "latest_capture_meta_path", return_value=Path("meta")), \
             mock.patch.object(harness, "read_key_values", return_value={
                 "forced_option_guiScale": "3", "gui_resource_pack_scenario": scenario}), \
             mock.patch.object(harness, "block_display_animation_upload_equivalence") as upload, \
             mock.patch("shield_animation_reference.compare_phase_paths",
                        return_value={"passed": True}) as pixels:
            for evidence in (None, {"passed": False}, {"passed": True, "native": {
                    "texture": 1, "retained_frame": 1, "retained_subframe": 1}}, {"passed": True, "native": {
                    "texture": 4260828946, "retained_frame": 1, "retained_subframe": 0}}):
                upload.return_value = evidence
                result = harness.model_item_foil_parity_report({"pairs": [pair]}, "shield", scenario)
                self.assertFalse(result["phase_diagnostic"]["passed"])
                pixels.assert_not_called()
            upload.return_value = {"passed": True, "native": {
                "texture": 4260828946, "retained_frame": 1, "retained_subframe": 1}}
            result = harness.model_item_foil_parity_report({"pairs": [pair]}, "shield", scenario)
            self.assertTrue(result["phase_diagnostic"]["passed"])
            self.assertFalse(result["passed"])
            self.assertEqual(upload.call_args.kwargs["required_texture"], 4260828946)
            doc["blockDisplayAnimationAtCapture"]["subFrame"] = 2
            result = harness.model_item_foil_parity_report({"pairs": [pair]}, "shield", scenario)
            self.assertFalse(result["phase_diagnostic"]["passed"])
            # An empty catch-up tick may advance the clock but must not
            # replace proof of which pixels the accepted submission retained.
            doc["blockDisplayAnimationAtCapture"].update(frame=2, subFrame=1)
            upload.return_value = {"passed": True, "native": {
                "texture": 4260828946, "frame": 3, "subframe": 0,
                "retained_frame": 2, "retained_subframe": 1}}
            result = harness.model_item_foil_parity_report({"pairs": [pair]}, "shield", scenario)
            self.assertTrue(result["phase_diagnostic"]["passed"])
            self.assertFalse(result["passed"])
            self.assertEqual(pixels.call_args.kwargs["phase"], 9)
            doc["blockDisplayAnimationAtCapture"].update(frame=0, subFrame=0)
            native = upload.return_value["native"]
            native.update(retained_frame=0, retained_subframe=0)
            for tick, passed in ((0, False), (16, False), (17, True), (136, True)):
                native["retained_tick"] = tick
                result = harness.model_item_foil_parity_report({"pairs": [pair]}, "shield", scenario)
                self.assertEqual(result["phase_diagnostic"]["passed"], passed)
                self.assertFalse(result["passed"])


if __name__ == "__main__":
    unittest.main()
