package net.minecraft.world.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSlotTest {
	@Test
	void forContainerWritesToCapturedIndex() {
		SimpleContainerData containerData = new SimpleContainerData(3);
		DataSlot dataSlot = DataSlot.forContainer(containerData, 2);

		dataSlot.set(1);

		assertEquals(0, containerData.get(0));
		assertEquals(0, containerData.get(1));
		assertEquals(1, containerData.get(2));
	}

	@Test
	void sharedWritesToCapturedIndex() {
		int[] values = new int[3];
		DataSlot dataSlot = DataSlot.shared(values, 2);

		dataSlot.set(1);

		assertEquals(0, values[0]);
		assertEquals(0, values[1]);
		assertEquals(1, values[2]);
	}
}