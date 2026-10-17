package com.seibel.distanthorizons.core.util.objects.quadTree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class QuadTreeRecenterIterationTest {
	private static final byte LEAF_DETAIL = 6;

	@Test
	void iteratorCreatedBeforeRecenterSkipsRootsThatLeftTheTree() {
		// The render thread builds DH render lists without LodQuadTree's lock;
		// its tick can recenter the tree after the iterator captured root positions.
		var tree = new QuadTree<String>(2048, new DhBlockPos2D(0, 0), LEAF_DETAIL);
		byte root = tree.treeRootDetailLevel;
		tree.setValue(DhSectionPos.encode(root, -1, -1), "old-origin");
		tree.setValue(DhSectionPos.encode(root, 0, 0), "shared");
		var iterator = tree.nodeIterator();

		tree.setCenterBlockPos(new DhBlockPos2D(1024, 1024));

		var values = new ArrayList<String>();
		while (iterator.hasNext()) {
			var node = iterator.next();
			if (node.value != null) values.add(node.value);
		}
		assertTrue(!values.contains("old-origin"), "a recentered-away root must not be iterated");
		assertEquals(java.util.List.of("shared"), values);
	}
}
