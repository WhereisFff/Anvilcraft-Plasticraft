package dev.anvilcraft.plasticraft.client.renderer.entity.allay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;

/** 复用原版悦灵网格与半透明渲染,泛型接到施工悦灵以便挂帽子与托管物层。 */
public class WorkingAllayModel extends HierarchicalModel<WorkingAllayEntity> implements ArmedModel {
    private final AllayModel inner;

    public WorkingAllayModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.inner = new AllayModel(root);
    }

    @Override
    public ModelPart root() {
        return this.inner.root();
    }

    @Override
    public void setupAnim(
        WorkingAllayEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        this.inner.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.inner.translateToHand(arm, poseStack);
    }

    public void translateToHead(PoseStack poseStack) {
        this.root().translateAndRotate(poseStack);
        this.root().getChild("head").translateAndRotate(poseStack);
    }
}
