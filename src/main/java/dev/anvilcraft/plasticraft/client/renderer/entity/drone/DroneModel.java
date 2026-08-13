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
 * 无人机共用主体模型。部件名一旦进入模型层与动画代码便不再改动:
 * root/body/ionocraft_body/magnetoelectric_core/processor、两个螺旋桨锚点、
 * tool_mount/future_tool_anchor、landing_gear 和 status_light。
 * 四种工具附件是分离模型层,见 {@link DroneToolAttachmentModel}。
 */
public class DroneModel {
    public static final ModelLayerLocation LAYER =
        new ModelLayerLocation(AnvilcraftPlasticraft.of("drone"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart ionocraftBody;
    private final ModelPart leftPropellerAnchor;
    private final ModelPart rightPropellerAnchor;
    private final ModelPart toolMount;

    public DroneModel(ModelPart bakedRoot) {
        this.root = bakedRoot.getChild("root");
        this.body = this.root.getChild("body");
        this.ionocraftBody = this.body.getChild("ionocraft_body");
        this.leftPropellerAnchor = this.ionocraftBody.getChild("left_propeller_anchor");
        this.rightPropellerAnchor = this.ionocraftBody.getChild("right_propeller_anchor");
        this.toolMount = this.body.getChild("tool_mount");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild(
            "root",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        PartDefinition body = root.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -2.5F, -4.0F, 8.0F, 5.0F, 8.0F),
            PartPose.offset(0.0F, -5.5F, 0.0F)
        );
        PartDefinition ionocraftBody = body.addOrReplaceChild(
            "ionocraft_body",
            CubeListBuilder.create()
                .texOffs(0, 13)
                .addBox(-10.0F, -2.0F, -1.0F, 20.0F, 2.0F, 2.0F),
            PartPose.offset(0.0F, -2.5F, 0.0F)
        );
        ionocraftBody.addOrReplaceChild(
            "left_propeller_anchor",
            CubeListBuilder.create()
                .texOffs(44, 13)
                .addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
            PartPose.offset(-8.0F, -2.0F, 0.0F)
        );
        ionocraftBody.addOrReplaceChild(
            "right_propeller_anchor",
            CubeListBuilder.create()
                .texOffs(44, 13)
                .mirror()
                .addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
            PartPose.offset(8.0F, -2.0F, 0.0F)
        );
        body.addOrReplaceChild(
            "magnetoelectric_core",
            CubeListBuilder.create()
                .texOffs(0, 17)
                .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, 0.0F, -4.0F)
        );
        body.addOrReplaceChild(
            "processor",
            CubeListBuilder.create()
                .texOffs(12, 17)
                .addBox(-2.0F, -1.0F, -1.5F, 4.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, -2.5F, 2.0F)
        );
        body.addOrReplaceChild(
            "status_light",
            CubeListBuilder.create()
                .texOffs(26, 17)
                .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
            PartPose.offset(0.0F, -2.5F, -2.5F)
        );
        PartDefinition toolMount = body.addOrReplaceChild(
            "tool_mount",
            CubeListBuilder.create()
                .texOffs(30, 17)
                .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 1.0F, 3.0F),
            PartPose.offset(0.0F, 2.5F, -3.0F)
        );
        toolMount.addOrReplaceChild(
            "future_tool_anchor",
            CubeListBuilder.create(),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        root.addOrReplaceChild(
            "landing_gear",
            CubeListBuilder.create()
                .texOffs(52, 13).addBox(-3.5F, 0.0F, -3.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(52, 13).addBox(2.5F, 0.0F, -3.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(52, 13).addBox(-3.5F, 0.0F, 2.5F, 1.0F, 3.0F, 1.0F)
                .texOffs(52, 13).addBox(2.5F, 0.0F, 2.5F, 1.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, -3.0F, 0.0F)
        );
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** 本轮无人机只有地面待机姿态;飞行、起落架与螺旋桨动画随能源系统 TODO 加入。 */
    public void setupIdlePose() {
        this.root.resetPose();
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    /** 把姿态栈平移到工具安装座,供附件层在同一空间渲染。 */
    public void translateToToolMount(PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        this.toolMount.translateAndRotate(poseStack);
    }

    /** 把姿态栈平移到指定侧螺旋桨锚点顶面。 */
    public void translateToPropellerAnchor(boolean left, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        this.ionocraftBody.translateAndRotate(poseStack);
        (left ? this.leftPropellerAnchor : this.rightPropellerAnchor).translateAndRotate(poseStack);
    }
}
