package net.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.mca.MCA;
import net.mca.client.gui.widget.RectangleWidget;
import net.mca.client.gui.widget.TooltipButtonWidget;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.GetVillageRequest;
import net.mca.network.c2s.RenameVillageMessage;
import net.mca.network.c2s.ReportBuildingMessage;
import net.mca.network.c2s.SaveVillageMessage;
import net.mca.resources.BuildingTypes;
import net.mca.resources.Rank;
import net.mca.resources.data.BuildingType;
import net.mca.resources.data.tasks.Task;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.mca.util.localization.FlowingText;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.function.Consumer;

public class BlueprintScreen extends ExtendedScreen {
    //gui element Y positions
    private final int positionTaxes = -60;
    private final int positionBirth = -10;
    private final int positionMarriage = 40;
    private Village village;
    private int reputation;
    private boolean isVillage;
    private Rank rank;
    private Set<String> completedTasks;
    private String page;
    private ButtonWidget[] buttonTaxes;
    private ButtonWidget[] buttonBirths;
    private ButtonWidget[] buttonMarriage;
    private ButtonWidget buttonPage;
    private int pageNumber = 0;
    private final List<ButtonWidget> catalogButtons = new LinkedList<>();

    private static final Identifier ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private BuildingType selectedBuilding;
    private UUID selectedVillager;

    private int mouseX;
    private int mouseY;

    private Map<Rank, List<Task>> tasks;

    public BlueprintScreen() {
        super(new LiteralText("Blueprint"));
    }

    private void saveVillage() {
        NetworkHandler.sendToServer(new SaveVillageMessage(village));
    }

    private void changeTaxes(float d) {
        village.setTaxes(Math.max(0.0f, Math.min(1.0f, village.getTaxes() + d)));
        saveVillage();
    }

    private void changePopulationThreshold(float d) {
        village.setPopulationThreshold(Math.max(0.0f, Math.min(1.0f, village.getPopulationThreshold() + d)));
        saveVillage();
    }

    private void changeMarriageThreshold(float d) {
        village.setMarriageThreshold(Math.max(0.0f, Math.min(1.0f, village.getMarriageThreshold() + d)));
        saveVillage();
    }

    private ButtonWidget[] createValueChanger(int x, int y, int w, int h, Consumer<Boolean> onPress, Text tooltip) {
        ButtonWidget[] buttons = new ButtonWidget[3];

        buttons[1] = addDrawableChild(new ButtonWidget(x - w / 2, y, w / 4, h,
                new LiteralText("<<"), (b) -> onPress.accept(false)));

        buttons[2] = addDrawableChild(new ButtonWidget(x + w / 4, y, w / 4, h,
                new LiteralText(">>"), (b) -> onPress.accept(true)));

        buttons[0] = addDrawableChild(new ButtonWidget(x - w / 4, y, w / 2, h,
                new LiteralText(""), (b) -> {
        },
                (ButtonWidget buttonWidget, MatrixStack matrixStack, int mx, int my) ->
                        renderTooltip(matrixStack, FlowingText.wrap(tooltip, 160), mx, my)
        ));

        return buttons;
    }

    protected void drawBuildingIcon(MatrixStack transform, int x, int y, int u, int v) {
        transform.push();
        transform.translate(x - 6.6, y - 6.6, 0);
        transform.scale(0.66f, 0.66f, 0.66f);
        this.drawTexture(transform, 0, 0, u, v, 20, 20);
        transform.pop();
    }

    @Override
    public void init() {
        NetworkHandler.sendToServer(new GetVillageRequest());
        setPage("waiting");
    }

