
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

  int values[]; // indexed by key % bufferSize
  int nextIndices[]; // indexed by key % bufferSize
  private int bufferStartKey = -1;
  private int firstIndex = -1; // index of first entry, or -1
  private int lastIndex = -1; // index of last entry, or -1
  private int firstKey = -1; // the key of the first entry
  private int count = 0; // number of entries

  public SparseRingBuffer(int size) {
    this.bufferLength = size;
    this.values = new int[size];
    this.nextIndices = new int[size];
  }

  public void clear() {
    bufferStartKey = -1;
    firstIndex = -1;
    lastIndex = -1;
    firstKey = -1;
    count = 0;
  }

  public boolean isEmpty() {
    return count == 0;
  }

  public int size() {
    return count;
  }

  public boolean containsKey(int key) {
    if (bufferStartKey < 0 || key < firstKey || key > firstKey + bufferLength)
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
    // assert bufferStartKey < 0 || getKeyForIndex(index) == key;
    values[index] = value;
    nextIndices[index] = -1;
    if (firstIndex < 0) {
      // add initial value to empty buffer
      bufferStartKey = key / bufferLength * bufferLength;
      firstIndex = lastIndex = index;
      firstKey = key;
      count++;
      return;
    }
    int lastKey = getKeyForIndex(lastIndex);
    if (key > lastKey) {
      // append (the common case)
      assert nextIndices[lastIndex] == -1;
      assert lastKey < key;
      assert index != lastIndex;
      nextIndices[lastIndex] = index;
      lastIndex = index;
      count++;
    } else if (lastKey == key) {
      // replace last value: nothing else to do
    } else if (key < firstKey) {
      // insert at beginning, before previous first element
      if (key < lastKey - bufferLength) {
        throw new IndexOutOfBoundsException(key);
      }
      nextIndices[index] = firstIndex;
      firstIndex = index;
      firstKey = key;
      count++;
    } else {
      // lastKey - bufferLength < key < lastKey
      // insert between elements
      int prev = -1;
      int next = firstIndex;
      while (getKeyForIndex(next) < key) {
        prev = next;
        next = nextIndices[next];
        assert next >= 0;
      }
      nextIndices[prev] = index;
      nextIndices[index] = next;
      count++;
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
    // This is necessary to guard the fast path.
    if (lastIndex < 0) {
      return;
    }
    // fast path detects when the entire buffer would be cleared. This is
    // functionally identical to the slow path.
    if (getKeyForIndex(lastIndex) < key) {
      clear();
      return;
    }
    while (count > 0 && firstKey < key) {
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
      firstKey = -1;
      lastIndex = -1;
    } else {
      firstKey += (firstIndex - prevFirstIndex + bufferLength) % bufferLength;
      bufferStartKey = firstKey / bufferLength * bufferLength;
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
    return new SparseRingBufferIterator(this, firstIndex);
  }

  /** Returns a string e.g. {10:1,20:2}. This is intended for debugging. */
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

class SparseRingBufferIterator implements Iterator<BufferEntry> {
  private final SparseRingBuffer buffer;
  private int index;
  private BufferEntry sample; // flyweight

  SparseRingBufferIterator(SparseRingBuffer buffer, int index) {
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
    int key = buffer.getKeyForIndex(index);
    int value = buffer.values[index];
    if (sample == null) {
      sample = new BufferEntry(key, value);
    } else {
      sample.update(key, value);
    }
    index = buffer.nextIndices[index];
    return sample;
  }
}
