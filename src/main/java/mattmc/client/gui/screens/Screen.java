package mattmc.client.gui.screens;

public interface Screen {
    default void onOpen() {}
    default void onClose() {}
    void tick();
    void render(double alpha);
}
