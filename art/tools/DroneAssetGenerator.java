import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 无人机模型贴图与 Blockbench 源文件生成器(一次性美术工具,不参与模组编译)。
 *
 * 几何数值必须与以下客户端模型类保持一致,修改模型时同步更新本文件并重新运行:
 * - client/renderer/entity/drone/DroneModel.java
 * - client/renderer/entity/drone/DroneToolAttachmentModel.java
 *
 * 运行方式(仓库根目录): java art/tools/DroneAssetGenerator.java
 * 输出:
 * - src/main/resources/assets/anvilcraftplasticraft/textures/entity/drone.png
 * - src/main/resources/assets/anvilcraftplasticraft/textures/entity/drone/tool/*.png
 * - art/blockbench/entity/drone.bbmodel 与 art/blockbench/entity/drone_tool/*.bbmodel
 */
public final class DroneAssetGenerator {
    static final Path TEXTURES = Path.of("src/main/resources/assets/anvilcraftplasticraft/textures/entity");
    static final Path BLOCKBENCH = Path.of("art/blockbench/entity");

    // AnvilCraft 机械视觉语言取色:铜橙、深灰、青绿电光。
    static final int BODY = 0x37393C;
    static final int BODY_TOP = 0x4C4E50;
    static final int IONO = 0x4D271C;
    static final int IONO_GOLD = 0x865C1A;
    static final int HUB = 0x606265;
    static final int LEG = 0x303235;
    static final int CORE = 0xB56447;
    static final int CORE_GLOW = 0x7CCDCA;
    static final int GLOW_LIGHT = 0xA7E0DD;
    static final int PROCESSOR = 0x4B2417;
    static final int PROCESSOR_PIN = 0xC26B4C;
    static final int MOUNT = 0x4C4E50;
    static final int CLAW = 0xDC7A39;
    static final int CLAW_LIGHT = 0xDC9362;
    static final int CLAW_DARK = 0xAB5320;
    static final int CUTTER = 0x4C4E50;
    static final int BLADE = 0xC9C9C9;
    static final int BLADE_EDGE = 0xE9E9E9;
    static final int MAGNET = 0x3F4144;
    static final int MAGNET_POLE = 0x1E283A;
    static final int MAGNET_GLOW = 0xB5E45A;
    static final int SPY_BASE = 0x4C4E50;
    static final int SPY_TUBE = 0xB56447;
    static final int SPY_GOLD = 0x865C1A;
    static final int SPY_LENS = 0x7CCDCA;

    record Cube(String name, double x, double y, double z, double w, double h, double d, int u, int v, int color) {
    }

    record Part(String name, double px, double py, double pz, double yRotDeg, List<Cube> cubes, List<Part> children) {
        static Part of(String name, double px, double py, double pz, List<Cube> cubes, Part... children) {
            return new Part(name, px, py, pz, 0.0D, cubes, List.of(children));
        }

        static Part rotated(String name, double px, double py, double pz, double yRotDeg, List<Cube> cubes) {
            return new Part(name, px, py, pz, yRotDeg, cubes, List.of());
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "mask".equals(args[0])) {
            generateConstructionProjectionMask();
            return;
        }
        Files.createDirectories(TEXTURES.resolve("drone/tool"));
        Files.createDirectories(BLOCKBENCH.resolve("drone_tool"));