    private void setPage(String page) {
        if (page.equals("close")) {
            assert client != null;
            client.setScreen(null);
            return;
        }

        this.page = page;

        clearChildren();

        // back button
        addDrawableChild(new ButtonWidget(5, 5, 20, 20, new TranslatableText("gui.button.backarrow"), (b) -> setPage("close")));

        //page selection
        int bx = width / 2 - 180;
        int by = height / 2 - 56;
        if (!page.equals("rename")) {
            if (!page.equals("empty") && !page.equals("waiting")) {
                for (String p : new String[] {"map", "rank", "catalog", "villagers", "rules", "refresh"}) {
                    ButtonWidget widget = new ButtonWidget(bx, by, 80, 20, new TranslatableText("gui.blueprint." + p), (b) -> setPage(p));
                    addDrawableChild(widget);
                    if (page.equals(p)) {
                        widget.active = false;
                    }
                    by += 22;
                }
            }
        }

        switch (page) {
            case "empty":
                //add building
                bx = width / 2 - 48;
                by = height / 2;
                addDrawableChild(new TooltipButtonWidget(bx - 50, by + 5, 96, 20, new TranslatableText("gui.blueprint.addRoom"), (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_ROOM));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                    close();
                }));
                addDrawableChild(new TooltipButtonWidget(bx + 50, by + 5, 96, 20, new TranslatableText("gui.blueprint.addBuilding"), (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                    close();
                }));
                break;
            case "refresh":
                NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
                NetworkHandler.sendToServer(new GetVillageRequest());
                assert client != null;
                assert client.player != null;
                client.player.sendMessage(new TranslatableText("blueprint.refreshed"), true);
                setPage("map");
                break;
            case "advanced":
                //auto-scan
                bx = width / 2 + 180 - 64 - 16;
                by = height / 2 - 56;
                TranslatableText text = new TranslatableText("gui.blueprint.autoScan");
                if (village.isAutoScan()) {
                    text.formatted(Formatting.GREEN);
                } else {
                    text.formatted(Formatting.GRAY).formatted(Formatting.STRIKETHROUGH);
                }
                addDrawableChild(new TooltipButtonWidget(bx, by, 96, 20, text, (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.AUTO_SCAN));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                    village.toggleAutoScan();
                    setPage(page);
                }));
                by += 22;

                //restrict access
                addDrawableChild(new TooltipButtonWidget(bx, by, 96, 20, new TranslatableText("gui.blueprint.restrictAccess"), (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //add whole building
                addDrawableChild(new TooltipButtonWidget(bx, by, 96, 20, new TranslatableText("gui.blueprint.addBuilding"), (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                }));
                by += 22 * 3;

                //rename village
                if (isVillage) {
                    addDrawableChild(new ButtonWidget(bx, by, 96, 20, new TranslatableText("gui.blueprint.renameVillage"), (b) -> {
                        setPage("rename");
                    }));
                }
            case "map":
                //add building
                bx = width / 2 + 180 - 64 - 16;
                by = height / 2 - 56 + 22 * 3;
                addDrawableChild(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.addRoom", (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_ROOM));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //remove building
                addDrawableChild(new ButtonWidget(bx, by, 96, 20, new TranslatableText("gui.blueprint.removeBuilding"), (b) -> {
                    NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE));
                    NetworkHandler.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //advanced
                if (!page.equals("advanced")) {
                    addDrawableChild(new ButtonWidget(bx, by, 96, 20, new TranslatableText("gui.blueprint.advanced"), (b) -> {
                        setPage("advanced");
                    }));
                }

                break;
            case "rank":
                break;
            case "catalog":
                //list catalog button
                int row = 0;
                int col = 0;
                int size = 21;
                int x = width / 2 - 4 * size - 8;
                int y = (int)(height / 2 - 2.0 * size);
                catalogButtons.clear();
                for (BuildingType bt : BuildingTypes.getInstance()) {
                    if (bt.visible()) {
                        TexturedButtonWidget widget = new TexturedButtonWidget(
                                row * size + x + 10, col * size + y - 10, 20, 20, bt.iconU(), bt.iconV() + 20, 20, ICON_TEXTURES, 256, 256, button -> {
                            selectBuilding(bt);
                            button.active = false;
                            catalogButtons.forEach(b -> b.active = true);
                        }, new TranslatableText("buildingType." + bt.name()));
                        catalogButtons.add(addDrawableChild(widget));

                        row++;
                        if (row > 4) {
                            row = 0;
                            col++;
                        }
                    }
                }
                break;
            case "villagers":
                addDrawableChild(new ButtonWidget(width / 2 - 24 - 20, height / 2 + 54, 20, 20, new LiteralText("<"), (b) -> {
                    if (pageNumber > 0) {
                        pageNumber--;
                    }
                }));
                addDrawableChild(new ButtonWidget(width / 2 + 24, height / 2 + 54, 20, 20, new LiteralText(">"), (b) -> {
                    if (pageNumber < Math.ceil(village.getPopulation() / 9.0) - 1) {
                        pageNumber++;
                    }
                }));
                buttonPage = addDrawableChild(new ButtonWidget(width / 2 - 24, height / 2 + 54, 48, 20, new LiteralText("0/0)"), (b) -> {
                }));
                break;
            case "rules":
                //taxes
                buttonTaxes = createValueChanger(width / 2, height / 2 + positionTaxes + 10, 80, 20, (b) -> changeTaxes(b ? 0.1f : -0.1f), new TranslatableText("gui.blueprint.tooltip.taxes"));
                toggleButtons(buttonTaxes, false);

                //birth threshold
                buttonBirths = createValueChanger(width / 2, height / 2 + positionBirth + 10, 80, 20, (b) -> changePopulationThreshold(b ? 0.1f : -0.1f), new TranslatableText("gui.blueprint.tooltip.births"));
                toggleButtons(buttonBirths, false);

                //marriage threshold
                buttonMarriage = createValueChanger(width / 2, height / 2 + positionMarriage + 10, 80, 20, (b) -> changeMarriageThreshold(b ? 0.1f : -0.1f), new TranslatableText("gui.blueprint.tooltip.marriage"));
                toggleButtons(buttonMarriage, false);
                break;
            case "rename":
                TextFieldWidget field = addDrawableChild(new TextFieldWidget(textRenderer, width / 2 - 65, height / 2 - 16, 130, 20, new TranslatableText("gui.blueprint.renameVillage")));
                field.setMaxLength(32);
                field.setText(village.getName());

                addDrawableChild(new ButtonWidget(width / 2 - 66, height / 2 + 8, 64, 20, new TranslatableText("gui.blueprint.cancel"), (b) -> {
                    setPage("map");
                }));
                addDrawableChild(new ButtonWidget(width / 2 + 2, height / 2 + 8, 64, 20, new TranslatableText("gui.blueprint.rename"), (b) -> {
                    NetworkHandler.sendToServer(new RenameVillageMessage(village.getId(), field.getText()));
                    village.setName(field.getText());
                    setPage("map");
                }));
                break;
        }
    }

    private void selectBuilding(BuildingType b) {
        selectedBuilding = b;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(MatrixStack transform, int sizeX, int sizeY, float offset) {
        renderBackground(transform);

        assert client != null;
        this.mouseX = (int)(client.mouse.getX() * width / client.getWindow().getFramebufferWidth());
        this.mouseY = (int)(client.mouse.getY() * height / client.getWindow().getFramebufferHeight());

        switch (page) {
            case "waiting":
                drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.waiting"), width / 2, height / 2, 0xffaaaaaa);
                break;
            case "empty":
                drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.empty"), width / 2, height / 2 - 20, 0xffaaaaaa);
                break;
            case "map":
                renderStats(transform);
            case "advanced":
                renderName(transform);
                renderMap(transform);
                break;
            case "rank":
                renderTasks(transform);
                renderStats(transform);
                break;
            case "catalog":
                renderCatalog(transform);
                break;
            case "villagers":
                renderVillagers(transform);
                break;
            case "rules":
                renderRules(transform);
                break;
        }

        super.render(transform, sizeX, sizeY, offset);
    }

    private void renderName(MatrixStack transform) {
        //name
        transform.push();
        transform.scale(2.0f, 2.0f, 2.0f);
        if (isVillage) {
            drawCenteredText(transform, textRenderer, village.getName(), width / 4, height / 4 - 48, 0xffffffff);
        } else {
            drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.settlement"), width / 4, height / 4 - 48, 0xffffffff);
        }
        transform.pop();
    }

    private void renderStats(MatrixStack transform) {
        int x = width / 2 + (page.equals("rank") ? -70 : 105);
        int y = height / 2 - 50;

        //rank
        Text rankStr = new TranslatableText(rank.getTranslationKey());
        int rankColor = rank.ordinal() == 0 ? 0xffff0000 : 0xffffff00;

        textRenderer.drawWithShadow(transform, new TranslatableText("gui.blueprint.currentRank", rankStr), x, y, rankColor);
        textRenderer.drawWithShadow(transform, new TranslatableText("gui.blueprint.reputation", String.valueOf(reputation)), x, y + 11, rank.ordinal() == 0 ? 0xffff0000 : 0xffffffff);
        textRenderer.drawWithShadow(transform, new TranslatableText("gui.blueprint.buildings", village.getBuildings().size()), x, y + 22, 0xffffffff);
        textRenderer.drawWithShadow(transform, new TranslatableText("gui.blueprint.population", village.getPopulation(), village.getMaxPopulation()), x, y + 33, 0xffffffff);
    }

    private void renderMap(MatrixStack transform) {
        int mapSize = 75;
        int y = height / 2 + 8;
        RectangleWidget.drawRectangle(transform, width / 2 - mapSize, y - mapSize, width / 2 + mapSize, y + mapSize, 0xffffff88);

        //hint
        if (!village.isAutoScan() && village.getBuildings().size() <= 1) {
            drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.autoScanDisabled"), width / 2, height / 2 + 90, 0xaaffffff);
        }

        transform.push();

        RenderSystem.setShaderTexture(0, ICON_TEXTURES);

        //center and scale the map
        float sc = Math.min((float)mapSize / (village.getBox().getMaxBlockCount() + 3) * 2, 2.0f);
        int mouseLocalX = (int)((mouseX - width / 2.0) / sc + village.getCenter().getX());
        int mouseLocalY = (int)((mouseY - y) / sc + village.getCenter().getZ());
        transform.translate(width / 2.0, y, 0);
        transform.scale(sc, sc, 0.0f);
        transform.translate(-village.getCenter().getX(), -village.getCenter().getZ(), 0);

        //show the players location
        assert client != null;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            RectangleWidget.drawRectangle(transform, (int)player.getX() - 1, (int)player.getZ() - 1, (int)player.getX() + 1, (int)player.getZ() + 1, 0xffff00ff);
        }

        //buildings
        List<Building> hoverBuildings = new LinkedList<>();
        for (Building building : village.getBuildings().values()) {
            BuildingType bt = BuildingTypes.getInstance().getBuildingType(building.getType());

            if (bt.getMinBlocks() > 0 && bt.getMinBlocks() > building.getBlockCount()) continue;

            if (bt.isIcon()) {
                BlockPos c = building.getCenter();
                drawBuildingIcon(transform, c.getX(), c.getZ(), bt.iconU(), bt.iconV());

                //tooltip
                int margin = 6;
                if (c.getSquaredDistance(new Vec3i(mouseLocalX, c.getY(), mouseLocalY)) < margin * margin) {
                    hoverBuildings.add(building);
                }
            } else {
                BlockPos p0 = building.getPos0();
                BlockPos p1 = building.getPos1();
                RectangleWidget.drawRectangle(transform, p0.getX(), p0.getZ(), p1.getX(), p1.getZ(), bt.getColor());

                //icon
                if (bt.visible()) {
                    BlockPos c = building.getCenter();
                    drawBuildingIcon(transform, c.getX(), c.getZ(), bt.iconU(), bt.iconV());
                }

                //tooltip
                int margin = 1;
                if (mouseLocalX >= p0.getX() - margin && mouseLocalX <= p1.getX() + margin && mouseLocalY >= p0.getZ() - margin && mouseLocalY <= p1.getZ() + margin) {
                    hoverBuildings.add(building);
                }
            }
        }

        transform.pop();

        //sort vertically
        hoverBuildings.sort((a, b) -> b.getCenter().getY() - a.getCenter().getY());

        //get tooltips
        List<List<Text>> tooltips = new LinkedList<>();
        for (Building b : hoverBuildings) {
            tooltips.add(getBuildingTooltip(b));
        }

        //get height
        int h = 0;
        for (List<Text> b : tooltips) {
            h += getTooltipHeight(b) + 9;
        }

        //render
        int py = mouseY - h / 2 + 12;
        for (List<Text> b : tooltips) {
            renderTooltip(transform, b, mouseX, py);
            py += getTooltipHeight(b) + 9;
        }
    }

    private List<Text> getBuildingTooltip(Building hoverBuilding) {
        List<Text> lines = new LinkedList<>();

        //name
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(hoverBuilding.getType());
        lines.add(new TranslatableText("buildingType." + bt.name()));

        //size
        if (!bt.grouped()) {
            lines.add(new TranslatableText("gui.blueprint.size", String.valueOf(hoverBuilding.getSize())));
        }

        //residents
        for (String name : village.getResidents(hoverBuilding.getId())) {
            lines.add(new LiteralText(name));
        }

        //present blocks
        for (Map.Entry<Identifier, List<BlockPos>> block : hoverBuilding.getBlocks().entrySet()) {
            lines.add(new LiteralText(block.getValue().size() + " x ").append(getBlockName(block.getKey())).formatted(Formatting.GRAY));
        }

        return lines;
    }

    private void renderTasks(MatrixStack transform) {
        if (rank == null) {
            return;
        }

        int y = height / 2 + 5;
        int x = width / 2 - 70;

        //tasks
        for (Task task : tasks.get(rank.promote())) {
            boolean completed = completedTasks.contains(task.getId());
            Text t = task.getTranslatable().formatted(completed ? Formatting.STRIKETHROUGH : Formatting.RESET);
            textRenderer.drawWithShadow(transform, t, x, y, completed ? 0xff88ff88 : 0xffff5555);
            y += 11;
        }
    }

    private void renderCatalog(MatrixStack transform) {
        //title
        transform.push();
        transform.scale(2.0f, 2.0f, 2.0f);
        drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.catalogFull"), width / 4, height / 4 - 52, 0xffffffff);
        transform.pop();

        //explanation
        drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.catalogHint").formatted(Formatting.GRAY), width / 2, height / 2 - 82, 0xffffffff);

        //building
        int x = width / 2 + 35;
        int y = height / 2 - 50;
        if (selectedBuilding != null) {
            //name
            textRenderer.drawWithShadow(transform, new TranslatableText("buildingType." + selectedBuilding.name()), x, y, selectedBuilding.getColor());

            //description
            List<Text> wrap = FlowingText.wrap(new TranslatableText("buildingType." + selectedBuilding.name() + ".description").formatted(Formatting.GRAY).formatted(Formatting.ITALIC), 150);
            for (Text t : wrap) {
                textRenderer.drawWithShadow(transform, t, x, y + 12, 0xffffffff);
                y += 10;
            }

            //size
            Text size = selectedBuilding.size() == 0 ? new TranslatableText("gui.blueprint.anySize") : new TranslatableText("gui.blueprint.size", String.valueOf(selectedBuilding.size()));
            textRenderer.drawWithShadow(transform, size, x, y + 20, 0xffdddddd);

            //required blocks
            for (Map.Entry<Identifier, Integer> b : selectedBuilding.getGroups().entrySet()) {
                textRenderer.drawWithShadow(transform, new LiteralText(b.getValue() + " x ").append(getBlockName(b.getKey())), x, y + 32, 0xffffffff);
                y += 10;
            }
        } else {
            //help
            List<Text> wrap = FlowingText.wrap(new TranslatableText("gui.blueprint.buildingTypes").formatted(Formatting.GRAY).formatted(Formatting.ITALIC), 150);
            for (Text t : wrap) {
                textRenderer.drawWithShadow(transform, t, x, y, 0xffffffff);
                y += 10;
            }
        }
    }

    private void renderVillagers(MatrixStack transform) {
        int maxPages = (int)Math.ceil(village.getPopulation() / 9.0);
        buttonPage.setMessage(new LiteralText((pageNumber + 1) + "/" + maxPages));

        List<Map.Entry<UUID, String>> villager = village.getResidentNames().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList();

        selectedVillager = null;
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index < villager.size()) {
                int y = height / 2 - 51 + i * 11;
                boolean hover = isMouseWithin(width / 2 - 50, y - 1, 100, 11);
                drawCenteredText(transform, textRenderer, new LiteralText(villager.get(index).getValue()), width / 2, y, hover ? 0xFFD7D784 : 0xFFFFFFFF);
                if (hover) {
                    selectedVillager = villager.get(index).getKey();
                }
            } else {
                break;
            }
        }
    }

    private void renderRules(MatrixStack transform) {
        buttonTaxes[0].setMessage(new LiteralText((int)(village.getTaxes() * 100) + "%"));
        buttonMarriage[0].setMessage(new LiteralText((int)(village.getMarriageThreshold() * 100) + "%"));
        buttonBirths[0].setMessage(new LiteralText((int)(village.getPopulationThreshold() * 100) + "%"));

        //taxes
        drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.taxes"), width / 2, height / 2 + positionTaxes, 0xffffffff);
        if (!rank.isAtLeast(Rank.MERCHANT)) {
            drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.rankTooLow"), width / 2, height / 2 + positionTaxes + 15, 0xffffffff);
            toggleButtons(buttonTaxes, false);
        } else {
            toggleButtons(buttonTaxes, true);
        }

        //births
        drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.birth"), width / 2, height / 2 + positionBirth, 0xffffffff);
        if (!rank.isAtLeast(Rank.NOBLE)) {
            drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.rankTooLow"), width / 2, height / 2 + positionBirth + 15, 0xffffffff);
            toggleButtons(buttonBirths, false);
        } else {
            toggleButtons(buttonBirths, true);
        }

        //marriages
        drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.marriage"), width / 2, height / 2 + positionMarriage, 0xffffffff);
        if (!rank.isAtLeast(Rank.MAYOR)) {
            drawCenteredText(transform, textRenderer, new TranslatableText("gui.blueprint.rankTooLow"), width / 2, height / 2 + positionMarriage + 15, 0xffffffff);
            toggleButtons(buttonMarriage, false);
        } else {
            toggleButtons(buttonMarriage, true);
        }
    }

    private Text getBlockName(Identifier id) {
        if (Registry.BLOCK.containsId(id)) {
            return new TranslatableText(Registry.BLOCK.get(id).getTranslationKey());
        } else {
            return new TranslatableText("tag." + id.toString());
        }
    }

    private void toggleButtons(ButtonWidget[] buttons, boolean active) {
        for (ButtonWidget b : buttons) {
            b.active = active;
            b.visible = active;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page.equals("villagers") && selectedVillager != null) {
            assert client != null;
            client.setScreen(new FamilyTreeScreen(selectedVillager));
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void setVillage(Village village) {
        this.village = village;
        if (village == null) {
            setPage("empty");
        } else if (page.equals("waiting")) {
            setPage("map");
        }
    }

    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
    }
}
