import processing.serial.*;
import osteele.processing.SerialRecord.*;
import java.util.*;

Serial serialPort;
SerialRecord serialRecord;
SerialPlotterGraph plotter;

// different colors for different axes
// handle values that don't have field names
// add commas to y labels
// dynamic scale y axis
// interpolate setting
// toggle what's displayed
// freeze
// interpolate
// make this a method of SerialRecord

void setup() {
  size(800, 424);

  String serialPortName = SerialUtils.findArduinoPort();
  serialPort = new Serial(this, serialPortName, 9600);
  serialRecord = new SerialRecord(this, serialPort);
  plotter = new SerialPlotterGraph();
}

void draw() {
  background(255);

  serialRecord.read();
  plotter.addFromRecord(serialRecord);
  plotter.draw();

  fill(0);
}

class SerialPlotterGraph {
  public static final int defaultHeight = 404;

  final int leftPadding = 59;
  final int bottomPadding = 39;
  final int topPadding = 61;
  final int rightPadding = 21;

  private int x;
  private int y;
  private int w; // width of the plot rectangle
  private int h; // height of the plot rectangle

  private ChannelMap samples = new ChannelMap(1000);
  private int vMin = 0;
  private int vMax = 1023;
  private int endTime;

  SerialPlotterGraph(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    h = height - topPadding - bottomPadding;
    w = width - leftPadding - rightPadding;
  }

  SerialPlotterGraph() {
    this(0, 0, width, SerialPlotterGraph.defaultHeight);
  }

  void draw() {
    endTime = millis();

    push();
    translate(x + leftPadding, y + topPadding);
    noFill();
    stroke(128);
    stroke(212, 216, 216); // axes
    line(0, 0, w, 0);
    line(0, h, w, h);

    stroke(235, 241, 241); // subdivisions
    for (int y = 0; y < h; y += 44) {
      line(0, y, w, y);
    }
    for (float x = 0; (x += 180) <= w; ) {
      line(x, 0, x, h);
    }

    drawAxisLabels();
    for (var entry : samples.channels.entrySet()) {
      drawChannel(entry.getKey(), entry.getValue());
    }
    pop();
  }

  void addFromRecord(SerialRecord serialRecord) {
    samples.addFromRecord(serialRecord);
  }

  private void drawAxisLabels() {
    fill(50);
    textSize(14);

    // x axis
    textAlign(CENTER, TOP);
    for (int i = samples.startTime; i <= endTime; i += 250) {
      text(i, sampleTimeToX(i), h + 10);
    }

    // y axis
    textAlign(RIGHT);
    for (int v = vMin; v <= vMax; v+=200) {
      text(v, -10, valueToY(v));
    }
  }

  private void drawChannel(String name, SampleBuffer ch) {
    stroke(66, 140, 193);
    strokeWeight(1.5);
    noFill();
    beginShape();
    //print(name, ch.count);
    //int n = 0;
    for (SampleValue sample : ch) {
      //n++;
      vertex(sampleTimeToX(sample.sampleTime), valueToY(sample.value));
    }
    //println(";", n);
    endShape();
  }

  private float sampleTimeToX(int t) {
    return map(t, samples.startTime, endTime + 1, 0, w);
  }

  private float valueToY(int v) {
    return map(v, vMin, vMax, h, 0);
  }
}

class ChannelMap {
  final int duration;
  int startTime =- 1;
  /** Channels, indexed by field name. */
  Map<String, SampleBuffer> channels = new HashMap<String, SampleBuffer>();

  ChannelMap(int duration) {
    this.duration = duration;
  }

  void addFromRecord(SerialRecord serialRecord) {
    int sampleTime = serialRecord.sampleTime;
    for (int i = 0; i < serialRecord.size; i++) {
      var fieldName = serialRecord.fieldNames[i];
      if (fieldName == null) {
        continue;
      }
      if (!fieldName.equals("millis")) continue;
      var channel = channels.get(fieldName);
      if (channel == null) {
        channel = new SampleBuffer(duration);
        channels.put(fieldName, channel);
      }
      channel.put(serialRecord.sampleTime, serialRecord.values[i]);
    }
    // remove channels that have lost all their values
    for (var iter = channels.entrySet().iterator(); iter.hasNext(); ) {
      var entry = iter.next();
      if (entry.getValue().isEmpty()) {
        iter.remove();
      }
    }
    for (SampleBuffer ch : channels.values()) {
      ch.removeBefore(sampleTime - duration + 1);
    }
    if (startTime < 0) startTime = sampleTime;
    startTime = max(startTime, sampleTime - 1000);
  }
}

class SampleBuffer implements Iterable<SampleValue> {
  SparseRingBuffer buffer;
  
  SampleBuffer(int duration) {
    buffer = new SparseRingBuffer(duration);
  }
  
  void put(int sampleTime, int value) {
    buffer.put(sampleTime, value);
  }
  boolean isEmpty(){return buffer.isEmpty();}
  
  void removeBefore(int sampleTime) {
    buffer.removeBefore(sampleTime);
  }
  
   public Iterator<SampleValue> iterator() {
     return new SampleBufferIterator(buffer);
   }
}

class SampleBufferIterator implements Iterator<SampleValue> {
   private Iterator iterator;
   private SampleValue sample; // flyweight

   SampleBufferIterator(SparseRingBuffer buffer) {
     this.iterator = buffer.iterator();
   }

   boolean hasNext() {
     return iterator.hasNext();
   }

   SampleValue next() {
     var entry = iterator.next();
     if (sample == null) {
       sample = new SampleValue(entry.getKey(), entry.getValue());
     } else {
       sample.update(entry.getKey(), entry.getValue());
     }
     return sample;
   }
 }

 class SampleValue {
   int sampleTime;
   int value;

   SampleValue(int sampleTime, int value) {
     this.sampleTime = sampleTime;
     this.value = value;
   }

   void update(int sampleTime, int value) {
     this.sampleTime = sampleTime;
     this.value = value;
   }
 }