        Part drone = Part.of("root", 0, 24, 0, List.of(),
            Part.of("body", 0, -5.5, 0, List.of(new Cube("body", -4, -2.5, -4, 8, 5, 8, 0, 0, BODY)),
                Part.of("ionocraft_body", 0, -2.5, 0,
                    List.of(new Cube("iono_arm", -10, -2, -1, 20, 2, 2, 0, 13, IONO)),
                    Part.of("left_propeller_anchor", -8, -2, 0,
                        List.of(new Cube("hub", -1, -1, -1, 2, 1, 2, 44, 13, HUB))),
                    Part.of("right_propeller_anchor", 8, -2, 0,
                        List.of(new Cube("hub", -1, -1, -1, 2, 1, 2, 44, 13, HUB)))),
                Part.of("magnetoelectric_core", 0, 0, -4,
                    List.of(new Cube("core", -2, -2, -2, 4, 4, 2, 0, 17, CORE))),
                Part.of("processor", 0, -2.5, 2,
                    List.of(new Cube("processor", -2, -1, -1.5, 4, 1, 3, 12, 17, PROCESSOR))),
                Part.of("status_light", 0, -2.5, -2.5,
                    List.of(new Cube("light", -0.5, -2, -0.5, 1, 2, 1, 26, 17, CORE_GLOW))),
                Part.of("tool_mount", 0, 2.5, -3,
                    List.of(new Cube("mount", -1.5, 0, -1.5, 3, 1, 3, 30, 17, MOUNT)),
                    Part.of("future_tool_anchor", 0, 1, 0, List.of()))),
            Part.of("landing_gear", 0, -3, 0, List.of(
                new Cube("leg", -3.5, 0, -3.5, 1, 3, 1, 52, 13, LEG),
                new Cube("leg", 2.5, 0, -3.5, 1, 3, 1, 52, 13, LEG),
                new Cube("leg", -3.5, 0, 2.5, 1, 3, 1, 52, 13, LEG),
                new Cube("leg", 2.5, 0, 2.5, 1, 3, 1, 52, 13, LEG))));

        Part claw = Part.of("construction_attachment", 0, 0, 0,
            List.of(new Cube("claw_base", -1.5, 0, -2.5, 3, 2, 3, 0, 0, CLAW_DARK)),
            Part.rotated("claw_left", -1, 1, -2.5, -20, List.of(
                new Cube("claw_finger", -0.5, -0.5, -3, 1, 1, 3, 0, 5, CLAW),
                new Cube("claw_tip", -0.5, -0.5, -4, 1, 1, 1, 8, 5, CLAW_LIGHT))),
            Part.rotated("claw_right", 1, 1, -2.5, 20, List.of(
                new Cube("claw_finger", -0.5, -0.5, -3, 1, 1, 3, 0, 5, CLAW),
                new Cube("claw_tip", -0.5, -0.5, -4, 1, 1, 1, 8, 5, CLAW_LIGHT))),
            Part.of("held_item_anchor", 0, 1, -4.5, List.of()));

        Part cutter = Part.of("demolition_attachment", 0, 0, 0, List.of(),
            Part.of("stonecutter_body", 0, 0, 0,
                List.of(new Cube("cutter_body", -2, 0, -3, 4, 2, 3, 0, 0, CUTTER))),
            Part.of("stonecutter_blade", 0, 1, -3,
                List.of(new Cube("blade", -0.5, -2, -4, 1, 4, 4, 14, 0, BLADE))));

        Part magnet = Part.of("collection_attachment", 0, 0, 0, List.of(),
            Part.of("magnet_body", 0, 0, 0, List.of(
                new Cube("magnet_bend", -2, 0, -3.5, 4, 2, 3, 0, 0, MAGNET),
                new Cube("magnet_pole", -2, 2, -3.5, 1, 1, 3, 0, 5, MAGNET_POLE),
                new Cube("magnet_pole", 1, 2, -3.5, 1, 1, 3, 0, 5, MAGNET_POLE))),
            Part.of("magnet_field_anchor", 0, 3, -2, List.of()));

        Part spyglass = Part.of("observation_attachment", 0, 0, 0, List.of(),
            Part.of("spyglass_body", 0, 0, 0,
                List.of(new Cube("spy_body", -1, 0, -2, 2, 2, 2, 0, 0, SPY_BASE)),
                Part.of("spyglass_tube", 0, 1, -2,
                    List.of(new Cube("spy_tube", -1, -1, -4, 2, 2, 4, 8, 0, SPY_TUBE)),
                    Part.of("spyglass_lens", 0, 0, -4,
                        List.of(new Cube("spy_lens", -1.5, -1.5, -1, 3, 3, 1, 20, 0, SPY_GOLD))))));

