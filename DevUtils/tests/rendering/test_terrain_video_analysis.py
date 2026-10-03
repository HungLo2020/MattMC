import unittest
import numpy as np
from AnalyzeTerrainVideo import returning_mask, sampled_triads

class TerrainVideoAnalysisTest(unittest.TestCase):
    def test_transient_missing_patch_has_return_signature(self):
        ground=np.full((32,32,3),[70,100,40],dtype=np.uint8)
        missing=ground.copy();missing[8:24,8:24]=[200,220,255]
        result=returning_mask(ground,missing,ground)
        self.assertEqual(int(result.sum()),16*16)
        self.assertFalse(result[:8].any())

    def test_persistent_change_and_monotonic_edge_do_not_return(self):
        a=np.zeros((32,32,3),dtype=np.uint8)
        b=a.copy();b[:,8:]=255
        c=a.copy();c[:,16:]=255
        self.assertFalse(returning_mask(a,b,b).any())
        # A moving edge changes each pixel once across a three-frame window.
        a[:,4:]=255
        self.assertFalse(returning_mask(a,b,c).any())

    def test_small_lighting_variation_remains_below_triage_threshold(self):
        a=np.full((8,8,3),60,dtype=np.uint8)
        self.assertFalse(returning_mask(a,a+4,a+8).any())

    def test_missing_rendered_frame_repeated_across_samples_needs_wider_lag(self):
        ground=np.full((32,32,3),[70,100,40],dtype=np.uint8)
        missing=ground.copy();missing[8:24,8:24]=[200,220,255]
        sequence=[ground,ground,missing,missing,ground,ground]
        self.assertFalse(any(returning_mask(a,b,c).any()
                             for _,a,b,c in sampled_triads(sequence,1)))
        wider=list(sampled_triads(sequence,2))
        self.assertEqual([index for index,*_ in wider],[2,3])
        self.assertTrue(all(int(returning_mask(a,b,c).sum())==256 for _,a,b,c in wider))

    def test_four_sample_lag_covers_three_repeated_samples_and_rejects_unbounded_lags(self):
        ground=np.full((8,8,3),60,dtype=np.uint8)
        missing=np.full((8,8,3),255,dtype=np.uint8)
        sequence=[ground]*4+[missing]*3+[ground]*4
        triads=list(sampled_triads(sequence,4))
        self.assertEqual([index for index,*_ in triads],[4,5,6])
        self.assertTrue(all(returning_mask(a,b,c).all() for _,a,b,c in triads))
        with self.assertRaises(ValueError):
            list(sampled_triads(sequence,8))

if __name__=='__main__':unittest.main()
