
/** The spare ring buffer is available as a standalone Java file at
 * <https://github.com/osteele/SpareRingBuffer.java>.
 */
import java.util.*;

/**
 * Implements a "sparse ring buffer", that holds sample values over the last
 * `size` time units. The buffer is sparse in time, not space: like a ring
 * buffer, it uses fixed-size arrays that are sized for the largest possible
 * number of samples; but like a linked list, each entry contains a pointer to
 * the next populated entry.
 */
public class SparseRingBuffer implements Iterable<BufferEntry> {
  private final int bufferLength;

  int values[]; // indexed by sampleTime % bufferSize
  int nextIndices[]; // indexed by sampleTime % bufferSize
  private int bufferStartKey = -1;
  private int firstIndex = -1; // index of first entry, or -1
  private int lastIndex = -1; // index of last entry, or -1
  private int firstIndexKey = -1; // the sampletime of the first entry
  private int count = 0; // number of entries

  public SparseRingBuffer(int size) {
    this.bufferLength = size;
    this.values = new int[size];
    this.nextIndices = new int[size];
  }

  public boolean isEmpty() {
    return count == 0;
  }

  public int size() {
    return count;
  }

  public boolean containsKey(int key) {
    if (bufferStartKey < 0 || key < firstIndexKey || key > firstIndexKey + bufferLength)
      return false;
    int index = key - bufferStartKey;
    if (bufferLength <= index && index < bufferLength + firstIndex)
      index -= bufferLength;
    if (index < 0 || bufferLength <= index)
      return false;
    return index == lastIndex || nextIndices[index] > 0;
  }

  /**
   * Set the entry at timestamp to value. Removes all entries with timestamp ts
   * s.t. ts <= timestamp - count.
   */
  public void put(int key, int value) {
    removeBefore(key - bufferLength + 1);
    int index = key % bufferLength;
    assert bufferStartKey < 0 || getKeyForIndex(index) == key;
    values[index] = value;
    nextIndices[index] = -1;
    if (firstIndex < 0) {
      // initial value
      bufferStartKey = key / bufferLength * bufferLength;
      firstIndex = lastIndex = index;
      firstIndexKey = key;
      count++;
    } else if (getKeyForIndex(lastIndex) == key) {
      // replace last value
    } else if (getKeyForIndex(lastIndex) < key) {
      // append
      assert nextIndices[lastIndex] == -1;
      assert getKeyForIndex(lastIndex) < key;
      assert index != lastIndex;
      nextIndices[lastIndex] = index;
      lastIndex = index;
      count++;
    } else {
      // insert. This is relatively simply, although inefficiently, implemented
      // by iterating from the start, with obvious techniques to trade off
      // against the other cases to make it efficient. However, I don't need it
      // for my use cases.
      throw new UnsupportedOperationException(
          "SparseRingBuffer.put does not support insertion into the middle of the buffer");
    }
  }

  public int get(int key) {
    if (!containsKey(key))
      throw new IndexOutOfBoundsException(
          String.format("SpareRingBuffer does not have an entry for %s", key));
    int index = key % bufferLength;
    return values[index];
  }

  void removeBefore(int key) {
    while (count > 0 && firstIndexKey < key) {
      removeFirstEntry();
    }
  }

  private void removeFirstEntry() {
    assert firstIndex >= 0;
    int prevFirstIndex = firstIndex;
    firstIndex = nextIndices[firstIndex];
    nextIndices[prevFirstIndex] = -1;
    if (--count == 0) {
      bufferStartKey = -1;
      firstIndexKey = -1;
      lastIndex = -1;
    } else {
      bufferStartKey = firstIndex / bufferLength * bufferLength;
      firstIndexKey += (firstIndex - prevFirstIndex + bufferLength) % bufferLength;
    }
  }

  int getKeyForIndex(int index) {
    assert 0 <= index && index < bufferLength;
    int key = bufferStartKey + index;
    if (index < firstIndex) {
      key += bufferLength;
    }
    return key;
  }

  /**
   * Iterating over an instance of this class uses the flyweight pattern. Each
   * call to next() will return the same instance, re-initialized with new
   * values.
   */
  public Iterator<BufferEntry> iterator() {
    return new SampleBufferIterator(this, firstIndex);
  }

  public String toString() {
    var str = new StringBuffer("{");
    String prefix = "";
    for (var entry : this) {
      str.append(prefix + entry.getKey() + ":" + entry.getValue());
      prefix = ",";
    }
    return str + "}";
  }
}

class SampleBufferIterator implements Iterator<BufferEntry> {
  private final SparseRingBuffer buffer;
  private int index;
  private BufferEntry sample; // flyweight

  SampleBufferIterator(SparseRingBuffer buffer, int index) {
    this.buffer = buffer;
    this.index = index;
  }

  public boolean hasNext() {
    return index >= 0;
  }

  public BufferEntry next() {
    if (index < 0) {
      throw new NoSuchElementException();
    }
    int sampleTime = buffer.getKeyForIndex(index);
    int value = buffer.values[index];
    if (sample == null) {
      sample = new BufferEntry(sampleTime, value);
    } else {
      sample.update(sampleTime, value);
    }
    index = buffer.nextIndices[index];
    return sample;
  }
}

class BufferEntry {
  int key;
  int value;

  BufferEntry(int key, int value) {
    this.key = key;
    this.value = value;
  }

  int getKey() {
    return this.key;
  }

  int getValue() {
    return this.value;
  }

  void update(int sampleTime, int value) {
    this.key = sampleTime;
    this.value = value;
  }
}
