/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.test.gametest;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

public class CustomTestInvokerTest implements CustomTestMethodInvoker {
	@Override
	public void invokeTestMethod(TestContext context, Method method) throws ReflectiveOperationException {
		context.setBlockState(0, 1, 0, Blocks.DIAMOND_BLOCK);

		method.invoke(this, context, Blocks.DIAMOND_BLOCK);
	}

	@GameTest
	public void testCustomInvoker(TestContext context, Block testBlock) {
		context.addInstantFinalTask(() ->
				context.checkBlock(new BlockPos(0, 1, 0), (block) -> block == testBlock, (b) -> Text.literal("Expect block to be diamond"))
		);
	}
}