        emit(drone, 64, TEXTURES.resolve("drone.png"), BLOCKBENCH.resolve("drone.bbmodel"), "drone");
        emit(claw, 32, TEXTURES.resolve("drone/tool/construction_claw.png"),
            BLOCKBENCH.resolve("drone_tool/construction_claw.bbmodel"), "construction_claw");
        emit(cutter, 32, TEXTURES.resolve("drone/tool/demolition_stonecutter.png"),
            BLOCKBENCH.resolve("drone_tool/demolition_stonecutter.bbmodel"), "demolition_stonecutter");
        emit(magnet, 32, TEXTURES.resolve("drone/tool/collection_magnet.png"),
            BLOCKBENCH.resolve("drone_tool/collection_magnet.bbmodel"), "collection_magnet");
        emit(spyglass, 32, TEXTURES.resolve("drone/tool/observation_spyglass.png"),
            BLOCKBENCH.resolve("drone_tool/observation_spyglass.bbmodel"), "observation_spyglass");
        generateGui();
        generateStationAssets();
        generateConstructionProjectionMask();
        System.out.println("Drone assets generated.");
    }

    /** 施工投影占位遮罩:白网格半透明像素,由渲染器按未交付/已交付状态着色。 */
    static void generateConstructionProjectionMask() throws IOException {
        Path directory = Path.of("src/main/resources/assets/anvilcraftplasticraft/textures/misc");
        Files.createDirectories(directory);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean line = x == 0 || y == 0 || x == 15 || y == 15 || x == 8 || y == 8 || (x + y) % 4 == 0;
                int alpha = line ? 0xA0 : 0x28;
                image.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        Path path = directory.resolve("construction_projection.png");
        ImageIO.write(image, "png", path.toFile());
        System.out.println("  " + path);
    }

    // ==================== 无人机站资产 ====================

    static final Path BLOCK_TEXTURES = Path.of("src/main/resources/assets/anvilcraftplasticraft/textures/block");

