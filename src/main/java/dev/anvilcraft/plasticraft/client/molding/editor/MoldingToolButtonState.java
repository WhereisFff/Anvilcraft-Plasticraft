package dev.anvilcraft.plasticraft.client.molding.editor;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** 将工具的保持状态与鼠标按压过程分开，避免按下按钮时提前执行编辑操作。 */
public final class MoldingToolButtonState {
    private MoldingTool selectedTool;
    private MirrorMode mirrorMode;
    @Nullable
    private MoldingTool pressedTool;

    public MoldingToolButtonState(MoldingTool selectedTool) {
        this.selectedTool = selectedTool;
        this.mirrorMode = selectedTool == MoldingTool.MIRROR ? MirrorMode.X : MirrorMode.STANDARD;
    }

    public MoldingTool selectedTool() {
        return this.selectedTool;
    }

    public Optional<MoldingAxis> mirrorAxis() {
        return this.mirrorMode.axis();
    }

    public void select(MoldingTool tool) {
        if (tool == MoldingTool.MIRROR) {
            if (this.selectedTool != MoldingTool.MIRROR) this.mirrorMode = MirrorMode.X;
        } else {
            this.mirrorMode = MirrorMode.STANDARD;
        }
        this.selectedTool = tool;
        this.pressedTool = null;
    }

    public void selectMirrorAxis(MoldingAxis axis) {
        this.selectedTool = MoldingTool.MIRROR;
        this.mirrorMode = MirrorMode.fromAxis(axis);
    }

    public boolean press(MoldingTool tool) {
        if (tool == MoldingTool.NONE || this.pressedTool != null) return false;
        this.pressedTool = tool;
        return true;
    }

    public ReleaseAction release(MoldingTool tool, boolean pointerInside) {
        if (this.pressedTool != tool) return ReleaseAction.NONE;
        this.pressedTool = null;
        if (!pointerInside) return ReleaseAction.NONE;
        return switch (tool) {
            case MOVE, SCALE, ROTATE -> {
                select(this.selectedTool == tool ? MoldingTool.NONE : tool);
                yield ReleaseAction.TOOL_CHANGED;
            }
            case PIVOT -> {
                if (this.selectedTool == MoldingTool.PIVOT) yield ReleaseAction.CENTER_PIVOT;
                select(MoldingTool.PIVOT);
                yield ReleaseAction.TOOL_CHANGED;
            }
            case MIRROR -> {
                MirrorMode current = this.selectedTool == MoldingTool.MIRROR
                    ? this.mirrorMode
                    : MirrorMode.STANDARD;
                this.mirrorMode = current.next();
                this.selectedTool = this.mirrorMode == MirrorMode.STANDARD
                    ? MoldingTool.NONE
                    : MoldingTool.MIRROR;
                yield ReleaseAction.TOOL_CHANGED;
            }
            case NONE -> ReleaseAction.NONE;
        };
    }

    public int frame(MoldingTool tool, boolean hovered) {
        if (this.pressedTool == tool) {
            if (tool == MoldingTool.PIVOT && this.selectedTool == MoldingTool.PIVOT) return 5;
            if (tool == MoldingTool.MIRROR) return activeMirrorMode().frameBase() + 2;
            return 2;
        }
        if (tool == MoldingTool.PIVOT) {
            if (this.selectedTool == MoldingTool.PIVOT) return hovered ? 4 : 3;
            return hovered ? 1 : 0;
        }
        if (tool == MoldingTool.MIRROR) {
            return activeMirrorMode().frameBase() + (hovered ? 1 : 0);
        }
        if (this.selectedTool == tool) return hovered ? 4 : 3;
        return hovered ? 1 : 0;
    }

    private MirrorMode activeMirrorMode() {
        return this.selectedTool == MoldingTool.MIRROR ? this.mirrorMode : MirrorMode.STANDARD;
    }

    public enum ReleaseAction {
        NONE,
        TOOL_CHANGED,
        CENTER_PIVOT
    }

    private enum MirrorMode {
        STANDARD(null),
        X(MoldingAxis.X),
        Y(MoldingAxis.Y),
        Z(MoldingAxis.Z);

        @Nullable
        private final MoldingAxis axis;

        MirrorMode(@Nullable MoldingAxis axis) {
            this.axis = axis;
        }

        private Optional<MoldingAxis> axis() {
            return Optional.ofNullable(this.axis);
        }

        private int frameBase() {
            return this.ordinal() * 3;
        }

        private MirrorMode next() {
            MirrorMode[] modes = values();
            return modes[(this.ordinal() + 1) % modes.length];
        }

        private static MirrorMode fromAxis(MoldingAxis axis) {
            return switch (axis) {
                case X -> X;
                case Y -> Y;
                case Z -> Z;
            };
        }
    }
}
