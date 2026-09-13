"""Shared resource-only animation inputs; never substitute static parity for timing."""
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "Common"))
import capture_runner
import graphics_harness


class ShieldAnimationPackTests(unittest.TestCase):
    def test_different_period_pack_requires_joint_alignment(self):
        schedules = {
            "entity/shield/base": [(2,3),(0,5),(2,2),(1,7)],
            "entity/shield/border": [(1,10),(2,4),(0,14),(1,6)],
            "entity/shield/cross": [(0,6),(1,21),(0,9),(2,15)],
            "entity/shield_base": [(1,6),(0,4),(2,7)],
        }
        with tempfile.TemporaryDirectory() as root:
            spec, = capture_runner.gui_resource_pack_specs("shield-periods-animation-interpolated")
            target = Path(root) / "pack"
            capture_runner.write_gui_resource_pack(target, spec)
            for name, schedule in schedules.items():
                path = "assets/minecraft/textures/" + name + ".png"
                meta = json.loads((target / (path + ".mcmeta")).read_text())["animation"]
                self.assertEqual([(f["index"], f["time"]) for f in meta["frames"]], schedule)
                self.assertEqual((target / path).read_bytes(), capture_runner.shield_animation_png(path))
        durations = [sum(ticks for _, ticks in schedule) for schedule in schedules.values()]
        self.assertEqual(durations, [17,34,51,17])
        self.assertTrue(all(106 % duration == 4 for duration in durations))
        self.assertFalse(all(123 % duration == 4 for duration in durations))

    def test_mixed_materials_keep_distinct_authored_schedules_and_source_masks(self):
        expected = {
            "entity/shield/base": [(2,3),(0,5),(2,2),(1,7)],
            "entity/shield/border": [(1,5),(2,2),(0,7),(1,3)],
            "entity/shield/cross": [(0,2),(1,7),(0,3),(2,5)],
            "entity/shield_base": [(1,6),(0,4),(2,7)],
        }
        with tempfile.TemporaryDirectory() as root:
            spec, = capture_runner.gui_resource_pack_specs("shield-mixed-animation-interpolated")
            target = Path(root) / "pack"
            capture_runner.write_gui_resource_pack(target, spec)
            self.assertEqual(len(list(target.rglob("*.png"))), 4)
            for name, schedule in expected.items():
                path = "assets/minecraft/textures/" + name + ".png"
                metadata = json.loads((target / (path + ".mcmeta")).read_text())["animation"]
                self.assertEqual([(f["index"], f["time"]) for f in metadata["frames"]], schedule)
                self.assertTrue(metadata["interpolate"])
                self.assertEqual((target / path).read_bytes(), capture_runner.shield_animation_png(path))

    def test_combined_pack_contains_each_original_mask_without_models(self):
        with tempfile.TemporaryDirectory() as root:
            spec, = capture_runner.gui_resource_pack_specs("shield-combined-animation-interpolated")
            target = Path(root) / "pack"
            capture_runner.write_gui_resource_pack(target, spec)
            textures = ["assets/minecraft/textures/" + name + ".png" for name in (
                "entity/shield/base", "entity/shield/border", "entity/shield/cross", "entity/shield_base")]
            self.assertEqual({p.relative_to(target).as_posix() for p in target.rglob("*") if p.is_file()},
                             {"pack.mcmeta", *textures, *(name + ".mcmeta" for name in textures)})
            for texture in textures:
                self.assertEqual((target / texture).read_bytes(), capture_runner.shield_animation_png(texture))
                metadata = json.loads((target / (texture + ".mcmeta")).read_text())["animation"]
                self.assertTrue(metadata["interpolate"])
                self.assertEqual([(f["index"], f["time"]) for f in metadata["frames"]], [(2,3),(0,5),(2,2),(1,7)])

    def test_selected_material_animation_preserves_source_mask_and_shading(self):
        for prefix,texture in (
                ("shield-pattern-animation","assets/minecraft/textures/entity/shield/cross.png"),
                ("shield-body-animation","assets/minecraft/textures/entity/shield_base.png"),
                ("shield-dye-animation","assets/minecraft/textures/entity/shield/base.png"),
                ("shield-border-animation","assets/minecraft/textures/entity/shield/border.png")):
            source = Path(__file__).resolve().parents[2] / "src/main/resources" / texture
            with Image.open(source) as original:
                original = original.convert("RGBA")
                for scenario in (prefix,prefix+"-interpolated"):
                    with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as root:
                        spec, = capture_runner.gui_resource_pack_specs(scenario)
                        target = Path(root) / "pack"
                        capture_runner.write_gui_resource_pack(target, spec)
                        self.assertEqual({p.relative_to(target).as_posix() for p in target.rglob("*") if p.is_file()},
                                         {"pack.mcmeta", texture, texture+".mcmeta"})
                        meta = json.loads((target/(texture+".mcmeta")).read_text())["animation"]
                        self.assertEqual(meta["interpolate"],scenario.endswith("-interpolated"))
                        self.assertEqual([(f["index"],f["time"]) for f in meta["frames"]],[(2,3),(0,5),(2,2),(1,7)])
                        with Image.open(target/texture) as sheet:
                            self.assertEqual(sheet.size,(64,192))
                            for index,color in enumerate(capture_runner.SHIELD_ANIMATION_COLORS):
                                frame = sheet.crop((0,index*64,64,(index+1)*64))
                                self.assertEqual(frame.getchannel("A").tobytes(),original.getchannel("A").tobytes())
                                for y in range(64):
                                    for x in range(64):
                                        pixel = original.getpixel((x,y))
                                        self.assertEqual(frame.getpixel((x,y)),
                                            tuple(pixel[c]*color[c]//255 for c in range(3))+(pixel[3],))


    def test_three_distinct_source_frames_are_not_reordered_to_playback_order(self):
        payload = capture_runner.shield_animation_png()
        self.assertEqual(payload, capture_runner.shield_animation_png())
        with Image.open(io.BytesIO(payload)) as image:
            self.assertEqual(image.size, (64,192))
            for index, color in enumerate(capture_runner.SHIELD_ANIMATION_COLORS):
                self.assertEqual(image.crop((0,index*64,64,(index+1)*64)).getextrema(),
                                 tuple((value,value) for value in color))

    def test_only_texture_and_animation_metadata_change(self):
        for scenario in ("shield-animation", "shield-animation-interpolated"):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as root:
                spec, = capture_runner.gui_resource_pack_specs(scenario)
                target = Path(root) / "pack"
                capture_runner.write_gui_resource_pack(target, spec)
                texture = "assets/minecraft/textures/entity/shield_base_nopattern.png"
                self.assertEqual({p.relative_to(target).as_posix() for p in target.rglob("*") if p.is_file()},
                                 {"pack.mcmeta",texture,texture+".mcmeta"})
                meta = json.loads((target/(texture+".mcmeta")).read_text())["animation"]
                self.assertEqual((meta["width"],meta["height"]),(64,64))
                self.assertEqual(meta["interpolate"],scenario.endswith("-interpolated"))
                self.assertEqual([(f["index"],f["time"]) for f in meta["frames"]],[(2,3),(0,5),(2,2),(1,7)])
                self.assertEqual((target/texture).read_bytes(),capture_runner.shield_animation_png())

    def test_static_or_missing_pixels_never_admit_animation(self):
        for scenario in ("shield-animation", "shield-animation-interpolated"):
            result = graphics_harness.model_item_foil_parity_report({"pairs":[]},"shield",scenario)
            self.assertTrue(result["requested"])
            self.assertFalse(result["passed"])
            self.assertIn("phase-matched",result["reason"])


if __name__ == "__main__":
    unittest.main()
