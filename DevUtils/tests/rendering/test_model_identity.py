import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from model_identity import matches_model_identity, combine_model_parts


class CanonicalModelIdentityTest(unittest.TestCase):
    def test_real_model_parts_and_unsplit_assets_retain_their_family(self):
        for path in ["", "/body", "/right_hind_leg", "/头/arm-L"]:
            part = "root" if not path else path.encode().hex()
            self.assertTrue(matches_model_identity("minecraft:cow/part/" + part, "minecraft:cow"))
        self.assertTrue(matches_model_identity("minecraft:cow", "minecraft:cow"))

    def test_other_families_and_malformed_or_noncanonical_parts_reject(self):
        for actual in [None, 0, "minecraft:cow_extra", "minecraft:cow_extra/part/2f626f6479",
                       "minecraft:cow/part/", "minecraft:cow/part/body", "minecraft:cow/part/2F",
                       "minecraft:cow/part/2", "minecraft:cow/part/ff", "minecraft:cow/part/c0af",
                       "minecraft:cow/part/2f/extra", "minecraft:cow/part/root/extra"]:
            self.assertFalse(matches_model_identity(actual, "minecraft:cow"), actual)
        self.assertFalse(matches_model_identity("minecraft:cow/part/2f626f6479", "minecraft:pig"))

    def test_crop_covers_complete_frame_and_rejects_partial_duplicate_or_mixed_parts(self):
        def part(name, box):
            return dict(semanticModelIdentity="minecraft:cow/part/"+name.encode().hex(),
                        frameIndex=20, route="rust-vulkan-whole-frame", entityId=3,
                        textureId="cow", viewport={"width":1280,"height":720},
                        vertexLayoutVersion=2,indexType=1,sectionCullPolicy=0,
                        projected=True,screenBounds=dict(zip(("left","top","right","bottom"),box)),
                        vertexCount=24,sectionCount=6,meshKey=len(name),meshGeneration=1)
        parts=[part("/body",[10,20,50,60]),part("/head",[0,0,30,30])]
        inventory={p["semanticModelIdentity"] for p in parts}
        merged=combine_model_parts(parts,"minecraft:cow",inventory)
        self.assertEqual(merged["screenBounds"],dict(left=0,top=0,right=50,bottom=60))
        self.assertEqual(merged["sectionCount"],12)
        self.assertEqual(len(merged["parts"]),2)
        self.assertIsNone(combine_model_parts(parts[:1],"minecraft:cow",inventory))
        self.assertIsNone(combine_model_parts([parts[0],parts[0]],"minecraft:cow",inventory))
        for key,value in [("entityId",4),("frameIndex",21),("textureId","pig"),("projected",False)]:
            changed=[parts[0],{**parts[1],key:value}]
            self.assertIsNone(combine_model_parts(changed,"minecraft:cow",inventory),key)
        self.assertIsNone(combine_model_parts(parts,"minecraft:pig",inventory))


if __name__ == "__main__":
    unittest.main()
