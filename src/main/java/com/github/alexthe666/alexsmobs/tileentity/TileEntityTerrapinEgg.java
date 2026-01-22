package com.github.alexthe666.alexsmobs.tileentity;

import com.github.alexthe666.alexsmobs.entity.EntityTerrapin;
import com.github.alexthe666.alexsmobs.entity.util.TerrapinTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TileEntityTerrapinEgg extends BlockEntity {
    public ParentData parent1;
    public ParentData parent2;

    public TileEntityTerrapinEgg(BlockPos pos, BlockState state) {
        super(BlockEntityType.TERRAPIN_EGG, pos, state);
    }

    public void addAttributesToOffspring(EntityTerrapin baby, RandomSource random){
        if(parent1 != null && parent2 != null){
            baby.setTurtleType(random.nextBoolean() ? parent1.type : parent2.type);
            baby.setShellType(random.nextBoolean() ? parent1.shellType : parent2.shellType);
            baby.setSkinType(random.nextBoolean() ? parent1.skinType : parent2.skinType);
            baby.setTurtleColor((parent1.turtleColor + parent2.turtleColor) / 2);
            baby.setShellColor((parent1.shellColor + parent2.shellColor) / 2);
            baby.setSkinColor((parent1.skinColor + parent2.skinColor) / 2);
            if(random.nextFloat() < 0.15F){
                baby.setTurtleType(TerrapinTypes.OVERLAY);
                switch (random.nextInt(2)){
                    case 0:
                        baby.setTurtleColor((int) (0xFFFFFF * random.nextFloat()));
                        break;
                    case 1:
                        baby.setShellColor((int) (0xFFFFFF * random.nextFloat()));
                        break;
                    case 2:
                        baby.setSkinColor((int) (0xFFFFFF * random.nextFloat()));
                        break;
                }
            }
        }
    }


    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Parent1Data").ifPresent(child -> {
            this.parent1 = new ParentData(child);
        });
        input.child("Parent2Data").ifPresent(child -> {
            this.parent2 = new ParentData(child);
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if(this.parent1 != null){
            output.putChild("Parent1Data", child -> parent1.writeToNBT(child));
        }
        if(this.parent2 != null){
            output.putChild("Parent2Data", child -> parent2.writeToNBT(child));
        }
    }

    public static class ParentData {
        public TerrapinTypes type;
        public int shellType;
        public int skinType;
        public int turtleColor;
        public int shellColor;
        public int skinColor;

        public ParentData(TerrapinTypes type, int shellType, int skinType, int turtleColor, int shellColor, int skinColor) {
            this.type = type;
            this.shellType = shellType;
            this.skinType = skinType;
            this.turtleColor = turtleColor;
            this.shellColor = shellColor;
            this.skinColor = skinColor;
        }

        public ParentData(ValueInput input){
            this(TerrapinTypes.values()[Mth.clamp(input.getIntOr("TerrapinType", 0), 0, TerrapinTypes.values().length - 1)],
                    input.getIntOr("ShellType", 0),
                    input.getIntOr("SkinType", 0),
                    input.getIntOr("TurtleColor", 0),
                    input.getIntOr("ShellColor", 0),
                    input.getIntOr("SkinColor", 0)
                    );
        }

        public boolean canMerge(ParentData other){
            if(type == TerrapinTypes.OVERLAY && other.type == TerrapinTypes.OVERLAY){
                return turtleColor == other.turtleColor && shellType == other.shellType && skinType == other.skinType && shellColor == other.shellColor && skinColor == other.skinColor;
            }
            return other.type == this.type;
        }

        public void writeToNBT(ValueOutput output){
            output.putInt("TerrapinType", type.ordinal());
            output.putInt("ShellType", shellType);
            output.putInt("SkinType", skinType);
            output.putInt("TurtleColor", turtleColor);
            output.putInt("ShellColor", shellColor);
            output.putInt("SkinColor", skinColor);

        }
    }

}
