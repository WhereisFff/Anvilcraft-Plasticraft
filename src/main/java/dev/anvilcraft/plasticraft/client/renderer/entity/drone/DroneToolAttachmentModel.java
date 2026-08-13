package dev.anvilcraft.plasticraft.client.renderer.entity.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 四种可动工具附件的分离模型层。附件原点对齐主体的 tool_mount 枢轴,
 * 每件附件保持独立贴图、UV 与关节,未来新增工具不改写现有附件;
 * 关节动作(蟹钳开合、锯片旋转、磁铁吸附、镜筒伸缩)由后续任务 TODO 驱动。
 */
public class DroneToolAttachmentModel {
    public static final ModelLayerLocation CONSTRUCTION_CLAW_LAYER =
        new ModelLayerLocation(AnvilcraftPlasticraft.of("drone_tool/construction_claw"), "main");
    public static final ModelLayerLocation DEMOLITION_STONECUTTER_LAYER =
        new ModelLayerLocation(AnvilcraftPlasticraft.of("drone_tool/demolition_stonecutter"), "main");
    public static final ModelLayerLocation COLLECTION_MAGNET_LAYER =
        new ModelLayerLocation(AnvilcraftPlasticraft.of("drone_tool/collection_magnet"), "main");
    public static final ModelLayerLocation OBSERVATION_SPYGLASS_LAYER =
        new ModelLayerLocation(AnvilcraftPlasticraft.of("drone_tool/observation_spyglass"), "main");

    private final ModelPart root;

    public DroneToolAttachmentModel(ModelPart root) {
        this.root = root;
    }

    public void translateToHeldItem(PoseStack poseStack) {
        if (!this.root.hasChild("construction_attachment")) return;
        ModelPart attachment = this.root.getChild("construction_attachment");
        attachment.translateAndRotate(poseStack);
        if (attachment.hasChild("held_item_anchor")) {
            attachment.getChild("held_item_anchor").translateAndRotate(poseStack);
        }
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    /** 蟹钳附件:臂基、左右钳指与持物锚点;静止姿态保持半开。 */
    public static LayerDefinition createConstructionClawLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition attachment = mesh.getRoot().addOrReplaceChild(
            "construction_attachment",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.5F, 0.0F, -2.5F, 3.0F, 2.0F, 3.0F),
            PartPose.ZERO
        );
        attachment.addOrReplaceChild(
            "claw_left",
            CubeListBuilder.create()
                .texOffs(0, 5).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F)
                .texOffs(8, 5).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(-1.0F, 1.0F, -2.5F, 0.0F, -0.35F, 0.0F)
        );
        attachment.addOrReplaceChild(
            "claw_right",
            CubeListBuilder.create()
                .texOffs(0, 5).mirror().addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F)
                .texOffs(8, 5).mirror().addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 1.0F),
            PartPose.offsetAndRotation(1.0F, 1.0F, -2.5F, 0.0F, 0.35F, 0.0F)
        );
        attachment.addOrReplaceChild(
            "held_item_anchor",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 1.0F, -4.5F)
        );
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** 切石机附件:机架与竖直锯片;刀片只表现瞬间切割,不做挖掘进度。 */
    public static LayerDefinition createDemolitionStonecutterLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition attachment = mesh.getRoot().addOrReplaceChild(
            "demolition_attachment",
            CubeListBuilder.create(),
            PartPose.ZERO
        );
        attachment.addOrReplaceChild(
            "stonecutter_body",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-2.0F, 0.0F, -3.0F, 4.0F, 2.0F, 3.0F),
            PartPose.ZERO
        );
        attachment.addOrReplaceChild(
            "stonecutter_blade",
            CubeListBuilder.create()
                .texOffs(14, 0)
                .addBox(-0.5F, -2.0F, -4.0F, 1.0F, 4.0F, 4.0F),
            PartPose.offset(0.0F, 1.0F, -3.0F)
        );
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** 手持磁铁附件:U 形磁体与磁场锚点。 */
    public static LayerDefinition createCollectionMagnetLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition attachment = mesh.getRoot().addOrReplaceChild(
            "collection_attachment",
            CubeListBuilder.create(),
            PartPose.ZERO
        );
        attachment.addOrReplaceChild(
            "magnet_body",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, 0.0F, -3.5F, 4.0F, 2.0F, 3.0F)
                .texOffs(0, 5).addBox(-2.0F, 2.0F, -3.5F, 1.0F, 1.0F, 3.0F)
                .texOffs(0, 5).addBox(1.0F, 2.0F, -3.5F, 1.0F, 1.0F, 3.0F),
            PartPose.ZERO
        );
        attachment.addOrReplaceChild(
            "magnet_field_anchor",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 3.0F, -2.0F)
        );
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** 望远镜附件:云台、镜筒与镜头;观察姿态与伸缩由观察任务 TODO 驱动。 */
    public static LayerDefinition createObservationSpyglassLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition attachment = mesh.getRoot().addOrReplaceChild(
            "observation_attachment",
            CubeListBuilder.create(),
            PartPose.ZERO
        );
        PartDefinition body = attachment.addOrReplaceChild(
            "spyglass_body",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.0F, 0.0F, -2.0F, 2.0F, 2.0F, 2.0F),
            PartPose.ZERO
        );
        PartDefinition tube = body.addOrReplaceChild(
            "spyglass_tube",
            CubeListBuilder.create()
                .texOffs(8, 0)
                .addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 1.0F, -2.0F)
        );
        tube.addOrReplaceChild(
            "spyglass_lens",
            CubeListBuilder.create()
                .texOffs(20, 0)
                .addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, -4.0F)
        );
        return LayerDefinition.create(mesh, 32, 32);
    }
}
