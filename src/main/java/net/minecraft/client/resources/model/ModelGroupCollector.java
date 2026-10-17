package net.minecraft.client.resources.model;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(EnvType.CLIENT)
public class ModelGroupCollector {
	static final int SINGLETON_MODEL_GROUP = -1;
	private static final int INVISIBLE_MODEL_GROUP = 0;

	/**
	 * Groups states whose models render alike: the same equality group (one
	 * per blockstate variant entry, or per multipart definition and set of
	 * matching selectors, from Rust's loader) and the same values of the
	 * block's coloring properties.
	 */
	public static Object2IntMap<BlockState> build(BlockColors blockColors, NativeModels nativeModels) {
		Map<Block, List<Property<?>>> map = new HashMap();
		Map<ModelGroupCollector.GroupKey, Set<BlockState>> map2 = new HashMap();
		List<BlockState> states = nativeModels.states();
		int[] groups = nativeModels.groups();
		for (int index = 0; index < states.size(); index++) {
			if (groups[index] < 0) {
				continue;
			}
			BlockState blockState = states.get(index);
			List<Property<?>> list = (List<Property<?>>)map.computeIfAbsent(blockState.getBlock(), block -> List.copyOf(blockColors.getColoringProperties(block)));
			ModelGroupCollector.GroupKey groupKey = ModelGroupCollector.GroupKey.create(blockState, groups[index], list);
			((Set)map2.computeIfAbsent(groupKey, groupKeyx -> Sets.newIdentityHashSet())).add(blockState);
		}
		int i = 1;
		Object2IntMap<BlockState> object2IntMap = new Object2IntOpenHashMap<>();
		object2IntMap.defaultReturnValue(-1);

		for (Set<BlockState> set : map2.values()) {
			Iterator<BlockState> iterator = set.iterator();

			while (iterator.hasNext()) {
				BlockState blockState = (BlockState)iterator.next();
				if (blockState.getRenderShape() != RenderShape.MODEL) {
					iterator.remove();
					object2IntMap.put(blockState, 0);
				}
			}

			if (set.size() > 1) {
				int j = i++;
				set.forEach(blockState -> object2IntMap.put(blockState, j));
			}
		}

		return object2IntMap;
	}

	@Environment(EnvType.CLIENT)
	record GroupKey(Object equalityGroup, List<Object> coloringValues) {
		public static ModelGroupCollector.GroupKey create(BlockState blockState, int equalityGroup, List<Property<?>> list) {
			List<Object> list2 = getColoringValues(blockState, list);
			return new ModelGroupCollector.GroupKey(equalityGroup, list2);
		}

		private static List<Object> getColoringValues(BlockState blockState, List<Property<?>> list) {
			Object[] objects = new Object[list.size()];

			for (int i = 0; i < list.size(); i++) {
				objects[i] = blockState.getValue((Property)list.get(i));
			}

			return List.of(objects);
		}
	}
}
