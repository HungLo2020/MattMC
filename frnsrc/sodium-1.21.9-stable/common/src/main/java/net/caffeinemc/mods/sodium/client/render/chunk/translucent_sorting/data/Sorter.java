package frnsrc.sodium;

public interface Sorter extends PresentSortData {
    void writeIndexBuffer(CombinedCameraPos cameraPos, boolean initial);

    void destroy();
}