    /** 站体方块贴图(有电/无电各一张,cube_all 共用)、站点 GUI 背景与召回按钮。 */
    static void generateStationAssets() throws IOException {
        Files.createDirectories(BLOCK_TEXTURES);
        writeStationBlockTexture("drone_station.png", true);
        writeStationBlockTexture("drone_station_off.png", false);

        BufferedImage background = new BufferedImage(176, 186, BufferedImage.TYPE_INT_ARGB);
        paintPanel(background, 0, 0, 176, 186);
        paintInset(background, 10, 20, 13, 37);      // 能量条框
        for (int row = 0; row < 4; row++) {          // 16 个无人机槽 4x4
            for (int column = 0; column < 4; column++) {
                paintSlot(background, 43 + column * 18, 17 + row * 18);
            }
        }
        paintSlot(background, 133, 26);              // 结构磁盘槽
        paintSlot(background, 133, 62);              // 电容器充能槽
        for (int row = 0; row < 3; row++) {          // 玩家背包
            for (int column = 0; column < 9; column++) {
                paintSlot(background, 7 + column * 18, 103 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) { // 快捷栏
            paintSlot(background, 7 + column * 18, 161);
        }
        ImageIO.write(background, "png", GUI.resolve("background/drone_station.png").toFile());
        System.out.println("  " + GUI.resolve("background/drone_station.png"));

        writeButton("return_home", DroneAssetGenerator::paintReturnHomeIcon);
    }

    static void writeStationBlockTexture(String name, boolean powered) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        long seed = name.hashCode();
        // 机壳底色与铆钉边框。
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                double noise = (hash(seed, x, y) % 1000) / 1000.0 * 0.08 - 0.04;
                set(image, x, y, scale(BODY, (powered ? 1.0 : 0.82) * (1.0 + noise)));
            }
        }
        for (int i = 0; i < 16; i++) {
            set(image, i, 0, scale(IONO, 0.9));
            set(image, i, 15, scale(IONO, 0.7));
            set(image, 0, i, scale(IONO, 0.8));
            set(image, 15, i, scale(IONO, 0.8));
        }
        set(image, 1, 1, CORE);
        set(image, 14, 1, CORE);
        set(image, 1, 14, CORE);
        set(image, 14, 14, CORE);
        // 中央舱门:双开缝线与合页。
        for (int x = 4; x <= 11; x++) {
            for (int y = 4; y <= 11; y++) {
                double noise = (hash(seed + 7, x, y) % 1000) / 1000.0 * 0.06 - 0.03;
                set(image, x, y, scale(0x2C2E31, (powered ? 1.0 : 0.8) * (1.0 + noise)));
            }
        }
        for (int i = 4; i <= 11; i++) {
            set(image, i, 4, scale(HUB, 0.8));
            set(image, i, 11, scale(HUB, 0.6));
            set(image, 4, i, scale(HUB, 0.7));
            set(image, 11, i, scale(HUB, 0.7));
            set(image, 7, i, scale(0x1B1C1E, 1.0));
            set(image, 8, i, scale(0x1B1C1E, 1.0));
        }
        // 四角状态灯:有电亮青,无电熄灭。
        int lampColor = powered ? CORE_GLOW : scale(CORE_GLOW, 0.25);
        set(image, 2, 2, lampColor);
        set(image, 13, 2, lampColor);
        set(image, 2, 13, lampColor);
        set(image, 13, 13, lampColor);
        Path path = BLOCK_TEXTURES.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        System.out.println("  " + path);
    }

    static void paintReturnHomeIcon(BufferedImage image, int y0, int color) {
        // 小房子:屋顶三角 + 房体。
        for (int x = 3; x <= 12; x++) {
            int half = Math.abs(x - 7) + Math.abs(x - 8);
            int roofY = 3 + (half - 1) / 2;
            for (int y = roofY; y <= 7; y++) {
                if (y >= 3) set(image, x, y0 + y, color);
            }
        }
        for (int x = 5; x <= 10; x++) {
            for (int y = 8; y <= 12; y++) {
                set(image, x, y0 + y, color);
            }
        }
        for (int y = 9; y <= 12; y++) {
            set(image, 7, y0 + y, 0);
            set(image, 8, y0 + y, 0);
        }
    }

    // ==================== GUI 资产 ====================

    static final Path GUI = Path.of("src/main/resources/assets/anvilcraftplasticraft/textures/gui");

    /** 无人机单机设置界面背景与策略按钮;布局与 DroneScreen/DroneMenu 中的常量保持一致。 */
    static void generateGui() throws IOException {
        Files.createDirectories(GUI.resolve("background"));
        Files.createDirectories(GUI.resolve("button/drone"));

        BufferedImage background = new BufferedImage(176, 150, BufferedImage.TYPE_INT_ARGB);
        paintPanel(background, 0, 0, 176, 150);
        paintInset(background, 10, 20, 13, 37);      // 能量条框
        for (int row = 0; row < 3; row++) {          // 收集库存 3x3 槽位
            for (int column = 0; column < 3; column++) {
                paintSlot(background, 105 + column * 18, 77 + row * 18);
            }
        }
        ImageIO.write(background, "png", GUI.resolve("background/drone.png").toFile());
        System.out.println("  " + GUI.resolve("background/drone.png"));

        writeButton("pause", DroneAssetGenerator::paintPauseIcon);
        writeButton("skip", DroneAssetGenerator::paintSkipIcon);
    }

    interface IconPainter {
        void paint(BufferedImage image, int y0, int color);
    }

    static void writeButton(String name, IconPainter icon) throws IOException {
        BufferedImage image = new BufferedImage(16, 64, BufferedImage.TYPE_INT_ARGB);
        // 四帧竖排:0 正常、1 悬停、2 按下(当前选中)、3 禁用。
        int[][] frames = {
            {0x8B8B8B, 0xFFFFFF, 0x555555, 0xE9E9E9},
            {0x9DA2A6, 0xFFFFFF, 0x606468, 0xFFFFFF},
            {0x5E6265, 0x3F4144, 0xA7B0B6, 0x7CCDCA},
            {0x6F6F6F, 0x7F7F7F, 0x4F4F4F, 0x9A9A9A}
        };
        for (int frame = 0; frame < 4; frame++) {
            int y0 = frame * 16;
            int base = frames[frame][0];
            int light = frames[frame][1];
            int dark = frames[frame][2];
            int iconColor = frames[frame][3];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    set(image, x, y0 + y, base);
                }
            }
            for (int i = 0; i < 16; i++) {
                set(image, i, y0, light);
                set(image, 0, y0 + i, light);
                set(image, i, y0 + 15, dark);
                set(image, 15, y0 + i, dark);
            }
            icon.paint(image, y0, iconColor);
        }
        Path path = GUI.resolve("button/drone/" + name + ".png");
        ImageIO.write(image, "png", path.toFile());
        System.out.println("  " + path);
    }

    static void paintPauseIcon(BufferedImage image, int y0, int color) {
        for (int y = 4; y <= 11; y++) {
            set(image, 5, y0 + y, color);
            set(image, 6, y0 + y, color);
            set(image, 9, y0 + y, color);
            set(image, 10, y0 + y, color);
        }
    }

    static void paintSkipIcon(BufferedImage image, int y0, int color) {
        for (int x = 4; x <= 8; x++) {
            int half = x - 4;
            for (int y = 4 + half; y <= 11 - half; y++) {
                set(image, x, y0 + y, color);
            }
        }
        for (int y = 4; y <= 11; y++) {
            set(image, 10, y0 + y, color);
            set(image, 11, y0 + y, color);
        }
    }

    /** 原版风格灰色面板:黑色圆角外框、左上高光、右下阴影。 */
    static void paintPanel(BufferedImage image, int x, int y, int w, int h) {
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                set(image, x + px, y + py, 0xC6C6C6);
            }
        }
        for (int i = 1; i < w - 1; i++) {
            set(image, x + i, y, 0x000000);
            set(image, x + i, y + h - 1, 0x000000);
        }
        for (int i = 1; i < h - 1; i++) {
            set(image, x, y + i, 0x000000);
            set(image, x + w - 1, y + i, 0x000000);
        }
        for (int i = 2; i < w - 2; i++) {
            set(image, x + i, y + 1, 0xFFFFFF);
            set(image, x + i, y + h - 2, 0x555555);
        }
        for (int i = 2; i < h - 2; i++) {
            set(image, x + 1, y + i, 0xFFFFFF);
            set(image, x + w - 2, y + i, 0x555555);
        }
        set(image, x + 1, y + h - 2, 0x8B8B8B);
        set(image, x + w - 2, y + 1, 0x8B8B8B);
        // 四角圆角:清掉外框角像素。
        clear(image, x, y, 1, 1);
        clear(image, x + w - 1, y, 1, 1);
        clear(image, x, y + h - 1, 1, 1);
        clear(image, x + w - 1, y + h - 1, 1, 1);
        set(image, x + 1, y + 1, 0xC6C6C6);
        set(image, x + w - 2, y + h - 2, 0xC6C6C6);
    }

    /** 内凹区域:左上暗边、右下亮边、深色底,供能量条这类动态内容覆盖。 */
    static void paintInset(BufferedImage image, int x, int y, int w, int h) {
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                set(image, x + px, y + py, 0x1B0A0A);
            }
        }
        for (int i = 0; i < w; i++) {
            set(image, x + i, y, 0x373737);
            set(image, x + i, y + h - 1, 0xFFFFFF);
        }
        for (int i = 0; i < h; i++) {
            set(image, x, y + i, 0x373737);
            set(image, x + w - 1, y + i, 0xFFFFFF);
        }
        set(image, x + w - 1, y, 0x8B8B8B);
        set(image, x, y + h - 1, 0x8B8B8B);
    }

    /** 原版 18x18 物品槽位框。 */
    static void paintSlot(BufferedImage image, int x, int y) {
        for (int px = 1; px < 17; px++) {
            for (int py = 1; py < 17; py++) {
                set(image, x + px, y + py, 0x8B8B8B);
            }
        }
        for (int i = 0; i < 17; i++) {
            set(image, x + i, y, 0x373737);
            set(image, x, y + i, 0x373737);
            set(image, x + i + 1, y + 17, 0xFFFFFF);
            set(image, x + 17, y + i + 1, 0xFFFFFF);
        }
        set(image, x + 17, y, 0x8B8B8B);
        set(image, x, y + 17, 0x8B8B8B);
    }

    static void emit(Part root, int size, Path texturePath, Path bbmodelPath, String name) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        paintPart(image, root);
        ImageIO.write(image, "png", texturePath.toFile());

        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", pngBytes);
        String base64 = Base64.getEncoder().encodeToString(pngBytes.toByteArray());
        Files.writeString(bbmodelPath, bbmodel(root, size, name, base64), StandardCharsets.UTF_8);
        System.out.println("  " + texturePath + " + " + bbmodelPath);
    }

    // ==================== 贴图绘制 ====================

    static void paintPart(BufferedImage image, Part part) {
        for (Cube cube : part.cubes()) paintCube(image, cube);
        for (Part child : part.children()) paintPart(image, child);
    }

    static void paintCube(BufferedImage image, Cube cube) {
        int w = ceil(cube.w()), h = ceil(cube.h()), d = ceil(cube.d());
        int u = cube.u(), v = cube.v();
        long seed = cube.name().hashCode() * 31L + u * 7L + v;
        face(image, u + d, v, w, d, cube.color(), 1.18, seed + 1);          // top
        face(image, u + d + w, v, w, d, cube.color(), 0.50, seed + 2);      // bottom
        face(image, u, v + d, d, h, cube.color(), 0.82, seed + 3);          // east
        face(image, u + d, v + d, w, h, cube.color(), 1.00, seed + 4);      // north(front)
        face(image, u + d + w, v + d, d, h, cube.color(), 0.82, seed + 5);  // west
        face(image, u + d + w + d, v + d, w, h, cube.color(), 0.70, seed + 6); // south(back)
        paintDetails(image, cube, w, h, d);
    }

    static void face(BufferedImage image, int x, int y, int w, int h, int rgb, double shade, long seed) {
        if (w <= 0 || h <= 0) return;
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                double noise = (hash(seed, px, py) % 1000) / 1000.0 * 0.10 - 0.05;
                double edge = (px == 0 || py == 0 || px == w - 1 || py == h - 1) && w > 2 && h > 2 ? 0.86 : 1.0;
                set(image, x + px, y + py, scale(rgb, shade * (1.0 + noise) * edge));
            }
        }
    }

    /** 按部件补充结构细节:发光元件、锯片圆盘、触点等。 */
    static void paintDetails(BufferedImage image, Cube cube, int w, int h, int d) {
        int u = cube.u(), v = cube.v();
        switch (cube.name()) {
            case "body" -> {
                // 顶面通风缝与机身前面的双传感器。
                for (int i = 1; i < w - 1; i += 2) set(image, u + d + i, v + d / 2, scale(BODY, 0.6));
                set(image, u + d + 2, v + d + 1, CORE_GLOW);
                set(image, u + d + w - 3, v + d + 1, CORE_GLOW);
                // 顶面四角铆钉。
                set(image, u + d, v, scale(CORE, 1.0));
                set(image, u + d + w - 1, v, scale(CORE, 1.0));
                set(image, u + d, v + d - 1, scale(CORE, 1.0));
                set(image, u + d + w - 1, v + d - 1, scale(CORE, 1.0));
            }
            case "iono_arm" -> {
                // 横臂顶面的金色导流条。
                for (int i = 2; i < w - 2; i += 3) {
                    set(image, u + d + i, v, scale(IONO_GOLD, 1.1));
                    set(image, u + d + i, v + d - 1, scale(IONO_GOLD, 1.1));
                }
            }
            case "core" -> {
                // 磁电核心朝外面板中心的电光。
                face(image, u + d + w / 2 - 1, v + d + h / 2 - 1, 2, 2, CORE_GLOW, 1.0, 77);
                set(image, u + d + w / 2 - 1, v + d + h / 2 - 1, GLOW_LIGHT);
            }
            case "light" -> {
                // 状态灯整体发光。
                for (int px = 0; px < 2 * (w + d); px++) {
                    for (int py = 0; py < d + h; py++) {
                        set(image, u + px, v + py, py == 0 ? GLOW_LIGHT : CORE_GLOW);
                    }
                }
            }
            case "processor" -> {
                for (int i = 1; i < w - 1; i += 2) set(image, u + d + i, v + d / 2, PROCESSOR_PIN);
            }
            case "blade" -> {
                // 左右面画圆形锯片:四角透明,边缘亮齿,中心轴点。
                clear(image, u, v, 2 * (w + d), d + h);
                for (int[] region : new int[][]{{u, v + d}, {u + d + w, v + d}}) {
                    paintSawDisc(image, region[0], region[1], d, h);
                }
                // 顶/底/前/后的窄条按金属色补齐。
                face(image, u + d, v, w, d, BLADE, 0.9, 91);
                face(image, u + d + w, v, w, d, BLADE, 0.55, 92);
                face(image, u + d, v + d, w, h, BLADE, 0.8, 93);
                face(image, u + d + w + d, v + d, w, h, BLADE, 0.8, 94);
            }
            case "magnet_pole" -> {
                // 磁极底面的磁场绿光。
                face(image, u + d + w, v, w, d, MAGNET_GLOW, 1.0, 95);
            }
            case "spy_tube" -> {
                // 镜筒的两道金环。
                for (int py = 0; py < h; py++) {
                    set(image, u + d + 0, v + d + py, SPY_GOLD);
                    set(image, u + 1, v + d + py, scale(SPY_GOLD, 0.9));
                    set(image, u + d + w + 1, v + d + py, scale(SPY_GOLD, 0.9));
                }
            }
            case "spy_lens" -> {
                // 镜头朝外面板:青色镜面加高光。
                face(image, u + d, v + d, w, h, SPY_LENS, 1.0, 96);
                set(image, u + d, v + d, GLOW_LIGHT);
            }
            default -> {
            }
        }
    }

    static void paintSawDisc(BufferedImage image, int x, int y, int w, int h) {
        double cx = w / 2.0 - 0.5, cy = h / 2.0 - 0.5;
        double radius = Math.min(w, h) / 2.0;
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                double distance = Math.hypot(px - cx, py - cy);
                if (distance > radius) continue;
                boolean rim = distance > radius - 1.0;
                int color = rim ? BLADE_EDGE : BLADE;
                if (px == (int) Math.round(cx) && py == (int) Math.round(cy)) color = 0x555555;
                set(image, x + px, y + py, scale(color, rim && (px + py) % 2 == 0 ? 1.0 : 0.92));
            }
        }
    }

    static void clear(BufferedImage image, int x, int y, int w, int h) {
        for (int px = 0; px < w; px++) {
            for (int py = 0; py < h; py++) {
                if (inBounds(image, x + px, y + py)) image.setRGB(x + px, y + py, 0);
            }
        }
    }

    static void set(BufferedImage image, int x, int y, int rgb) {
        if (inBounds(image, x, y)) image.setRGB(x, y, 0xFF000000 | rgb);
    }

    static boolean inBounds(BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight();
    }

    static int scale(int rgb, double factor) {
        int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static long hash(long seed, int x, int y) {
        long value = seed * 6364136223846793005L + x * 9007199254740993L + y * 2862933555777941757L;
        value ^= value >>> 33;
        return Math.abs(value);
    }

    static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    // ==================== Blockbench 源文件 ====================

    /**
     * 生成 modded_entity 格式的 bbmodel。坐标转换:Java 实体模型 Y 向下、地面在 y=24,
     * Blockbench Y 向上、地面在 y=0;X/Z 轴向以 Java 模型为准。
     */
    static String bbmodel(Part root, int resolution, String name, String textureBase64) {
        StringBuilder elements = new StringBuilder();
        StringBuilder outliner = new StringBuilder();
        collect(root, 0, 0, 0, elements, outliner);
        return """
            {
              "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": true},
              "name": "%s",
              "model_identifier": "%s",
              "visible_box": [1, 1, 0],
              "variable_placeholders": "",
              "variable_placeholder_buttons": [],
              "timeline_setups": [],
              "unhandled_root_fields": {},
              "resolution": {"width": %d, "height": %d},
              "elements": [%s],
              "outliner": [%s],
              "textures": [{
                "path": "", "name": "%s.png", "folder": "entity", "namespace": "", "id": "0",
                "width": %d, "height": %d, "uv_width": %d, "uv_height": %d,
                "particle": false, "use_as_default": false, "layers_enabled": false,
                "sync_to_project": "", "render_mode": "default", "render_sides": "auto",
                "frame_time": 1, "frame_order_type": "loop", "frame_order": "", "frame_interpolate": false,
                "visible": true, "internal": true, "saved": false,
                "uuid": "%s",
                "source": "data:image/png;base64,%s"
              }]
            }
            """.formatted(
            name, name, resolution, resolution,
            elements, outliner,
            name, resolution, resolution, resolution, resolution,
            uuid("texture/" + name), textureBase64
        );
    }

    static void collect(
        Part part,
        double ax,
        double ay,
        double az,
        StringBuilder elements,
        StringBuilder outliner
    ) {
        // 兄弟节点之间的逗号由调用方(children 循环)负责,顶层只有一个根组。
        double px = ax + part.px(), py = ay + part.py(), pz = az + part.pz();
        outliner.append("""
            {"name": "%s", "origin": [%s, %s, %s], "rotation": [0, %s, 0], "color": 0, "uuid": "%s", \
            "export": true, "mirror_uv": false, "isOpen": true, "locked": false, "visibility": true, \
            "autouv": 0, "children": [""".formatted(
            part.name(), number(px), number(24 - py), number(pz),
            number(-part.yRotDeg()), uuid("part/" + part.name() + "/" + px + "/" + py + "/" + pz)
        ));
        boolean firstChild = true;
        int cubeIndex = 0;
        for (Cube cube : part.cubes()) {
            String cubeId = uuid("cube/" + part.name() + "/" + cube.name() + "/" + cubeIndex++);
            if (!elements.isEmpty()) elements.append(',');
            double x1 = px + cube.x(), z1 = pz + cube.z();
            double yTop = 24 - (py + cube.y());
            elements.append("""
                {"name": "%s", "box_uv": true, "rescale": false, "locked": false, "light_emission": 0, \
                "render_order": "default", "allow_mirror_modeling": true, \
                "from": [%s, %s, %s], "to": [%s, %s, %s], "autouv": 0, "color": %d, \
                "inflate": 0, "origin": [%s, %s, %s], "uv_offset": [%d, %d], "type": "cube", "uuid": "%s"}""".formatted(
                cube.name(),
                number(x1), number(yTop - cube.h()), number(z1),
                number(x1 + cube.w()), number(yTop), number(z1 + cube.d()),
                cubeIndex % 8,
                number(px), number(24 - py), number(pz),
                cube.u(), cube.v(),
                cubeId
            ));
            if (!firstChild) outliner.append(',');
            outliner.append('"').append(cubeId).append('"');
            firstChild = false;
        }
        for (Part child : part.children()) {
            if (!firstChild) outliner.append(',');
            firstChild = false;
            collect(child, px, py, pz, elements, outliner);
        }
        outliner.append("]}");
    }

    static String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private DroneAssetGenerator() {
    }
}
