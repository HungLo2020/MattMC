package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;

public class CraftingInput implements RecipeInput {
	public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());
	private final int width;
	private final int height;
	private final List<ItemStack> items;
	private final StackedItemContents stackedContents = new StackedItemContents();
	private final int ingredientCount;

	private CraftingInput(int i, int j, List<ItemStack> list) {
		this.width = i;
		this.height = j;
		this.items = list;
		int k = 0;

		for (ItemStack itemStack : list) {
			if (!itemStack.isEmpty()) {
				k++;
				this.stackedContents.accountStack(itemStack, 1);
			}
		}

		this.ingredientCount = k;
	}

	public static CraftingInput of(int i, int j, List<ItemStack> list) {
		return ofPositioned(i, j, list).input();
	}

	public static CraftingInput.Positioned ofPositioned(int i, int j, List<ItemStack> list) {
		if (i != 0 && j != 0) {
			int k = i - 1;
			int l = 0;
			int m = j - 1;
			int n = 0;

			for (int o = 0; o < j; o++) {
				boolean bl = true;

				for (int p = 0; p < i; p++) {
					ItemStack itemStack = (ItemStack)list.get(p + o * i);
					if (!itemStack.isEmpty()) {
						k = Math.min(k, p);
						l = Math.max(l, p);
						bl = false;
					}
				}

				if (!bl) {
					m = Math.min(m, o);
					n = Math.max(n, o);
				}
			}

			int o = l - k + 1;
			int q = n - m + 1;
			if (o <= 0 || q <= 0) {
				return CraftingInput.Positioned.EMPTY;
			} else if (o == i && q == j) {
				return new CraftingInput.Positioned(new CraftingInput(i, j, list), k, m);
			} else {
				List<ItemStack> list2 = new ArrayList(o * q);

				for (int r = 0; r < q; r++) {
					for (int s = 0; s < o; s++) {
						int t = s + k + (r + m) * i;
						list2.add((ItemStack)list.get(t));
					}
				}

				return new CraftingInput.Positioned(new CraftingInput(o, q, list2), k, m);
			}
		} else {
			return CraftingInput.Positioned.EMPTY;
		}
	}

	/**
	 * ofPositioned for a native grid whose non-empty rows are already known (bit
	 * p + o * i of the occupancy mask). Only the cropped cells are read; a full
	 * bounding box keeps the original immutable copy of every cell.
	 */
	public static CraftingInput.Positioned ofOccupied(int i, int j, long occupied, List<ItemStack> list) {
		int k = i - 1;
		int l = 0;
		int m = j - 1;
		int n = 0;

		for (int o = 0; o < j; o++) {
			long row = occupied >>> (o * i) & (1L << i) - 1;
			if (row != 0) {
				k = Math.min(k, Long.numberOfTrailingZeros(row));
				l = Math.max(l, 63 - Long.numberOfLeadingZeros(row));
				m = Math.min(m, o);
				n = Math.max(n, o);
			}
		}

		int o = l - k + 1;
		int q = n - m + 1;
		if (o <= 0 || q <= 0) {
			return CraftingInput.Positioned.EMPTY;
		} else if (o == i && q == j) {
			return new CraftingInput.Positioned(new CraftingInput(i, j, List.copyOf(list)), k, m);
		} else {
			List<ItemStack> list2 = new ArrayList(o * q);

			for (int r = 0; r < q; r++) {
				for (int s = 0; s < o; s++) {
					list2.add((ItemStack)list.get(s + k + (r + m) * i));
				}
			}

			return new CraftingInput.Positioned(new CraftingInput(o, q, list2), k, m);
		}
	}

	@Override
	public ItemStack getItem(int i) {
		return (ItemStack)this.items.get(i);
	}

	public ItemStack getItem(int i, int j) {
		return (ItemStack)this.items.get(i + j * this.width);
	}

	@Override
	public int size() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.ingredientCount == 0;
	}

	public StackedItemContents stackedContents() {
		return this.stackedContents;
	}

	public List<ItemStack> items() {
		return this.items;
	}

	public int ingredientCount() {
		return this.ingredientCount;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else {
			return !(object instanceof CraftingInput craftingInput)
				? false
				: this.width == craftingInput.width
					&& this.height == craftingInput.height
					&& this.ingredientCount == craftingInput.ingredientCount
					&& ItemStack.listMatches(this.items, craftingInput.items);
		}
	}

	public int hashCode() {
		int i = ItemStack.hashStackList(this.items);
		i = 31 * i + this.width;
		return 31 * i + this.height;
	}

	public record Positioned(CraftingInput input, int left, int top) {
		public static final CraftingInput.Positioned EMPTY = new CraftingInput.Positioned(CraftingInput.EMPTY, 0, 0);
	}
}
