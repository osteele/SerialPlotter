
public class BufferEntry {
  int key;
  int value;

  BufferEntry(int key, int value) {
    this.key = key;
    this.value = value;
  }

  public int getKey() {
    return this.key;
  }

  public int getValue() {
    return this.value;
  }

  void update(int key, int value) {
    this.key = key;
    this.value = value;
  }
}
